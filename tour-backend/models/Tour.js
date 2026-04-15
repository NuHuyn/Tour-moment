const mongoose = require("mongoose");

const tourSchema = new mongoose.Schema({
  authorId: { type: mongoose.Schema.Types.ObjectId, ref: "User" },
  title: String,
  description: String,
  startDate: { type: Date, required: true },
  endDate: { type: Date, default: null }, // Sẽ nhận dữ liệu từ Android gửi lên
  imageUrl: String, // <--- THÊM: Để lưu link ảnh bìa tour
  status: { 
    type: String, 
    enum: ["Upcoming", "Ongoing", "Completed"], 
    default: "Upcoming" 
  }, // <--- THÊM: Để lọc dữ liệu theo Tab
  isShared: { type: Boolean, default: false },
  originalTourId: { type: mongoose.Schema.Types.ObjectId, ref: "Tour", default: null },
  waypoints: [{
    locationName: String,
    price: { type: Number, default: 0 },
    coordinate: {
      type: { type: String, default: 'Point' },
      coordinates: [Number] 
    },
    arrivalDate: { type: Date, default: Date.now },
    note: String,
    photos: [String] 
  }]
});

module.exports = mongoose.model("Tour", tourSchema);