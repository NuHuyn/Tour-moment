const express = require("express");
const router = express.Router();
const upload = require("../middlewares/uploadMiddleware");
const {
  uploadImage,
  createTour,
  getMyTours,
  updateTour,
  shareTour,
  copyTour,
  getPublicTours,
  getTourById,
  unlockWaypoint,
  unlockAllWaypoints,
  addWaypoint
} = require("../controllers/tourController");
const {
  getTourReviews,
  submitReview,
  updateReview,
  deleteReview
} = require("../controllers/reviewController");

router.post("/upload", upload.single("image"), uploadImage);
router.get("/my-tours/:userId", getMyTours);
router.post("/copy/:tourId", copyTour);
router.patch("/:id/share", shareTour);
router.patch("/:id/waypoint", addWaypoint);
router.post("/:id/waypoints/:index/unlock", unlockWaypoint);
router.post("/:id/unlock-all", unlockAllWaypoints);
router.get("/:id/reviews", getTourReviews);
router.post("/:id/reviews", submitReview);
router.patch("/:id/reviews", updateReview);
router.delete("/:id/reviews", deleteReview);
router.put("/:id", updateTour);
router.post("/", createTour);
router.get("/", getPublicTours);
router.get("/:id", getTourById);

module.exports = router;
