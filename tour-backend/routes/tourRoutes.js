const express = require("express");
const router = express.Router();
const Tour = require("../models/Tour");
const multer = require('multer');
const path = require('path');

// 1. Cấu hình Multer
const storage = multer.diskStorage({
    destination: (req, file, cb) => {
        cb(null, 'uploads/'); 
    },
    filename: (req, file, cb) => {
        cb(null, Date.now() + path.extname(file.originalname));
    }
});

const upload = multer({ storage: storage });

// 2. API Upload ảnh
router.post('/upload', upload.single('image'), (req, res) => {
    try {
        if (!req.file) {
            return res.status(400).json({ message: "Vui lòng chọn một file ảnh" });
        }
        const fullUrl = `${req.protocol}://${req.get('host')}/uploads/${req.file.filename}`;
        res.status(200).json({ 
            message: "Tải lên thành công",
            imageUrl: fullUrl 
        });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});


// Cập nhật thông tin Tour (Dùng cho updateTour trong Android)
router.put("/:id", async (req, res) => {
    try {
        const updatedTour = await Tour.findByIdAndUpdate(
            req.params.id,
            req.body, // Lấy toàn bộ dữ liệu mới từ Android gửi lên (bao gồm isShared)
            { new: true }
        );
        res.status(200).json(updatedTour);
    } catch (err) {
        res.status(500).json({ message: "Lỗi cập nhật tour: " + err.message });
    }
});

// --- MỚI: 3. LẤY TOUR CÁ NHÂN (Cho màn hình My Travel) ---
// Route này sẽ trả về TẤT CẢ tour, kể cả tour có isShared = false
router.get("/my-tours", async (req, res) => {
    try {
        // Trong thực tế, bạn nên lọc theo authorId từ Token/Firebase
        // Ở đây tôi lấy tất cả để đảm bảo bạn thấy được tour trong tab Completed
        const myTours = await Tour.find().sort({ startDate: -1 });
        res.json(myTours);
    } catch (err) {
        res.status(500).json({ message: "Lỗi lấy tour cá nhân: " + err.message });
    }
});

// 4. TẠO TOUR MỚI
router.post("/", async (req, res) => {
    try {
        const { authorId, title, description, startDate, endDate, imageUrl, status, waypoints } = req.body;
        
        const newTour = new Tour({
            authorId,
            title,
            description,
            startDate,
            endDate,
            imageUrl,
            status: status || "Upcoming", // Mặc định là Upcoming nếu Android không gửi
            isShared: false, // Mặc định tour mới tạo là riêng tư
            waypoints: waypoints || []
        });

        const savedTour = await newTour.save();
        res.status(201).json(savedTour);
    } catch (err) {
        res.status(500).json({ message: "Lỗi tạo tour: " + err.message });
    }
});

// 5. THÊM ĐỊA ĐIỂM (WAYPOINT)
router.patch("/:tourId/waypoint", async (req, res) => {
    try {
        const { locationName, longitude, latitude, note, arrivalDate, price, photos } = req.body;
        
        const updatedTour = await Tour.findByIdAndUpdate(
            req.params.tourId,
            {
                $push: {
                    waypoints: {
                        locationName,
                        price,
                        coordinate: {
                            type: "Point",
                            coordinates: [parseFloat(longitude || 0), parseFloat(latitude || 0)] 
                        },
                        arrivalDate,
                        note,
                        photos: photos || [] // Thêm mảng ảnh cho từng điểm dừng
                    }
                }
            },
            { new: true }
        );
        res.json(updatedTour);
    } catch (err) {
        res.status(500).json({ message: "Lỗi thêm địa điểm: " + err.message });
    }
});

// 6. LẤY DANH SÁCH TOUR CÔNG KHAI (Màn hình Home)
router.get("/", async (req, res) => {
    try {
        const sharedTours = await Tour.find({ isShared: true })
            .populate("authorId", "displayName photoUrl")
            .sort({ startDate: -1 });
        res.json(sharedTours);
    } catch (err) {
        res.status(500).json({ message: "Lỗi lấy dữ liệu: " + err.message });
    }
});

// Cập nhật trạng thái isShared của tour
router.patch("/:id/share", async (req, res) => {
    try {
        const updatedTour = await Tour.findByIdAndUpdate(
            req.params.id,
            { isShared: true }, // Luôn đặt thành true khi nhấn share
            { new: true }
        );
        res.status(200).json(updatedTour);
    } catch (err) {
        res.status(500).json({ message: "Lỗi update: " + err.message });
    }
});


module.exports = router;