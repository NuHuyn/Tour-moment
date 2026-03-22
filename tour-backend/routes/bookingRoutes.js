const express = require("express");
const Booking = require("../models/Booking");

const router = express.Router();

router.post("/", async (req, res) => {

  const booking = new Booking(req.body);

  await booking.save();

  res.json(booking);

});

router.get("/:userId", async (req, res) => {

  const bookings = await Booking.find({
    user_id: req.params.userId
  }).populate("tour_id");

  res.json(bookings);

});

module.exports = router;