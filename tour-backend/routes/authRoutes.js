const express = require("express");
const User = require("../models/User");
const router = express.Router();

router.post("/google-login", async (req, res) => {
  try {
    const { googleId, email, displayName, photoUrl } = req.body;

    // Sử dụng findOneAndUpdate với upsert: true 
    // Nếu tìm thấy googleId -> Cập nhật thông tin mới nhất
    // Nếu KHÔNG thấy -> Tạo mới hoàn toàn
    const user = await User.findOneAndUpdate(
      { googleId: googleId }, 
      { 
        email, 
        displayName, 
        photoUrl,
        $setOnInsert: { role: "customer" } // Chỉ set role khi tạo mới
      },
      { new: true, upsert: true, runValidators: true }
    );

    res.json(user);
  } catch (err) {
    console.error("LOGIN ERROR:", err); // Xem lỗi chi tiết ở cửa sổ Node.js
    res.status(500).json({ message: "Lỗi Server: " + err.message });
  }
});

module.exports = router;