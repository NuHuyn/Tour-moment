const mongoose = require("mongoose");

const tourSchema = new mongoose.Schema({
  tour_name: String,
  description: String,
  location: String,
  region: String,
  category: String,
  price: Number,
  image_url: String,
  created_by: {
    type: mongoose.Schema.Types.ObjectId,
    ref: "User"
  },
  status: {
    type: String,
    enum: ["pending", "approved", "rejected"],
    default: "pending"
  },
  created_at: {
    type: Date,
    default: Date.now
  }
});

module.exports = mongoose.model("Tour", tourSchema);