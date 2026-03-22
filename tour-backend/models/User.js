const mongoose = require("mongoose");

const userSchema = new mongoose.Schema({
  full_name: String,
  email: { type: String, unique: true },
  password_hash: String,
  phone: String,
  role: {
    type: String,
    enum: ["customer", "admin", "partner"],
    default: "customer"
  },
  created_at: {
    type: Date,
    default: Date.now
  }
});

module.exports = mongoose.model("User", userSchema);