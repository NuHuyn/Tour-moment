const mongoose = require("mongoose");

const tourSchema = new mongoose.Schema({
  authorId: { type: mongoose.Schema.Types.ObjectId, ref: "User" },
  title: { type: String, required: true },
  description: String,
  startDate: { type: Date, required: true },
  endDate: { type: Date, default: null },
  imageUrl: String, // Lưu link ảnh bìa tour
  
  // --- THÊM: Để lưu link video MP4 được tạo từ slide ảnh ---
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
    photos: [String] // Mảng các link ảnh của chặng này
  }]
}, { 
  timestamps: true // Tự động thêm createdAt và updatedAt để quản lý tour mới/cũ
});

// Tạo index cho tọa độ để sau này bạn có thể tìm kiếm tour theo vị trí (nếu cần)
tourSchema.index({ "waypoints.coordinate": "2dsphere" });

module.exports = mongoose.model("Tour", tourSchema);