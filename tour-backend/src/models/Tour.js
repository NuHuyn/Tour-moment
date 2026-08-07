const mongoose = require("mongoose");

const tourSchema = new mongoose.Schema({
  authorId: { 
    type: String, 
    ref: "User", 
    required: true 
  },
  title: { type: String, required: true },
  description: String,
  startDate: { type: Date, required: true },
  endDate: { type: Date, default: null },
  imageUrl: String, 
  videoUrl: { type: String, default: null }, 

  status: { 
    type: String, 
    enum: ["Upcoming", "Ongoing", "Completed"], 
    default: "Upcoming" 
  },
  isShared: { type: Boolean, default: false },
  originalTourId: { type: mongoose.Schema.Types.ObjectId, ref: "Tour", default: null },
  
  waypoints: [{
    locationName: String,
    price: { type: Number, default: 0 },
    coordinate: {
      type: { type: String, default: 'Point' },
      coordinates: [Number] // [kinh độ, vĩ độ]
    },
    arrivalDate: { type: Date, default: Date.now },
    note: String,
    photos: [String]
  }]
}, { 
  timestamps: true
});

tourSchema.index({ "waypoints.coordinate": "2dsphere" });

module.exports = mongoose.model("Tour", tourSchema);
