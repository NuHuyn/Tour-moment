/**
 * Read-only "tools" the chatbot is allowed to call, plus their DeepSeek tool schemas.
 *
 * Hard rule (see feasibility_report.md §4.1): every function here builds its Mongoose query
 * from a small set of typed, validated parameters - never from a raw/generated query object.
 * No tool here ever writes to the database.
 *
 * Each implementation returns { modelView, tours }:
 *   - modelView: what gets sent back to DeepSeek as the tool result (small, no `price`, no
 *     `authorId` - price is a waypoint-unlock flag, not a trip cost, and must never reach the
 *     model or it may repeat it back to the user as if it were one - see CHATBOT_PURPOSE).
 *   - tours: full tour objects, shaped exactly like GET /api/tours (matches the Android Tour
 *     model / TourAdapter), for the chatbot backend to hand back to the app as suggestion cards.
 *     This is already public data (same isShared:true filter as getPublicTours), so keeping
 *     `price` in this copy is not a new exposure - the Android UI already renders it elsewhere.
 */

const mongoose = require("mongoose");
const Tour = require("../models/Tour");
const User = require("../models/User");
const { redactTourWaypoints } = require("./waypointVisibility");

const DEFAULT_LIMIT = 5;
const MAX_LIMIT = 10;

// Built via RegExp(string) rather than a /.../ literal so the source file only ever contains
// plain ASCII escapes for the combining-diacritical-marks block (U+0300-U+036F) instead of raw
// Unicode combining characters, which are unreadable/fragile to hand-edit directly in source.
const COMBINING_MARKS_RE = new RegExp("[\\u0300-\\u036f]", "g");

function clampLimit(limit) {
  const n = Number.isFinite(limit) ? Math.floor(limit) : DEFAULT_LIMIT;
  return Math.min(Math.max(n, 1), MAX_LIMIT);
}

/** Vietnamese-diacritic-insensitive, case-insensitive normalization, e.g. "Hà Nội" -> "ha noi".
 *  The catalog is small enough (a handful of seed tours) that filtering in JS after one
 *  `isShared: true` fetch is simpler and more robust than trying to express this as a Mongo
 *  regex, and avoids ever building a query from user/model-supplied text. */
function normalizeVi(str) {
  return String(str || "")
    .normalize("NFD")
    .replace(COMBINING_MARKS_RE, "")
    .replace(/đ/g, "d")
    .replace(/Đ/g, "D")
    .toLowerCase()
    .trim();
}

async function attachAuthor(authorId) {
  const author = await User.findOne({ googleId: authorId }).select("displayName photoUrl").lean();
  return { displayName: (author && author.displayName) || "Traveler", photoUrl: (author && author.photoUrl) || null };
}

/** Full doc shaped like GET /api/tours's response - safe for the Android client. */
async function toClientTour(tourDoc) {
  return {
    _id: String(tourDoc._id),
    title: tourDoc.title,
    description: tourDoc.description,
    imageUrl: tourDoc.imageUrl,
    videoUrl: tourDoc.videoUrl,
    status: tourDoc.status,
    startDate: tourDoc.startDate,
    endDate: tourDoc.endDate,
    isShared: tourDoc.isShared,
    author: await attachAuthor(tourDoc.authorId),
    // Fail-closed: the chat tools have no per-device identity threaded through them (see
    // waypointVisibility.js docstring), so every locked-and-not-explicitly-unlocked waypoint is
    // always redacted here, same as an anonymous getPublicTours call with no deviceId - a locked
    // waypoint real name/coordinates/photos must never reach the chatbot response either,
    // otherwise it becomes a second, unprotected path to the same data getPublicTours redacts.
    waypoints: redactTourWaypoints(tourDoc.waypoints, tourDoc._id, null).map((wp) => ({
      locationName: wp.locationName,
      note: wp.note,
      price: wp.price,
      photos: wp.photos,
      coordinate: wp.coordinate,
    })),
  };
}

/** Reduced view sent to the model - no price, no authorId, no photos/video (keeps tokens small
 *  and keeps the "never mention price as a cost" rule enforceable at the data layer, not just
 *  via prompt instructions). */
function toModelView(clientTour) {
  return {
    tourId: clientTour._id,
    title: clientTour.title,
    description: clientTour.description,
    status: clientTour.status,
    startDate: clientTour.startDate,
    endDate: clientTour.endDate,
    waypoints: (clientTour.waypoints || []).map((wp) => ({
      locationName: wp.locationName,
      note: wp.note,
    })),
  };
}

async function rankSharedTours(scoreFn, limit) {
  const shared = await Tour.find({ isShared: true }).lean();
  const scored = shared
    .map((tour) => ({ tour, score: scoreFn(tour) }))
    .filter((x) => x.score > 0)
    .sort((a, b) => b.score - a.score)
    .slice(0, clampLimit(limit));

  const tours = await Promise.all(scored.map((x) => toClientTour(x.tour)));
  return {
    modelView: { results: tours.map(toModelView), matchCount: tours.length },
    tours,
  };
}

/** Substring match, tolerant of spacing differences on top of normalizeVi's diacritic/case
 *  folding - e.g. matches "Sa Pa" against stored "Sapa" (seed data spells it as one word).
 *  Both sides are already run through normalizeVi by the caller. */
function looseIncludes(target, query) {
  if (!target || !query) return false;
  if (target.includes(query)) return true;
  return target.replace(/\s+/g, "").includes(query.replace(/\s+/g, ""));
}

async function searchToursByLocation({ location, limit } = {}) {
  const query = normalizeVi(location);
  if (!query) return { modelView: { results: [], matchCount: 0 }, tours: [] };

  return rankSharedTours((tour) => {
    const inWaypoint = (tour.waypoints || []).some((wp) => looseIncludes(normalizeVi(wp.locationName), query));
    const inTitle = looseIncludes(normalizeVi(tour.title), query);
    const inDescription = looseIncludes(normalizeVi(tour.description), query);
    return (inWaypoint ? 2 : 0) + (inTitle ? 2 : 0) + (inDescription ? 1 : 0);
  }, limit);
}

async function searchToursByTheme({ theme, limit } = {}) {
  const query = normalizeVi(theme);
  if (!query) return { modelView: { results: [], matchCount: 0 }, tours: [] };

  return rankSharedTours((tour) => {
    const inTitle = looseIncludes(normalizeVi(tour.title), query);
    const inDescription = looseIncludes(normalizeVi(tour.description), query);
    const inNotes = (tour.waypoints || []).some((wp) => looseIncludes(normalizeVi(wp.note), query));
    return (inTitle ? 2 : 0) + (inDescription ? 2 : 0) + (inNotes ? 1 : 0);
  }, limit);
}

async function getWaypointsForTour({ tourId } = {}) {
  if (!tourId || typeof tourId !== "string" || !mongoose.isValidObjectId(tourId)) {
    return { modelView: { error: "invalid_tour_id" }, tours: [] };
  }

  const tourDoc = await Tour.findOne({ _id: tourId, isShared: true }).lean();
  if (!tourDoc) {
    return { modelView: { error: "tour_not_found_or_not_shared" }, tours: [] };
  }

  const clientTour = await toClientTour(tourDoc);
  return {
    modelView: {
      tourId: clientTour._id,
      title: clientTour.title,
      waypoints: clientTour.waypoints.map((wp) => ({ locationName: wp.locationName, note: wp.note })),
    },
    tours: [clientTour],
  };
}

const toolSchemas = [
  {
    type: "function",
    function: {
      name: "search_tours_by_location",
      description:
        "Find shared JourneyLog tours whose title, description, or waypoints mention a given place " +
        "(city, province, or landmark). Use this whenever the user names a place, e.g. 'Hà Nội', 'Đà Lạt', 'Ha Long Bay'.",
      parameters: {
        type: "object",
        properties: {
          location: {
            type: "string",
            description: "Place name to search for, in Vietnamese or English, e.g. 'Hà Nội' or 'Ha Noi'.",
          },
          limit: { type: "integer", minimum: 1, maximum: MAX_LIMIT, description: "Max tours to return." },
        },
        required: ["location"],
      },
    },
  },
  {
    type: "function",
    function: {
      name: "search_tours_by_theme",
      description:
        "Find shared JourneyLog tours matching a travel theme or activity (e.g. 'biển' for beach, 'núi' for " +
        "mountain, 'văn hóa' for culture, 'ẩm thực' for food) when the user hasn't named a specific place.",
      parameters: {
        type: "object",
        properties: {
          theme: { type: "string", description: "Theme or activity keyword, in Vietnamese or English." },
          limit: { type: "integer", minimum: 1, maximum: MAX_LIMIT, description: "Max tours to return." },
        },
        required: ["theme"],
      },
    },
  },
  {
    type: "function",
    function: {
      name: "get_waypoints_for_tour",
      description:
        "Get the full list of waypoints (stops) for one specific shared tour, given its tourId from an " +
        "earlier search_tours_by_location/search_tours_by_theme result.",
      parameters: {
        type: "object",
        properties: {
          tourId: { type: "string", description: "The _id of a tour returned by a previous search call." },
        },
        required: ["tourId"],
      },
    },
  },
];

const toolImplementations = {
  search_tours_by_location: searchToursByLocation,
  search_tours_by_theme: searchToursByTheme,
  get_waypoints_for_tour: getWaypointsForTour,
};

module.exports = { toolSchemas, toolImplementations };
