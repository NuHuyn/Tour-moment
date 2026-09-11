const mongoose = require("mongoose");

/**
 * One review per (tripId, userId) - enforced by the unique compound index below. userId is a
 * User._id (== googleId, see User.js/authController.js); reviewer displayName/photoUrl are
 * deliberately NOT duplicated here - reviewController looks them up from User at read time
 * (same batch-lookup pattern tourController already uses for tour.author), so a later profile
 * name/photo change is reflected automatically instead of going stale on old reviews.
 */
const reviewSchema = new mongoose.Schema(
  {
    tripId: { type: mongoose.Schema.Types.ObjectId, ref: "Tour", required: true },
    userId: { type: String, ref: "User", required: true },
    rating: { type: Number, required: true, min: 1, max: 5 },
    comment: { type: String, default: "", trim: true, maxlength: 1000 },
  },
  { timestamps: true }
);

reviewSchema.index({ tripId: 1, userId: 1 }, { unique: true });
reviewSchema.index({ tripId: 1, createdAt: -1 });

module.exports = mongoose.model("Review", reviewSchema);
