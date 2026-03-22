const express = require("express");
const bcrypt = require("bcryptjs");
const User = require("../models/User");

const router = express.Router();

router.post("/register", async (req, res) => {
  try {

    const { full_name, email, password } = req.body;

    const hash = await bcrypt.hash(password, 10);

    const user = new User({
      full_name,
      email,
      password_hash: hash
    });

    await user.save();

    res.json(user);

  } catch (error) {
    res.status(500).json(error);
  }
});

router.post("/login", async (req, res) => {

  const { email, password } = req.body;

  const user = await User.findOne({ email });

  if (!user) return res.status(400).json("User not found");

  const isMatch = await bcrypt.compare(password, user.password_hash);

  if (!isMatch) return res.status(400).json("Wrong password");

  res.json(user);
});

module.exports = router;