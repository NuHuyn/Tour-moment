/**
 * Server-side enforcement of the per-waypoint paywall: a "locked" waypoint (price > 0, see the
 * price-as-unlock-flag convention in scripts/seedTours.js and tourChatTools.js) must never have
 * its real coordinates, name, or photos leave the server for a viewer whose device hasn't unlocked
 * it - hiding it only in the Android UI would still leak everything to anyone inspecting the raw
 * HTTP response.
 *
 * Used by tourController's getPublicTours/getTourById/copyTour and by tourChatTools' toClientTour,
 * so every response path that ships a shared tour's waypoints to a client goes through the same
 * redaction, instead of each place reimplementing (and potentially forgetting) it.
 */
const WaypointUnlock = require("../models/WaypointUnlock");

const FUZZ_MIN_METERS = 500;
const FUZZ_MAX_METERS = 1000;
const METERS_PER_DEGREE_LAT = 111_320;

/** Tiny deterministic string hash (djb2) - no crypto needed, this only has to be stable and
 *  well-distributed, not secure. Deterministic on purpose: the fuzzed pin must land in the same
 *  spot on every request, or a user could triangulate the real point by averaging repeated fetches. */
function hashString(str) {
  let hash = 5381;
  for (let i = 0; i < str.length; i++) {
    hash = (hash * 33) ^ str.charCodeAt(i);
  }
  return hash >>> 0; // unsigned
}

/** Offsets [lng, lat] by a deterministic 500-1000m vector seeded from tourId+index, so the same
 *  locked waypoint always shows at the same approximate spot instead of jittering per request. */
function fuzzCoordinate(lng, lat, seedStr) {
  const seed = hashString(seedStr);
  const angle = ((seed % 3600) / 3600) * 2 * Math.PI;
  const distanceMeters = FUZZ_MIN_METERS + (seed % 1000) / 1000 * (FUZZ_MAX_METERS - FUZZ_MIN_METERS);

  const dLat = (distanceMeters * Math.cos(angle)) / METERS_PER_DEGREE_LAT;
  const metersPerDegreeLng = METERS_PER_DEGREE_LAT * Math.cos((lat * Math.PI) / 180);
  const dLng = (distanceMeters * Math.sin(angle)) / metersPerDegreeLng;

  return [lng + dLng, lat + dLat];
}

/** District/area suffix after the last " - " in a waypoint name, e.g.
 *  "Nhà sàn cổ Vua Săn Voi Amakông - Buôn Đôn" -> "Buôn Đôn". Same convention TourDetailActivity's
 *  destination chips already parse client-side - kept so a locked waypoint's masked name still
 *  produces the right chip instead of breaking it. Falls back to the full name if there's no dash
 *  (teaser text only, never returned for a locked waypoint - see redactWaypoint). */
function districtSuffix(locationName) {
  if (!locationName) return "";
  const idx = locationName.lastIndexOf(" - ");
  return idx === -1 ? "" : locationName.slice(idx); // keeps the " - " separator
}

/** True if this waypoint is behind the paywall at all (this app's existing convention: price 0 =
 *  free, price > 0 = locked - see WaypointLockManager.java / scripts/seedTours.js). */
function isLocked(waypoint) {
  return Number(waypoint && waypoint.price) > 0;
}

/** Returns the waypoint as-is if free or already unlocked by this viewer; otherwise a redacted
 *  copy: masked name (district suffix kept, so destination chips still work), generic teaser note,
 *  fuzzed coordinate, and no photos (the server never learned the real photo either, so there's
 *  nothing for the client to blur - it shows its own generic "locked" artwork instead). */
function redactWaypoint(waypoint, index, tourId, unlockedIndexSet) {
  if (!isLocked(waypoint)) return { ...waypoint, locked: false };
  if (unlockedIndexSet && unlockedIndexSet.has(index)) return { ...waypoint, locked: false };

  const coords = waypoint.coordinate && waypoint.coordinate.coordinates;
  const fuzzed = coords
    ? fuzzCoordinate(coords[0], coords[1], `${tourId}:${index}`)
    : undefined;

  return {
    ...waypoint,
    locationName: `🔒 Điểm chưa mở khóa${districtSuffix(waypoint.locationName)}`,
    note: "Một điểm dừng chân thú vị đang chờ được khám phá. Mở khóa để xem chi tiết!",
    photos: [],
    coordinate: fuzzed
      ? { type: "Point", coordinates: fuzzed }
      : waypoint.coordinate,
    price: waypoint.price,
    // Explicit, unambiguous signal for the client - true only while this waypoint is both
    // paywalled (price > 0) AND this viewer/device has not unlocked it. Cheaper and more
    // robust for the client to check than pattern-matching the masked locationName/note text.
    locked: true,
  };
}

/** Set of waypoint indexes this device has already unlocked for one tour. */
async function getUnlockedIndexSet(deviceId, tourId) {
  if (!deviceId) return new Set(); // no device id -> fail closed, nothing is "unlocked"
  const records = await WaypointUnlock.find({ deviceId, tourId }).select("waypointIndex").lean();
  return new Set(records.map((r) => r.waypointIndex));
}

/** Batch version for a list of tours (getPublicTours shows many tours at once) - one query
 *  instead of N, keyed by tourId so callers can look up each tour's set. */
async function getUnlockedIndexSetsForTours(deviceId, tourIds) {
  const map = new Map(tourIds.map((id) => [String(id), new Set()]));
  if (!deviceId || tourIds.length === 0) return map; // fail closed with no deviceId
  const records = await WaypointUnlock.find({ deviceId, tourId: { $in: tourIds } })
    .select("tourId waypointIndex")
    .lean();
  records.forEach((r) => {
    const key = String(r.tourId);
    if (map.has(key)) map.get(key).add(r.waypointIndex);
  });
  return map;
}

/** Redacts every locked-and-not-yet-unlocked waypoint in a tour's waypoints array. `waypoints`
 *  may be plain objects (already .lean()'d) or Mongoose subdocuments - spread in redactWaypoint
 *  handles both. Does not mutate the input. */
function redactTourWaypoints(waypoints, tourId, unlockedIndexSet) {
  return (waypoints || []).map((wp, i) => redactWaypoint(wp, i, tourId, unlockedIndexSet));
}

module.exports = {
  isLocked,
  fuzzCoordinate,
  redactWaypoint,
  redactTourWaypoints,
  getUnlockedIndexSet,
  getUnlockedIndexSetsForTours,
};
