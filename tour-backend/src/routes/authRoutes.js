const express = require("express");
const router = express.Router();
const { verifyFirebaseUser } = require("../controllers/authController");

router.post("/verify", verifyFirebaseUser);

module.exports = router;
