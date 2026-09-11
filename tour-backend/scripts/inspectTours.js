/**
 * Read-only inspection: counts current tours/waypoints, no writes. Run before migrating.
 * Usage: node scripts/inspectTours.js
 */
const path = require("path");
require("dotenv").config({ path: path.resolve(__dirname, "../.env") });

const mongoose = require("mongoose");
const Tour = require("../src/models/Tour");

async function main() {
  const uri = process.env.MONGODB_URI;
  if (!uri) throw new Error("MONGODB_URI is missing - check tour-backend/.env");

  await mongoose.connect(uri);
  console.log(`[inspect] Connected to database: ${mongoose.connection.name}`);

  const tours = await Tour.find({}).lean();
  console.log(`[inspect] Total tour documents: ${tours.length}`);
  let totalWaypoints = 0;
  tours.forEach((t) => {
    const wpCount = (t.waypoints || []).length;
    totalWaypoints += wpCount;
    console.log(`  - ${t._id}  "${t.title}"  authorId=${t.authorId}  isShared=${t.isShared}  waypoints=${wpCount}`);
  });
  console.log(`[inspect] Total waypoints across all tours: ${totalWaypoints}`);

  const userCount = await mongoose.connection.db.collection("users").countDocuments();
  console.log(`[inspect] Total users (untouched collection, for reference): ${userCount}`);

  await mongoose.disconnect();
}

main().catch((err) => {
  console.error("[inspect] FAILED:", err);
  process.exit(1);
});
