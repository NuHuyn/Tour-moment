const Tour = require("../models/Tour");
const User = require("../models/User");

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
    const { userId } = req.body;
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
// @route   GET /api/tours
// @access  Public
const getPublicTours = async (req, res, next) => {
  try {
    const sharedTours = await Tour.find({ isShared: true }).lean().sort({ createdAt: -1 });

    const finalTours = await Promise.all(
      sharedTours.map(async (tour) => {
        const authorData = await User.findOne({ googleId: tour.authorId }).select("displayName photoUrl");
        return { 
          ...tour, 
          author: authorData || { displayName: "Traveler", photoUrl: "" } 
        };
      })
    );

    res.json(finalTours);
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
  addWaypoint
};
