const assert = require("node:assert/strict");
const path = require("node:path");
const fs = require("node:fs");
const { before, after, test } = require("node:test");

const cacheDir = path.resolve(__dirname, "../.cache/mongodb-binaries");
fs.mkdirSync(cacheDir, { recursive: true });
process.env.MONGOMS_DOWNLOAD_DIR = cacheDir;
process.env.PORT = "0";
process.env.NODE_ENV = "test";

const { MongoMemoryServer } = require("mongodb-memory-server");
const mongoose = require("mongoose");

let mongoServer;
let httpServer;
let baseUrl;

before(async () => {
  mongoServer = await MongoMemoryServer.create({ binary: { version: "8.2.6" } });
  process.env.MONGODB_URI = mongoServer.getUri("tour-moment-test");

  const { startServer } = require("../src/server");
  httpServer = await startServer();
  if (!httpServer.listening) {
    await new Promise((resolve) => httpServer.once("listening", resolve));
  }
  baseUrl = `http://127.0.0.1:${httpServer.address().port}`;
});

after(async () => {
  if (httpServer) await new Promise((resolve) => httpServer.close(resolve));
  await mongoose.disconnect();
  if (mongoServer) await mongoServer.stop();
});

test("health reports the local database connection", async () => {
  const response = await fetch(`${baseUrl}/health`);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { status: "ok", database: "connected" });
});

test("upload rejects unsupported and spoofed image files", async () => {
  const unsupportedForm = new FormData();
  unsupportedForm.append("image", new Blob(["plain text"], { type: "text/plain" }), "note.txt");
  const unsupportedResponse = await fetch(`${baseUrl}/api/tours/upload`, {
    method: "POST",
    body: unsupportedForm
  });
  assert.equal(unsupportedResponse.status, 400);

  const spoofedForm = new FormData();
  spoofedForm.append("image", new Blob(["not really jpeg"], { type: "image/jpeg" }), "fake.jpg");
  const spoofedResponse = await fetch(`${baseUrl}/api/tours/upload`, {
    method: "POST",
    body: spoofedForm
  });
  assert.equal(spoofedResponse.status, 400);
});

test("chat does not trust a client-supplied identity", async () => {
  const response = await fetch(`${baseUrl}/api/chat`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      message: "Suggest a tour",
      googleId: "spoofed-user",
      idToken: "not-a-firebase-token",
      history: Array.from({ length: 5 }, (_, index) => ({
        role: "user",
        content: `Previous question ${index}`
      }))
    })
  });
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.capped, true);
});

test("tour API rejects invalid waypoint data", async () => {
  const response = await fetch(`${baseUrl}/api/tours`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      authorId: "test-author",
      title: "Invalid tour",
      startDate: "2099-01-01T00:00:00.000Z",
      waypoints: [{
        locationName: "Invalid stop",
        price: -1,
        coordinate: { type: "Point", coordinates: [999, 999] }
      }]
    })
  });
  assert.equal(response.status, 400);
});

test("tour API creates, redacts and unlocks paid waypoints", async () => {
  const User = require("../src/models/User");
  await User.create({
    _id: "test-author",
    googleId: "test-author",
    email: "author@example.test",
    displayName: "Test Author"
  });

  const createResponse = await fetch(`${baseUrl}/api/tours`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      authorId: "test-author",
      title: "API integration tour",
      description: "Created by node:test",
      startDate: "2099-01-01T00:00:00.000Z",
      waypoints: [
        {
          locationName: "Free stop - Hà Nội",
          price: 0,
          coordinate: { type: "Point", coordinates: [105.8342, 21.0278] }
        },
        {
          locationName: "Secret stop - Hà Nội",
          price: 15000,
          coordinate: { type: "Point", coordinates: [105.85, 21.03] }
        }
      ]
    })
  });
  assert.equal(createResponse.status, 201);
  const created = await createResponse.json();

  const shareResponse = await fetch(`${baseUrl}/api/tours/${created._id}/share`, { method: "PATCH" });
  assert.equal(shareResponse.status, 200);

  const beforeUnlock = await fetch(`${baseUrl}/api/tours/${created._id}?deviceId=test-device`);
  const redactedTour = await beforeUnlock.json();
  assert.equal(redactedTour.waypoints[0].locked, false);
  assert.equal(redactedTour.waypoints[1].locked, true);
  assert.equal(redactedTour.authorId, undefined);
  assert.equal(redactedTour.author._id, undefined);
  assert.equal(redactedTour.author.googleId, undefined);

  const spoofedOwnerResponse = await fetch(
    `${baseUrl}/api/tours/${created._id}?deviceId=other-device&userId=test-author`
  );
  const spoofedOwnerTour = await spoofedOwnerResponse.json();
  assert.equal(spoofedOwnerTour.waypoints[1].locked, true);

  const lockedCopyResponse = await fetch(`${baseUrl}/api/tours/copy/${created._id}`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ userId: "copy-recipient", deviceId: "test-device" })
  });
  assert.equal(lockedCopyResponse.status, 403);
  assert.notEqual(redactedTour.waypoints[1].locationName, "Secret stop - Hà Nội");

  const unlockResponse = await fetch(`${baseUrl}/api/tours/${created._id}/waypoints/1/unlock`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ deviceId: "test-device" })
  });
  assert.equal(unlockResponse.status, 200);

  const afterUnlock = await fetch(`${baseUrl}/api/tours/${created._id}?deviceId=test-device`);
  const unlockedTour = await afterUnlock.json();
  assert.equal(unlockedTour.waypoints[1].locked, false);
  const copyResponse = await fetch(`${baseUrl}/api/tours/copy/${created._id}`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ userId: "copy-recipient", deviceId: "test-device" })
  });
  assert.equal(copyResponse.status, 201);
  const copiedTour = await copyResponse.json();
  assert.equal(copiedTour.waypoints[1].locationName, unlockedTour.waypoints[1].locationName);

  const invalidIdResponse = await fetch(`${baseUrl}/api/tours/not-an-object-id`);
  assert.equal(invalidIdResponse.status, 400);
  assert.equal(unlockedTour.waypoints[1].locationName, "Secret stop - Hà Nội");
});
