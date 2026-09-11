/**
 * Manual smoke test for waypointVisibility + the unlock endpoints' underlying logic, against the
 * live Đắk Lắk seed data. Read/write to WaypointUnlock only (cleans up after itself) - does not
 * touch tours.
 * Usage: node scripts/testWaypointVisibility.js
 */
const path = require("path");
require("dotenv").config({ path: path.resolve(__dirname, "../.env") });
const mongoose = require("mongoose");
const Tour = require("../src/models/Tour");
const WaypointUnlock = require("../src/models/WaypointUnlock");
const { redactTourWaypoints, getUnlockedIndexSet } = require("../src/services/waypointVisibility");

async function main() {
  await mongoose.connect(process.env.MONGODB_URI);
  const tour = await Tour.findOne({ isShared: true, "waypoints.price": { $gt: 0 } }).lean();
  if (!tour) throw new Error("No shared tour with a locked waypoint found - did the seed run?");

  const lockedIdx = tour.waypoints.findIndex((w) => w.price > 0);
  console.log(`Tour: "${tour.title}" (${tour._id}), locked waypoint index ${lockedIdx}: "${tour.waypoints[lockedIdx].locationName}"`);

  // 1. No deviceId at all -> fail closed, must be redacted.
  const redactedNoDevice = redactTourWaypoints(tour.waypoints, tour._id, undefined);
  console.log("\n[1] No device (undefined unlockedIndexSet):");
  console.log("    name:", redactedNoDevice[lockedIdx].locationName);
  console.log("    coord:", redactedNoDevice[lockedIdx].coordinate.coordinates, " (real:", tour.waypoints[lockedIdx].coordinate.coordinates, ")");
  console.log("    photos:", redactedNoDevice[lockedIdx].photos);

  // 2. Unknown device (never unlocked) -> still redacted.
  const fakeDeviceId = "test-device-never-unlocked";
  const unlockedSet1 = await getUnlockedIndexSet(fakeDeviceId, tour._id);
  const redacted1 = redactTourWaypoints(tour.waypoints, tour._id, unlockedSet1);
  console.log("\n[2] Unknown device, unlockedSet =", [...unlockedSet1], "-> name:", redacted1[lockedIdx].locationName);

  // 3. Determinism check - fuzzed coordinate must be IDENTICAL across two independent calls.
  const redactedAgain = redactTourWaypoints(tour.waypoints, tour._id, undefined);
  const same = JSON.stringify(redactedNoDevice[lockedIdx].coordinate) === JSON.stringify(redactedAgain[lockedIdx].coordinate);
  console.log("\n[3] Fuzzed coordinate stable across repeated calls:", same ? "YES" : "NO (BUG)");

  // 4. Simulate unlocking via the same upsert unlockWaypoint uses, then verify redaction lifts.
  const realDeviceId = "test-device-unlocking-now";
  await WaypointUnlock.updateOne(
    { deviceId: realDeviceId, tourId: tour._id, waypointIndex: lockedIdx },
    { $setOnInsert: { deviceId: realDeviceId, tourId: tour._id, waypointIndex: lockedIdx } },
    { upsert: true }
  );
  const unlockedSet2 = await getUnlockedIndexSet(realDeviceId, tour._id);
  const redacted2 = redactTourWaypoints(tour.waypoints, tour._id, unlockedSet2);
  console.log("\n[4] After unlocking as", realDeviceId, "-> name:", redacted2[lockedIdx].locationName,
    "  (real name is \"" + tour.waypoints[lockedIdx].locationName + "\")");
  console.log("    match real:", redacted2[lockedIdx].locationName === tour.waypoints[lockedIdx].locationName ? "YES" : "NO (BUG)");

  // cleanup test record
  await WaypointUnlock.deleteMany({ deviceId: { $in: [fakeDeviceId, realDeviceId] } });
  console.log("\n[cleanup] Removed test WaypointUnlock records.");

  await mongoose.disconnect();
}

main().catch((err) => {
  console.error("FAILED:", err);
  process.exit(1);
});
