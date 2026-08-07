const express = require("express");
const router = express.Router();

const authRoutes = require("./authRoutes");
const tourRoutes = require("./tourRoutes");

router.use("/auth", authRoutes);
router.use("/tours", tourRoutes);

module.exports = router;
