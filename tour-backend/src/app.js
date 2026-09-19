const express = require("express");
const cors = require("cors");
const path = require("path");
const mongoose = require("mongoose");

const routes = require("./routes");
const { notFoundHandler, errorHandler } = require("./middlewares/errorMiddleware");

const app = express();

// Middlewares
app.use(cors());
app.use(express.json({ limit: "1mb" }));
app.use(express.urlencoded({ extended: true, limit: "1mb" }));

// Serve static uploads
app.use("/uploads", express.static(path.join(__dirname, "../uploads"), {
  setHeaders: (res) => res.setHeader("X-Content-Type-Options", "nosniff"),
}));

// Root route check
app.get("/", (req, res) => {
  res.send("JourneyLog API is running...");
});

app.get("/health", (req, res) => {
  const databaseConnected = mongoose.connection.readyState === 1;
  res.status(databaseConnected ? 200 : 503).json({
    status: databaseConnected ? "ok" : "degraded",
    database: databaseConnected ? "connected" : "disconnected"
  });
});

// API Routes
app.use("/api", routes);

// Error Handling Middlewares
app.use(notFoundHandler);
app.use(errorHandler);

module.exports = app;
