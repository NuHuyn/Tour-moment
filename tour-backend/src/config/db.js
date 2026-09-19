const mongoose = require("mongoose");

const connectDB = async () => {
  const connString = process.env.MONGODB_URI;
  if (!connString) {
    throw new Error("MONGODB_URI is missing in environment variables");
  }

  await mongoose.connect(connString);
  console.log(`[MongoDB] Connected to database: ${mongoose.connection.name}`);
};

module.exports = connectDB;
