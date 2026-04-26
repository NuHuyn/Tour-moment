const express = require("express");
const router = express.Router();
const Tour = require("../models/Tour");
const multer = require('multer');
const path = require('path');

// --- CẤU HÌNH MULTER ---
const storage = multer.diskStorage({
    destination: (req, file, cb) => { cb(null, 'uploads/'); },
    filename: (req, file, cb) => { cb(null, Date.now() + path.extname(file.originalname)); }
});
const upload = multer({ storage: storage, limits: { fileSize: 10 * 1024 * 1024 } });

// --- 1. UPLOAD ẢNH (Dùng trực tiếp IP Google Cloud để tránh lỗi ảnh trên điện thoại thật) ---
router.post("/upload", upload.single('image'), (req, res) => {
    try {
        if (!req.file) {
            return res.status(400).json({ message: "No file uploaded" });
        }
        
        // SỬA Ở ĐÂY: Dùng trực tiếp IP Google Cloud thay vì dùng req.get('host')
        const imageUrl = `http://10.128.0.2/uploads/${req.file.filename}`;
        
        res.status(200).json({ imageUrl: imageUrl });
    } catch (err) {
        console.error("Upload Error:", err);
        res.status(500).json({ message: "Server error during upload: " + err.message });
    }
});

// --- 2. TẠO TOUR MỚI ---
router.post("/", async (req, res) => {
    try {
        const newTour = new Tour({ ...req.body, isShared: false });
        const savedTour = await newTour.save();
        res.status(201).json(savedTour); // Bỏ populate
    } catch (err) {
        res.status(500).json({ message: "Lỗi tạo tour: " + err.message });
    }
});

// --- 3. LẤY TOUR CÁ NHÂN (HÀM QUAN TRỌNG NHẤT) ---
router.get("/my-tours/:userId", async (req, res) => {
    try {
        const userId = req.params.userId;
        const statusFilter = req.query.status; 
        const now = new Date();

        // Tìm tour của User (Bỏ populate để tránh crash ID)
        let tours = await Tour.find({ authorId: userId }).sort({ createdAt: -1 });

        // Tự động cập nhật status theo ngày
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

        // Lọc theo nút bấm trên App
        const filteredTours = updatedTours.filter(t => t.status === statusFilter);
        res.json(filteredTours);
    } catch (err) {
        res.status(500).json({ message: "Lỗi lấy danh sách tour: " + err.message });
    }
});

// --- 4. CẬP NHẬT TOUR ---
router.put("/:id", async (req, res) => {
    try {
        const updatedTour = await Tour.findByIdAndUpdate(req.params.id, req.body, { new: true });
        res.status(200).json(updatedTour);
    } catch (err) {
        res.status(500).json({ message: "Lỗi cập nhật: " + err.message });
    }
});


// --- 6. CHIA SẺ TOUR ---
router.patch("/:id/share", async (req, res) => {
    try {
        const updatedTour = await Tour.findByIdAndUpdate(req.params.id, { isShared: true }, { new: true });
        res.status(200).json(updatedTour);
    } catch (err) {
        res.status(500).json({ message: "Lỗi share: " + err.message });
    }
});

// --- 7. COPY TOUR ---a
router.post("/copy/:tourId", async (req, res) => {
    try {
        const { userId } = req.body; // ID của nick thứ 2
        const originalTour = await Tour.findById(req.params.tourId);
        
        if (!originalTour) {
            return res.status(404).json({ message: "Không tìm thấy tour gốc" });
        }

        // Nhân bản dữ liệu tour
        const tourData = originalTour.toObject();
        delete tourData._id;        // Xóa ID cũ
        delete tourData.createdAt;  // Xóa ngày tạo cũ
        delete tourData.updatedAt;  // Xóa ngày cập nhật cũ

        const clonedTour = new Tour({
            ...tourData,
            authorId: userId,          
            isShared: false,           
            status: "Upcoming", 
            
            // QUAN TRỌNG: Đặt lại ngày đi/về vào tương lai để nó hiện ở tab Upcoming
            // Ngày đi: Ngày mai (Date.now + 1 ngày)
            startDate: new Date(Date.now() + 24 * 60 * 60 * 1000), 
            // Ngày về: 4 ngày sau
            endDate: new Date(Date.now() + 4 * 24 * 60 * 60 * 1000)
        });

        const savedTour = await clonedTour.save();
        res.status(201).json(savedTour);
    } catch (err) {
        console.error("LỖI COPY:", err);
        res.status(500).json({ message: "Lỗi khi copy tour: " + err.message });
    }
});


// LẤY DANH SÁCH TOUR CÔNG KHAI (HomeActivity gọi)
router.get("/", async (req, res) => {
    try {
        const User = require("../models/User");

        // 1. Lấy tour đã chia sẻ
        let sharedTours = await Tour.find({ isShared: true }).lean().sort({ createdAt: -1 });

        // 2. Gán thông tin Author bằng cách tìm theo googleId
        const finalTours = await Promise.all(sharedTours.map(async (tour) => {
            // SỬA Ở ĐÂY: Tìm theo googleId thay vì findById
            const authorData = await User.findOne({ googleId: tour.authorId }).select("displayName photoUrl");
            
            return { 
                ...tour, 
                author: authorData || { displayName: "Traveler", photoUrl: "" } 
            };
        }));

        res.json(finalTours);
    } catch (err) {
        console.error("LỖI HOME:", err);
        res.status(500).json({ message: "Lỗi lấy dữ liệu Home: " + err.message });
    }
});

module.exports = router;