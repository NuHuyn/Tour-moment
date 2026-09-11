const mongoose = require("mongoose");
const Review = require("../models/Review");
const Tour = require("../models/Tour");
const User = require("../models/User");

const RATING_MIN = 1;
const RATING_MAX = 5;

const isValidRating = (rating) =>
  Number.isInteger(rating) && rating >= RATING_MIN && rating <= RATING_MAX;

// @desc    Danh sách đánh giá của 1 tour + điểm trung bình/tổng số. Điểm trung bình được TÍNH
//          TRỰC TIẾP từ collection Review mỗi lần đọc (aggregate $avg/$sum), KHÔNG lưu thành
//          trường tổng hợp trên Tour - lựa chọn này tránh 2 nơi lưu dữ liệu bị lệch nhau (Tour.avg
//          vs review thực tế) mỗi khi có review được thêm/sửa/xóa, và không cần transaction giữa 2
//          collection. Đổi lại là 1 aggregate query nhỏ mỗi lần đọc - chấp nhận được vì số lượng
//          đánh giá/tour không lớn (không phải theo dõi like/view quy mô lớn).
// @route   GET /api/tours/:id/reviews
// @access  Public
const getTourReviews = async (req, res, next) => {
  try {
    const { id } = req.params;
    if (!mongoose.isValidObjectId(id)) {
      return res.status(400).json({ message: "tourId không hợp lệ" });
    }

    const [reviews, agg] = await Promise.all([
      Review.find({ tripId: id }).sort({ createdAt: -1 }).lean(),
      Review.aggregate([
        { $match: { tripId: new mongoose.Types.ObjectId(id) } },
        { $group: { _id: null, avgRating: { $avg: "$rating" }, reviewCount: { $sum: 1 } } },
      ]),
    ]);

    const userIds = [...new Set(reviews.map((r) => r.userId))];
    const users = await User.find({ _id: { $in: userIds } })
      .select("displayName photoUrl")
      .lean();
    const userById = new Map(users.map((u) => [u._id, u]));

    const items = reviews.map((r) => ({
      _id: r._id,
      userId: r.userId,
      rating: r.rating,
      comment: r.comment,
      createdAt: r.createdAt,
      updatedAt: r.updatedAt,
      user: userById.get(r.userId) || { displayName: "Người dùng JourneyLog", photoUrl: null },
    }));

    res.json({
      avgRating: agg.length ? Math.round(agg[0].avgRating * 10) / 10 : 0,
      reviewCount: agg.length ? agg[0].reviewCount : 0,
      reviews: items,
    });
  } catch (err) {
    next(err);
  }
};

// @desc    Gửi đánh giá mới cho 1 tour. Mỗi user chỉ được 1 đánh giá/tour (index unique
//          tripId+userId) - dùng PATCH để sửa đánh giá đã có thay vì gọi lại POST.
// @route   POST /api/tours/:id/reviews
// @body    { userId, rating, comment }
// @access  Public - nhưng userId phải là 1 User đã đăng nhập Google thật (có row trong
//          collection User); tài khoản Guest (id dạng "guest_<uuid>", không có row User) bị từ
//          chối - app đáng lẽ đã chặn việc này từ phía client bằng màn hình yêu cầu đăng nhập
//          trước khi gọi tới đây, đây là lớp kiểm tra thứ 2 ở server.
const submitReview = async (req, res, next) => {
  try {
    const { id } = req.params;
    const { userId, rating, comment } = req.body;

    if (!mongoose.isValidObjectId(id)) {
      return res.status(400).json({ message: "tourId không hợp lệ" });
    }
    if (!userId) {
      return res.status(400).json({ message: "Thiếu userId" });
    }
    if (!isValidRating(rating)) {
      return res.status(400).json({ message: "rating phải là số nguyên từ 1 đến 5" });
    }

    const [tourExists, user] = await Promise.all([
      Tour.exists({ _id: id }),
      User.findById(userId).select("_id"),
    ]);
    if (!tourExists) {
      return res.status(404).json({ message: "Không tìm thấy tour" });
    }
    if (!user) {
      return res.status(401).json({ message: "Cần đăng nhập bằng Google để đánh giá" });
    }

    const existing = await Review.findOne({ tripId: id, userId });
    if (existing) {
      return res
        .status(409)
        .json({ message: "Bạn đã đánh giá tour này rồi. Hãy chỉnh sửa đánh giá hiện có." });
    }

    const review = await Review.create({ tripId: id, userId, rating, comment: comment || "" });
    res.status(201).json(review);
  } catch (err) {
    if (err.code === 11000) {
      return res.status(409).json({ message: "Bạn đã đánh giá tour này rồi." });
    }
    next(err);
  }
};

// @desc    Sửa đánh giá của chính mình cho 1 tour.
// @route   PATCH /api/tours/:id/reviews
// @body    { userId, rating, comment }
// @access  Public (chỉ sửa được đánh giá khớp userId gửi lên - không có xác thực token thật,
//          giống toàn bộ phần còn lại của app hiện tại, xem TODO trong LoginActivity.java)
const updateReview = async (req, res, next) => {
  try {
    const { id } = req.params;
    const { userId, rating, comment } = req.body;

    if (!mongoose.isValidObjectId(id)) {
      return res.status(400).json({ message: "tourId không hợp lệ" });
    }
    if (!userId) {
      return res.status(400).json({ message: "Thiếu userId" });
    }
    if (!isValidRating(rating)) {
      return res.status(400).json({ message: "rating phải là số nguyên từ 1 đến 5" });
    }

    const review = await Review.findOneAndUpdate(
      { tripId: id, userId },
      { rating, comment: comment || "" },
      { new: true }
    );
    if (!review) {
      return res.status(404).json({ message: "Bạn chưa có đánh giá nào cho tour này" });
    }

    res.status(200).json(review);
  } catch (err) {
    next(err);
  }
};

// @desc    Xóa đánh giá của chính mình cho 1 tour.
// @route   DELETE /api/tours/:id/reviews
// @body    { userId }
const deleteReview = async (req, res, next) => {
  try {
    const { id } = req.params;
    const { userId } = req.body;

    if (!mongoose.isValidObjectId(id)) {
      return res.status(400).json({ message: "tourId không hợp lệ" });
    }
    if (!userId) {
      return res.status(400).json({ message: "Thiếu userId" });
    }

    const review = await Review.findOneAndDelete({ tripId: id, userId });
    if (!review) {
      return res.status(404).json({ message: "Bạn chưa có đánh giá nào cho tour này" });
    }

    res.status(200).json({ deleted: true });
  } catch (err) {
    next(err);
  }
};

module.exports = { getTourReviews, submitReview, updateReview, deleteReview };
