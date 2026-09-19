const mongoose = require("mongoose");

const tourSchema = new mongoose.Schema({
  authorId: { 
    type: String, 
    ref: "User", 
    required: true 
  },
  title: { type: String, required: true, trim: true },
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
    locationName: { type: String, trim: true },
    price: { type: Number, default: 0, min: 0 },
    coordinate: {
      type: { type: String, enum: ["Point"], default: "Point" },
      coordinates: {
        type: [Number],
        validate: {
          validator: (coordinates) => !coordinates || coordinates.length === 0 || (
            coordinates.length === 2
            && Number.isFinite(coordinates[0])
            && Number.isFinite(coordinates[1])
            && Math.abs(coordinates[0]) <= 180
            && Math.abs(coordinates[1]) <= 90
          ),
          message: "coordinates must be valid GeoJSON [longitude, latitude] values",
        },
      },
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
