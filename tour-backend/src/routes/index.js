const express = require("express");
const router = express.Router();

const authRoutes = require("./authRoutes");
const tourRoutes = require("./tourRoutes");
const chatRoutes = require("./chatRoutes");

router.use("/auth", authRoutes);
router.use("/tours", tourRoutes);
router.use("/chat", chatRoutes);

module.exports = router;
