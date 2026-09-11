const auth = require("../config/firebaseAdmin");
const User = require("../models/User");

// @desc    Xác thực Firebase ID token (Email/Password hoặc Google - client đăng nhập bằng
//          Firebase Auth SDK trước, rồi gửi idToken lên đây) và đồng bộ/khởi tạo User row tương
//          ứng trong MongoDB. Thay thế /api/auth/google-login cũ (client-supplied, không xác
//          thực) - uid trong idToken đã được Google ký và xác minh server-side, không thể giả mạo.
// @route   POST /api/auth/verify
// @body    { idToken }
// @access  Public
const verifyFirebaseUser = async (req, res, next) => {
  try {
    const { idToken } = req.body;
    if (!idToken) {
      return res.status(400).json({ message: "Thiếu idToken" });
    }

    let decoded;
    try {
      decoded = await auth.verifyIdToken(idToken);
    } catch (err) {
      return res.status(401).json({ message: "idToken không hợp lệ hoặc đã hết hạn" });
    }

    const { uid, email, name, picture } = decoded;
    if (!email) {
      return res.status(400).json({ message: "Tài khoản Firebase thiếu email" });
    }

    // uid is Firebase's stable per-account id (same value regardless of sign-in method - email/
    // password or Google - for the same account), stored in the same googleId/_id fields the
    // rest of the backend already keys tour ownership/reviews/etc. off of, so nothing downstream
    // (tourController, reviewController) needs to change.
    const user = await User.findOneAndUpdate(
      { googleId: uid },
      {
        email,
        // Don't clobber a display name/photo the user may have set with Firebase's (often null,
        // for a plain email/password signup) on every re-login - only set on first insert.
        $setOnInsert: {
          _id: uid,
          role: "customer",
          displayName: name || email.split("@")[0],
          photoUrl: picture || null,
        },
      },
      { new: true, upsert: true, runValidators: true }
    );

    res.json(user);
  } catch (err) {
    next(err);
  }
};

module.exports = {
  verifyFirebaseUser,
};
