const User = require("../models/User");

// @desc    Đăng nhập hoặc Đăng ký bằng Google
// @route   POST /api/auth/google-login
// @access  Public
const googleLogin = async (req, res, next) => {
  try {
    const { googleId, email, displayName, photoUrl } = req.body;

    if (!googleId || !email) {
      return res.status(400).json({ message: "Thiếu thông tin googleId hoặc email" });
    }

    const user = await User.findOneAndUpdate(
      { googleId: googleId },
      {
        email,
        displayName,
        photoUrl,
        $setOnInsert: { _id: googleId, role: "customer" } // Đảm bảo set cả _id nếu tạo mới
      },
      { new: true, upsert: true, runValidators: true }
    );

    res.json(user);
  } catch (err) {
    next(err);
  }
};

module.exports = {
  googleLogin
};
