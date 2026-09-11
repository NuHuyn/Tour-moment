const mongoose = require("mongoose");

/**
 * Server-side record of "this device paid to unlock this waypoint". Introduced so locked-waypoint
 * coordinates/names can actually be withheld server-side (see src/services/waypointVisibility.js) -
 * previously the whole paywall (WaypointLockManager on the Android side) was 100% local
 * SharedPreferences, so the server had no way to know who'd "paid" and always shipped full data.
 *
 * Identity is a client-generated deviceId (see DeviceIdProvider.java), not a googleId - this app's
 * existing unlock flow works for guests too (no login required), so entitlement has to work without
 * one. This is intentionally the same trust model the rest of the app already uses for
 * self-reported identity (see chatController.js's googleId handling) - a device could fabricate/
 * reset its id and lose its unlocks, but it can't forge someone else's id to read waypoints it
 * never paid for, which is the actual threat being closed off here (network-response inspection).
 */
const waypointUnlockSchema = new mongoose.Schema(
  {
    deviceId: { type: String, required: true },
    tourId: { type: mongoose.Schema.Types.ObjectId, ref: "Tour", required: true },
    waypointIndex: { type: Number, required: true },
  },
  { timestamps: true }
);

// One record per (device, tour, waypoint) - unlockWaypoint upserts against this so re-tapping
// "unlock" on an already-unlocked step is a harmless no-op instead of a duplicate row.
waypointUnlockSchema.index({ deviceId: 1, tourId: 1, waypointIndex: 1 }, { unique: true });

module.exports = mongoose.model("WaypointUnlock", waypointUnlockSchema);
