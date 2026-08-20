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
  addWaypoint
} = require("../controllers/tourController");

router.post("/upload", upload.single("image"), uploadImage);
router.get("/my-tours/:userId", getMyTours);
router.post("/copy/:tourId", copyTour);
router.patch("/:id/share", shareTour);
router.patch("/:id/waypoint", addWaypoint);
router.put("/:id", updateTour);
router.post("/", createTour);
router.get("/", getPublicTours);

module.exports = router;
