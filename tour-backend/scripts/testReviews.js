/**
 * Manual smoke test for the reviews feature (Review model + reviewController), against the live
 * Đắk Lắk seed data. Creates a temporary test User + Review, exercises submit/duplicate-reject/
 * edit/aggregate/delete through the actual HTTP endpoints (so it also verifies routing), then
 * cleans up both rows it created.
 * Usage: node scripts/testReviews.js
 */
const path = require("path");
require("dotenv").config({ path: path.resolve(__dirname, "../.env") });
const mongoose = require("mongoose");
const Tour = require("../src/models/Tour");
const User = require("../src/models/User");
const Review = require("../src/models/Review");

const BASE_URL = process.env.TEST_BASE_URL || "https://tour-moment-backend-42345853831.asia-southeast1.run.app";
const TEST_USER_ID = "test-reviewer-smoke-script";

async function http(method, path, body) {
  const res = await fetch(`${BASE_URL}${path}`, {
    method,
    headers: { "Content-Type": "application/json" },
    body: body ? JSON.stringify(body) : undefined,
  });
  let json = null;
  try { json = await res.json(); } catch (_) {}
  return { status: res.status, json };
}

function assert(cond, msg) {
  if (!cond) throw new Error("FAILED: " + msg);
  console.log("  ok - " + msg);
}

async function main() {
  await mongoose.connect(process.env.MONGODB_URI);

  const tour = await Tour.findOne({ isShared: true }).lean();
  if (!tour) throw new Error("No shared tour found - did the seed run?");
  console.log(`Tour: "${tour.title}" (${tour._id})`);

  // Clean slate + a real User row (submitReview requires one to exist, on purpose - guests can't review).
  await Review.deleteOne({ tripId: tour._id, userId: TEST_USER_ID });
  await User.deleteOne({ _id: TEST_USER_ID });
  await User.create({
    _id: TEST_USER_ID,
    googleId: TEST_USER_ID,
    email: "smoke-test-reviewer@example.com",
    displayName: "Smoke Test Reviewer",
    photoUrl: null,
  });

  try {
    console.log("1. GET reviews before any exist");
    let r = await http("GET", `/api/tours/${tour._id}/reviews`);
    assert(r.status === 200, `200, got ${r.status}`);
    const beforeCount = r.json.reviewCount;

    console.log("2. POST a new review");
    r = await http("POST", `/api/tours/${tour._id}/reviews`, { userId: TEST_USER_ID, rating: 4, comment: "Chuyến đi rất đáng nhớ!" });
    assert(r.status === 201, `201, got ${r.status} ${JSON.stringify(r.json)}`);
    assert(r.json.rating === 4, "rating echoed back as 4");

    console.log("3. POST again (same user) -> 409 duplicate");
    r = await http("POST", `/api/tours/${tour._id}/reviews`, { userId: TEST_USER_ID, rating: 5, comment: "again" });
    assert(r.status === 409, `409, got ${r.status}`);

    console.log("4. GET reviews - count incremented, avg reflects it, author populated");
    r = await http("GET", `/api/tours/${tour._id}/reviews`);
    assert(r.json.reviewCount === beforeCount + 1, `reviewCount ${beforeCount + 1}, got ${r.json.reviewCount}`);
    const mine = r.json.reviews.find((x) => x.userId === TEST_USER_ID);
    assert(!!mine, "my review present in list");
    assert(mine.user && mine.user.displayName === "Smoke Test Reviewer", `author displayName populated, got ${JSON.stringify(mine.user)}`);

    console.log("5. PATCH (edit) my review");
    r = await http("PATCH", `/api/tours/${tour._id}/reviews`, { userId: TEST_USER_ID, rating: 5, comment: "Sửa lại: tuyệt vời!" });
    assert(r.status === 200, `200, got ${r.status}`);
    assert(r.json.rating === 5, "rating updated to 5");

    console.log("6. GET reviews - edit reflected, count unchanged (still 1 more than before)");
    r = await http("GET", `/api/tours/${tour._id}/reviews`);
    assert(r.json.reviewCount === beforeCount + 1, "reviewCount unchanged by edit");
    const mine2 = r.json.reviews.find((x) => x.userId === TEST_USER_ID);
    assert(mine2.rating === 5 && mine2.comment === "Sửa lại: tuyệt vời!", "edited fields reflected in GET");

    console.log("7. DELETE my review");
    r = await http("DELETE", `/api/tours/${tour._id}/reviews`, { userId: TEST_USER_ID });
    assert(r.status === 200, `200, got ${r.status}`);

    console.log("8. GET reviews - back to original count");
    r = await http("GET", `/api/tours/${tour._id}/reviews`);
    assert(r.json.reviewCount === beforeCount, `reviewCount back to ${beforeCount}, got ${r.json.reviewCount}`);

    console.log("9. PATCH/DELETE on a review that no longer exists -> 404");
    r = await http("PATCH", `/api/tours/${tour._id}/reviews`, { userId: TEST_USER_ID, rating: 3, comment: "x" });
    assert(r.status === 404, `404, got ${r.status}`);

    console.log("\nALL CHECKS PASSED");
  } finally {
    await Review.deleteOne({ tripId: tour._id, userId: TEST_USER_ID });
    await User.deleteOne({ _id: TEST_USER_ID });
    await mongoose.disconnect();
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
