const mongoose = require("mongoose");

const connectDB = async () => {
  try {
    const connString = process.env.MONGODB_URI;
    if (!connString) {
      throw new Error("MONGODB_URI is missing in environment variables");
    }

    await mongoose.connect(connString);

    console.log(`[MongoDB] Connected to database: ${mongoose.connection.name}`);
  } catch (error) {
    console.error(`[MongoDB] Connection error: ${error.message}`);
    process.exit(1);
  }
};

module.exports = connectDB;
