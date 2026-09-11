const mongoose = require("mongoose");
const Review = require("../models/Review");

/**
 * Batch avgRating/reviewCount lookup for many tours at once (1 aggregate query) - used by
 * getPublicTours so each Discovery card can render its star row without an N+1 query per tour.
 * Same $avg/$sum shape as reviewController.getTourReviews' single-tour aggregate, just grouped by
 * tripId instead of filtered to one.
 */
const getRatingSummariesForTours = async (tourIds) => {
  if (!tourIds || tourIds.length === 0) return new Map();
  const objectIds = tourIds
    .filter((id) => mongoose.isValidObjectId(id))
    .map((id) => new mongoose.Types.ObjectId(id));
  if (objectIds.length === 0) return new Map();

  const agg = await Review.aggregate([
    { $match: { tripId: { $in: objectIds } } },
    { $group: { _id: "$tripId", avgRating: { $avg: "$rating" }, reviewCount: { $sum: 1 } } },
  ]);

  const map = new Map();
  agg.forEach((r) => {
    map.set(String(r._id), { avgRating: Math.round(r.avgRating * 10) / 10, reviewCount: r.reviewCount });
  });
  return map;
};

module.exports = { getRatingSummariesForTours };
