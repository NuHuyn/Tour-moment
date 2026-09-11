const express = require("express");
const cors = require("cors");
const path = require("path");

const routes = require("./routes");
const { notFoundHandler, errorHandler } = require("./middlewares/errorMiddleware");

const app = express();

// Middlewares
app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Serve static uploads
app.use("/uploads", express.static(path.join(__dirname, "../uploads")));

// Root route check
app.get("/", (req, res) => {
  res.send("JourneyLog API is running...");
});

// API Routes
app.use("/api", routes);

// Error Handling Middlewares
app.use(notFoundHandler);
app.use(errorHandler);

module.exports = app;
