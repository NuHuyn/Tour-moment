const express = require("express");
const User = require("../models/User");
const router = express.Router();

// Route xử lý khi user đăng nhập bằng Google từ Android
router.post("/google-login", async (req, res) => {
  try {
    const { googleId, email, displayName, photoUrl } = req.body;

    // Kiểm tra xem user này đã tồn tại trong database chưa
    let user = await User.findOne({ googleId });

    if (!user) {
      // Nếu chưa có thì tạo mới (đăng ký)
      user = new User({
        googleId,
        email,
        displayName,
        photoUrl
      });
      await user.save();
    }

    // Trả về thông tin user (đăng nhập thành công)
    res.json(user);
  } catch (err) {
    res.status(500).json({ message: "Lỗi Server: " + err.message });
  }
});

module.exports = router;