const mongoose = require("mongoose");
const fs = require("fs");
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

const isSupportedImage = async (filePath) => {
  const handle = await fs.promises.open(filePath, "r");
  try {
    const header = Buffer.alloc(12);
    const { bytesRead } = await handle.read(header, 0, header.length, 0);
    if (bytesRead < 6) return false;
    const isJpeg = header[0] === 0xff && header[1] === 0xd8 && header[2] === 0xff;
    const isPng = header.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]));
    const signature = header.toString("ascii");
    const isGif = signature.startsWith("GIF87a") || signature.startsWith("GIF89a");
    const isWebp = signature.startsWith("RIFF") && signature.slice(8, 12) === "WEBP";
    return isJpeg || isPng || isGif || isWebp;
  } finally {
    await handle.close();
  }
};

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
const uploadImage = async (req, res, next) => {
  try {
    if (!req.file) {
      return res.status(400).json({ message: "No file uploaded" });
    }

    if (!(await isSupportedImage(req.file.path))) {
      await fs.promises.unlink(req.file.path);
      return res.status(400).json({ message: "Uploaded file is not a valid supported image" });
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
    if (!mongoose.isValidObjectId(req.params.id)) {
      return res.status(400).json({ message: "tourId is invalid" });
    }
    if (req.body.waypoints) {
      req.body.waypoints = normalizeWaypoints(req.body.waypoints);
    }
    const allowedFields = [
      "title", "description", "startDate", "endDate", "imageUrl", "videoUrl", "status", "waypoints",
    ];
    const updates = Object.fromEntries(
      Object.entries(req.body).filter(([key]) => allowedFields.includes(key))
    );
    const updatedTour = await Tour.findByIdAndUpdate(req.params.id, updates, {
      returnDocument: "after",
      runValidators: true,
    });
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
    if (!mongoose.isValidObjectId(req.params.id)) {
      return res.status(400).json({ message: "tourId is invalid" });
    }
    const updatedTour = await Tour.findByIdAndUpdate(
      req.params.id,
      { isShared: true },
      { returnDocument: "after" }
    );
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

    if (!mongoose.isValidObjectId(req.params.tourId)) {
      return res.status(400).json({ message: "tourId is invalid" });
    }

    const originalTour = await Tour.findById(req.params.tourId);
    if (!originalTour || !originalTour.isShared) {
      return res.status(404).json({ message: "Không tìm thấy tour gốc" });
    }

    const tourData = originalTour.toObject();
    delete tourData._id;
    delete tourData.createdAt;
    delete tourData.updatedAt;

    const unlockedIndexSet = await getUnlockedIndexSet(deviceId, req.params.tourId);
    const hasLockedWaypoint = (tourData.waypoints || []).some(
      (waypoint, index) => isLocked(waypoint) && !unlockedIndexSet.has(index)
    );
    if (hasLockedWaypoint) {
      return res.status(403).json({
        message: "Unlock all paid waypoints on this device before copying the tour",
      });
    }

    const clonedTour = new Tour({
      ...tourData,
      authorId: userId,
      originalTourId: originalTour.originalTourId || originalTour._id,
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
    const tourIds = sharedTours.map((tour) => tour._id);
    const authorIds = [...new Set(sharedTours.map((tour) => tour.authorId).filter(Boolean))];
    const [unlockedByTour, ratingByTour, authors] = await Promise.all([
      getUnlockedIndexSetsForTours(deviceId, tourIds),
      getRatingSummariesForTours(tourIds),
      User.find({ googleId: { $in: authorIds } }).select("googleId displayName photoUrl -_id").lean(),
    ]);
    const authorById = new Map(authors.map((author) => [author.googleId, {
      displayName: author.displayName,
      photoUrl: author.photoUrl,
    }]));

    const finalTours = sharedTours.map((tour) => {
      const { authorId, ...publicTour } = tour;
      const rating = ratingByTour.get(String(tour._id)) || { avgRating: 0, reviewCount: 0 };
      return {
        ...publicTour,
        waypoints: redactTourWaypoints(tour.waypoints, tour._id, unlockedByTour.get(String(tour._id))),
        author: authorById.get(authorId) || { displayName: "Traveler", photoUrl: null },
        avgRating: rating.avgRating,
        reviewCount: rating.reviewCount
      };
    });

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

    const unlockedIndexSet = await getUnlockedIndexSet(deviceId, id);
    const waypoints = redactTourWaypoints(tour.waypoints, id, unlockedIndexSet);

    const authorData = await User.findOne({ googleId: tour.authorId })
      .select("displayName photoUrl -_id")
      .lean();
    const { authorId, ...publicTour } = tour;
    res.json({ ...publicTour, waypoints, author: authorData || { displayName: "Traveler", photoUrl: null } });
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
    if (!mongoose.isValidObjectId(id)) {
      return res.status(400).json({ message: "tourId is invalid" });
    }
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
      { returnDocument: "after", runValidators: true }
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
