const mongoose = require("mongoose");
const Tour = require("../models/Tour");
const User = require("../models/User");
const WaypointUnlock = require("../models/WaypointUnlock");
const {
  isLocked,
  redactTourWaypoints,
  getUnlockedIndexSet,
  getUnlockedIndexSetsForTours,
} = require("../services/waypointVisibility");
const { getRatingSummariesForTours } = require("../services/reviewStats");

// Helper to normalize coordinates from [latitude, longitude] to standard GeoJSON [longitude, latitude]
const normalizeWaypoints = (waypoints) => {
  if (!waypoints || !Array.isArray(waypoints)) return waypoints;
  return waypoints.map(wp => {
    if (wp.coordinate && wp.coordinate.coordinates) {
      const [first, second] = wp.coordinate.coordinates;
      // In Vietnam: latitude is ~8 to ~23 (always <= 90), longitude is ~102 to ~109 (always > 90)
      // If coordinates[0] (latitude) <= 90 and coordinates[1] (longitude) > 90,
      // it means they are flipped, so swap them to standard GeoJSON: [longitude, latitude]
      if (Math.abs(first) <= 90 && Math.abs(second) > 90) {
        wp.coordinate.coordinates = [second, first];
      }
    }
    return wp;
  });
};

// @desc    Upload ảnh tour
// @route   POST /api/tours/upload
// @access  Public
const uploadImage = (req, res, next) => {
  try {
    if (!req.file) {
      return res.status(400).json({ message: "No file uploaded" });
    }

    // Ưu tiên dùng BASE_URL từ biến môi trường, nếu không có thì lấy host từ request
    const baseUrl = process.env.BASE_URL || `${req.protocol}://${req.get("host")}`;
    const imageUrl = `${baseUrl}/uploads/${req.file.filename}`;

    res.status(200).json({ imageUrl });
  } catch (err) {
    next(err);
  }
};

// @desc    Tạo tour mới
// @route   POST /api/tours
// @access  Public
const createTour = async (req, res, next) => {
  try {
    if (req.body.waypoints) {
      req.body.waypoints = normalizeWaypoints(req.body.waypoints);
    }
    const newTour = new Tour({ ...req.body, isShared: false });
    const savedTour = await newTour.save();
    res.status(201).json(savedTour);
  } catch (err) {
    next(err);
  }
};

// @desc    Lấy danh sách tour cá nhân
// @route   GET /api/tours/my-tours/:userId
// @access  Public
const getMyTours = async (req, res, next) => {
  try {
    const { userId } = req.params;
    const statusFilter = req.query.status;
    const now = new Date();

    let tours = await Tour.find({ authorId: userId }).sort({ createdAt: -1 });

    // Tự động cập nhật status theo thời gian thực
    const updatedTours = tours.map(tour => {
      const tourObj = tour.toObject();
      if (tourObj.endDate && new Date(tourObj.endDate) <= now) {
        tourObj.status = "Completed";
      } else if (new Date(tourObj.startDate) <= now && (!tourObj.endDate || new Date(tourObj.endDate) > now)) {
        tourObj.status = "Ongoing";
      } else {
        tourObj.status = "Upcoming";
      }
      return tourObj;
    });

    const filteredTours = statusFilter
      ? updatedTours.filter(t => t.status === statusFilter)
      : updatedTours;

    // No waypoint redaction here: this endpoint only ever returns tours the caller themselves
    // authored (authorId === userId) - locking is a viewer-facing paywall on OTHER people's
    // shared tours, not on your own content, so an owner always sees their own tour in full.
    res.json(filteredTours);
  } catch (err) {
    next(err);
  }
};

// @desc    Cập nhật thông tin tour
// @route   PUT /api/tours/:id
// @access  Public
const updateTour = async (req, res, next) => {
  try {
    if (req.body.waypoints) {
      req.body.waypoints = normalizeWaypoints(req.body.waypoints);
    }
    const updatedTour = await Tour.findByIdAndUpdate(req.params.id, req.body, { new: true });
    if (!updatedTour) {
      return res.status(404).json({ message: "Không tìm thấy tour" });
    }
    res.status(200).json(updatedTour);
  } catch (err) {
    next(err);
  }
};

// @desc    Chia sẻ tour công khai
// @route   PATCH /api/tours/:id/share
// @access  Public
const shareTour = async (req, res, next) => {
  try {
    const updatedTour = await Tour.findByIdAndUpdate(req.params.id, { isShared: true }, { new: true });
    if (!updatedTour) {
      return res.status(404).json({ message: "Không tìm thấy tour" });
    }
    res.status(200).json(updatedTour);
  } catch (err) {
    next(err);
  }
};

// @desc    Nhân bản / Copy tour
// @route   POST /api/tours/copy/:tourId
// @access  Public
const copyTour = async (req, res, next) => {
  try {
    const { userId, deviceId } = req.body;
    if (!userId) {
      return res.status(400).json({ message: "Thiếu thông tin userId người nhận" });
    }

    const originalTour = await Tour.findById(req.params.tourId);
    if (!originalTour) {
      return res.status(404).json({ message: "Không tìm thấy tour gốc" });
    }

    const tourData = originalTour.toObject();
    delete tourData._id;
    delete tourData.createdAt;
    delete tourData.updatedAt;

    // Security: without this, copying someone else's shared tour would smuggle full real
    // coordinates/names for waypoints the copying device never unlocked into the new (private)
    // clone, bypassing the exact same redaction getPublicTours applies on read. Whatever this
    // device hasn't unlocked stays redacted in the copy too - unlocking later works the same way
    // it already does for any other tourId (WaypointUnlock is keyed by tourId, and the clone gets
    // its own new _id).
    const unlockedIndexSet = await getUnlockedIndexSet(deviceId, req.params.tourId);
    tourData.waypoints = redactTourWaypoints(tourData.waypoints, req.params.tourId, unlockedIndexSet);

    const clonedTour = new Tour({
      ...tourData,
      authorId: userId,
      isShared: false,
      status: "Upcoming",
      startDate: new Date(Date.now() + 24 * 60 * 60 * 1000),
      endDate: new Date(Date.now() + 4 * 24 * 60 * 60 * 1000)
    });

    const savedTour = await clonedTour.save();
    res.status(201).json(savedTour);
  } catch (err) {
    next(err);
  }
};

// @desc    Lấy danh sách tour công khai (Home screen)
// @route   GET /api/tours?deviceId=...
// @access  Public
const getPublicTours = async (req, res, next) => {
  try {
    const deviceId = req.query.deviceId || null;
    const sharedTours = await Tour.find({ isShared: true }).lean().sort({ createdAt: -1 });
    const unlockedByTour = await getUnlockedIndexSetsForTours(deviceId, sharedTours.map((t) => t._id));
    const ratingByTour = await getRatingSummariesForTours(sharedTours.map((t) => t._id));

    const finalTours = await Promise.all(
      sharedTours.map(async (tour) => {
        const authorData = await User.findOne({ googleId: tour.authorId }).select("displayName photoUrl");
        const rating = ratingByTour.get(String(tour._id)) || { avgRating: 0, reviewCount: 0 };
        return {
          ...tour,
          waypoints: redactTourWaypoints(tour.waypoints, tour._id, unlockedByTour.get(String(tour._id))),
          author: authorData || { displayName: "Traveler", photoUrl: null },
          avgRating: rating.avgRating,
          reviewCount: rating.reviewCount
        };
      })
    );

    res.json(finalTours);
  } catch (err) {
    next(err);
  }
};

// @desc    Lấy chi tiết 1 tour (đã áp dụng che giấu waypoint khóa theo thiết bị)
// @route   GET /api/tours/:id?deviceId=...
// @access  Public
const getTourById = async (req, res, next) => {
  try {
    const { id } = req.params;
    if (!mongoose.isValidObjectId(id)) {
      return res.status(400).json({ message: "tourId không hợp lệ" });
    }
    const deviceId = req.query.deviceId || null;

    const tour = await Tour.findById(id).lean();
    if (!tour) {
      return res.status(404).json({ message: "Không tìm thấy tour" });
    }

    // Owner always sees their own tour in full (same rule as getMyTours) - locking only applies
    // to viewers who aren't the author.
    const isOwner = req.query.userId && tour.authorId === req.query.userId;
    let waypoints = tour.waypoints;
    if (!isOwner) {
      const unlockedIndexSet = await getUnlockedIndexSet(deviceId, id);
      waypoints = redactTourWaypoints(tour.waypoints, id, unlockedIndexSet);
    }

    const authorData = await User.findOne({ googleId: tour.authorId }).select("displayName photoUrl");
    res.json({ ...tour, waypoints, author: authorData || { displayName: "Traveler", photoUrl: null } });
  } catch (err) {
    next(err);
  }
};

// @desc    Mở khóa 1 waypoint cho 1 thiết bị (demo paywall - chưa gắn cổng thanh toán thật, xem
//          WaypointLockManager.java phía Android; endpoint này chỉ ghi nhận "đã trả tiền" để server
//          có thể ngừng che giấu waypoint đó cho đúng thiết bị đã trả).
// @route   POST /api/tours/:id/waypoints/:index/unlock
// @body    { deviceId }
// @access  Public
const unlockWaypoint = async (req, res, next) => {
  try {
    const { id, index } = req.params;
    const { deviceId } = req.body;
    const waypointIndex = Number(index);

    if (!deviceId) {
      return res.status(400).json({ message: "Thiếu deviceId" });
    }
    if (!mongoose.isValidObjectId(id) || !Number.isInteger(waypointIndex) || waypointIndex < 0) {
      return res.status(400).json({ message: "tourId hoặc waypoint index không hợp lệ" });
    }

    const tour = await Tour.findById(id).select("waypoints").lean();
    if (!tour || !tour.waypoints || !tour.waypoints[waypointIndex]) {
      return res.status(404).json({ message: "Không tìm thấy waypoint" });
    }
    if (!isLocked(tour.waypoints[waypointIndex])) {
      return res.status(200).json({ alreadyUnlocked: true }); // free waypoint - nothing to record
    }

    await WaypointUnlock.updateOne(
      { deviceId, tourId: id, waypointIndex },
      { $setOnInsert: { deviceId, tourId: id, waypointIndex } },
      { upsert: true }
    );

    res.status(200).json({ unlocked: true });
  } catch (err) {
    next(err);
  }
};

// @desc    Mở khóa toàn bộ waypoint còn khóa của 1 tour cho 1 thiết bị (nút "Unlock Now" - full trip).
// @route   POST /api/tours/:id/unlock-all
// @body    { deviceId }
// @access  Public
const unlockAllWaypoints = async (req, res, next) => {
  try {
    const { id } = req.params;
    const { deviceId } = req.body;

    if (!deviceId) {
      return res.status(400).json({ message: "Thiếu deviceId" });
    }
    if (!mongoose.isValidObjectId(id)) {
      return res.status(400).json({ message: "tourId không hợp lệ" });
    }

    const tour = await Tour.findById(id).select("waypoints").lean();
    if (!tour) {
      return res.status(404).json({ message: "Không tìm thấy tour" });
    }

    const lockedIndexes = (tour.waypoints || [])
      .map((wp, i) => (isLocked(wp) ? i : -1))
      .filter((i) => i !== -1);

    if (lockedIndexes.length > 0) {
      await WaypointUnlock.bulkWrite(
        lockedIndexes.map((waypointIndex) => ({
          updateOne: {
            filter: { deviceId, tourId: id, waypointIndex },
            update: { $setOnInsert: { deviceId, tourId: id, waypointIndex } },
            upsert: true,
          },
        }))
      );
    }

    res.status(200).json({ unlockedCount: lockedIndexes.length });
  } catch (err) {
    next(err);
  }
};

// @desc    Thêm waypoint vào tour
// @route   PATCH /api/tours/:id/waypoint
// @access  Public
const addWaypoint = async (req, res, next) => {
  try {
    const { id } = req.params;
    let waypoint = req.body;

    // Normalize coordinates if single waypoint
    if (waypoint.coordinate && waypoint.coordinate.coordinates) {
      const [first, second] = waypoint.coordinate.coordinates;
      if (Math.abs(first) <= 90 && Math.abs(second) > 90) {
        waypoint.coordinate.coordinates = [second, first];
      }
    }

    const updatedTour = await Tour.findByIdAndUpdate(
      id,
      { $push: { waypoints: waypoint } },
      { new: true, runValidators: true }
    );

    if (!updatedTour) {
      return res.status(404).json({ message: "Không tìm thấy tour" });
    }

    res.status(200).json(updatedTour);
  } catch (err) {
    next(err);
  }
};

module.exports = {
  uploadImage,
  createTour,
  getMyTours,
  updateTour,
  shareTour,
  copyTour,
  getPublicTours,
  getTourById,
  unlockWaypoint,
  unlockAllWaypoints,
  addWaypoint
};
