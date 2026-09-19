const path = require("path");
const fs = require("fs");

const backendRoot = path.resolve(__dirname, "..");
const cacheDir = path.join(backendRoot, ".cache", "mongodb-binaries");
const databaseDir = path.join(backendRoot, ".data", "mongodb-dev");

fs.mkdirSync(cacheDir, { recursive: true });
fs.mkdirSync(databaseDir, { recursive: true });
process.env.MONGOMS_DOWNLOAD_DIR = process.env.MONGOMS_DOWNLOAD_DIR || cacheDir;
process.env.PORT = process.env.PORT || "3000";

const { MongoMemoryServer } = require("mongodb-memory-server");
const mongoose = require("mongoose");

async function seedLocalData() {
  const User = require("../src/models/User");
  const Tour = require("../src/models/Tour");

  const authorId = "local-demo-author";
  await User.updateOne(
    { _id: authorId },
    {
      $setOnInsert: {
        _id: authorId,
        googleId: authorId,
        email: "local-demo@tourmoment.test",
        displayName: "Local Explorer",
        photoUrl: null
      }
    },
    { upsert: true }
  );

  const existingTour = await Tour.findOne({ title: "Dev Local Demo Tour", authorId });
  if (!existingTour) {
    await Tour.create({
      authorId,
      title: "Dev Local Demo Tour",
      description: "Sample data from the local MongoDB database.",
      startDate: new Date("2099-01-10T08:00:00.000Z"),
      endDate: new Date("2099-01-12T18:00:00.000Z"),
      imageUrl: "https://images.unsplash.com/photo-1528127269322-539801943592?auto=format&fit=crop&w=1200&q=80",
      status: "Upcoming",
      isShared: true,
      waypoints: [
        {
          locationName: "Bến Bạch Đằng - TP. Hồ Chí Minh",
          price: 0,
          coordinate: { type: "Point", coordinates: [106.7066, 10.7757] },
          arrivalDate: new Date("2099-01-10T08:00:00.000Z"),
          note: "Điểm dừng miễn phí trong dữ liệu local.",
          photos: []
        },
        {
          locationName: "Landmark 81 - TP. Hồ Chí Minh",
          price: 20000,
          coordinate: { type: "Point", coordinates: [106.7219, 10.7949] },
          arrivalDate: new Date("2099-01-10T11:00:00.000Z"),
          note: "Điểm mẫu có khóa để kiểm tra luồng mở khóa.",
          photos: []
        }
      ]
    });
    console.log("[Local] Seeded demo user and tour");
  }
}

async function main() {
  const mongoServer = await MongoMemoryServer.create({
    binary: { version: "8.2.6" },
    instance: {
      dbName: "tour-moment-dev",
      dbPath: databaseDir,
      port: 27017,
      storageEngine: "wiredTiger"
    }
  });

  process.env.MONGODB_URI = mongoServer.getUri("tour-moment-dev");
  const { startServer } = require("../src/server");
  const httpServer = await startServer();
  await seedLocalData();

  console.log(`[Local] MongoDB: ${process.env.MONGODB_URI}`);
  console.log(`[Local] API: http://127.0.0.1:${process.env.PORT}`);

  let stopping = false;
  const stop = async () => {
    if (stopping) return;
    stopping = true;
    await new Promise((resolve) => httpServer.close(resolve));
    await mongoose.disconnect();
    await mongoServer.stop();
    process.exit(0);
  };

  process.on("SIGINT", stop);
  process.on("SIGTERM", stop);
}

main().catch((error) => {
  console.error(`[Local] Failed to start: ${error.stack || error.message}`);
  process.exit(1);
});
