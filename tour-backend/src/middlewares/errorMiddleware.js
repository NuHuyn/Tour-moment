// Middleware bắt lỗi 404 cho các route không tồn tại
const notFoundHandler = (req, res, next) => {
  const error = new Error(`Not Found - ${req.originalUrl}`);
  res.status(404);
  next(error);
};

// Middleware xử lý lỗi tập trung
const errorHandler = (err, req, res, next) => {
  const reportedStatus = Number(err.statusCode || err.status);
  let statusCode = reportedStatus >= 400 && reportedStatus <= 599
    ? reportedStatus
    : res.statusCode && res.statusCode !== 200 ? res.statusCode : 500;
  if (err.name === "CastError" || err.name === "ValidationError" || err.name === "MulterError") {
    statusCode = 400;
  } else if (err.code === 11000) {
    statusCode = 409;
  }
  if (statusCode >= 500) {
    console.error(`[API Error] ${req.method} ${req.originalUrl}:`, err);
  } else if (process.env.NODE_ENV !== "test") {
    console.warn(`[API ${statusCode}] ${req.method} ${req.originalUrl}: ${err.message}`);
  }
  
  res.status(statusCode).json({
    message: err.message || "Lỗi Server",
    stack: process.env.NODE_ENV === "production" ? null : err.stack
  });
};

module.exports = {
  notFoundHandler,
  errorHandler
};
