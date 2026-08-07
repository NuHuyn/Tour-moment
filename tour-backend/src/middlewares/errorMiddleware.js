// Middleware bắt lỗi 404 cho các route không tồn tại
const notFoundHandler = (req, res, next) => {
  const error = new Error(`Not Found - ${req.originalUrl}`);
  res.status(404);
  next(error);
};

// Middleware xử lý lỗi tập trung
const errorHandler = (err, req, res, next) => {
  const statusCode = res.statusCode && res.statusCode !== 200 ? res.statusCode : 500;
  console.error(`[API Error] ${req.method} ${req.originalUrl}:`, err);
  
  res.status(statusCode).json({
    message: err.message || "Lỗi Server",
    stack: process.env.NODE_ENV === "production" ? null : err.stack
  });
};

module.exports = {
  notFoundHandler,
  errorHandler
};
