const multer = require("multer");
const path = require("path");
const fs = require("fs");
const crypto = require("crypto");

const uploadsDir = path.join(__dirname, "../../uploads");
fs.mkdirSync(uploadsDir, { recursive: true });

const allowedImageTypes = new Map([
  ["image/jpeg", ".jpg"],
  ["image/png", ".png"],
  ["image/webp", ".webp"],
  ["image/gif", ".gif"],
]);

const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    cb(null, uploadsDir);
  },
  filename: (req, file, cb) => {
    cb(null, `${crypto.randomUUID()}${allowedImageTypes.get(file.mimetype)}`);
  }
});

const upload = multer({
  storage,
  limits: { fileSize: 10 * 1024 * 1024 },
  fileFilter: (req, file, cb) => {
    if (!allowedImageTypes.has(file.mimetype)) {
      const error = new Error("Only JPEG, PNG, WebP, and GIF images are supported");
      error.statusCode = 400;
      return cb(error);
    }
    cb(null, true);
  },
});

module.exports = upload;
