/**
 * Follow-up to backfillStockPhotos.js: that pass's isBroken() only checked the URL's domain, not
 * actual content - so the 5 waypoints still holding the wrongly-re-uploaded placehold.co graphic
 * (from the original REPLACEMENTS-matching bug, now living on OUR bucket at a few KB each) read as
 * "already fine" since their URL is a storage.googleapis.com one. Confirmed via direct HEAD checks
 * (3-11KB each, real photos in this bucket are consistently >100KB). These are exactly the 5
 * waypoints already known to have no findable real photo (Núi Đá Voi Mẹ, Hồ Ea Kao, Hồ Ea Súp
 * Thượng, Cánh đồng lúa Hòa Thắng, Suối nước nóng Krông Bông) - straight to Pexels, no Wikimedia
 * retry applicable (there's no real source URL on file for these to retry).
 *
 * Usage: node scripts/backfillStockPhotos2.js --apply
 */
const path = require("path");
require("dotenv").config({ path: path.resolve(__dirname, "../.env") });
const mongoose = require("mongoose");
const https = require("https");
const fs = require("fs");
const os = require("os");
const { execFileSync } = require("child_process");
const { Jimp } = require("jimp");
const Tour = require("../src/models/Tour");

const BUCKET = "tour-momen-tour-photos";
const PUBLIC_BASE = `https://storage.googleapis.com/${BUCKET}`;
const APPLY = process.argv.includes("--apply");
const MAX_DIMENSION = 1600;
const JPEG_QUALITY = 82;
const PEXELS_KEY = process.env.PEXELS_API_KEY;

const TARGETS = [
  { tourId: "6aa21beb6da477d78d2afe37", waypoint: "Núi Đá Voi Mẹ (Đá Elephant Mother)", query: "large rock formation mountain" },
  { tourId: "6aa21beb6da477d78d2afe57", waypoint: "Hồ Ea Kao", query: "calm lake nature" },
  { tourId: "6aa21beb6da477d78d2afe57", waypoint: "Cánh đồng lúa Hòa Thắng", query: "green rice field" },
  { tourId: "6aa21beb6da477d78d2afe5c", waypoint: "Hồ Ea Súp Thượng", query: "lake reflection trees" },
  { tourId: "6aa21beb6da477d78d2afe4d", waypoint: "Suối nước nóng Krông Bông", query: "natural spring stream forest" },
];

function pexelsSearch(query) {
  return new Promise((resolve, reject) => {
    https.get(
      `https://api.pexels.com/v1/search?query=${encodeURIComponent(query)}&per_page=1&orientation=landscape`,
      { headers: { Authorization: PEXELS_KEY } },
      (res) => {
        let data = "";
        res.on("data", (c) => (data += c));
        res.on("end", () => {
          try {
            const json = JSON.parse(data);
            if (!json.photos || !json.photos.length) return reject(new Error(`no Pexels results for "${query}"`));
            resolve(json.photos[0].src.large2x || json.photos[0].src.original);
          } catch (e) {
            reject(new Error(`Pexels parse failed: ${data.slice(0, 200)}`));
          }
        });
      }
    ).on("error", reject);
  });
}

function slugify(s) {
  return s.normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/đ/gi, "d")
    .replace(/[^a-zA-Z0-9]+/g, "-").replace(/^-+|-+$/g, "").toLowerCase().slice(0, 60);
}

let counter = 40000;

async function main() {
  await mongoose.connect(process.env.MONGODB_URI);
  console.log(APPLY ? "APPLY MODE" : "DRY RUN");

  for (const { tourId, waypoint, query } of TARGETS) {
    const t = await Tour.findById(tourId);
    const wp = t.waypoints.find((w) => w.locationName.startsWith(waypoint));
    if (!wp) { console.log(`NOT FOUND: ${waypoint} in ${t.title}`); continue; }

    const sourceUrl = await pexelsSearch(query);
    const image = await Jimp.read(sourceUrl);
    if (image.bitmap.width > MAX_DIMENSION || image.bitmap.height > MAX_DIMENSION) {
      image.scaleToFit({ w: MAX_DIMENSION, h: MAX_DIMENSION });
    }
    const buffer = await image.getBuffer("image/jpeg", { quality: JPEG_QUALITY });
    const objectName = `${slugify(wp.locationName)}-stock-${counter++}.jpg`;
    const publicUrl = `${PUBLIC_BASE}/${objectName}`;

    if (APPLY) {
      const localOut = path.join(os.tmpdir(), `stock2-${Date.now()}.jpg`);
      fs.writeFileSync(localOut, buffer);
      execFileSync("gcloud", ["storage", "cp", localOut, `gs://${BUCKET}/${objectName}`, "--quiet"], { stdio: "inherit", shell: true });
      fs.unlinkSync(localOut);
      wp.photos = [publicUrl];
      await t.save();
    }

    console.log(`[STOCK BACKFILL] ${t.title} | ${wp.locationName} ("${query}") -> ${publicUrl} (${(buffer.length/1024).toFixed(0)}KB)`);
  }

  await mongoose.disconnect();
}

main().catch((err) => { console.error(err); process.exit(1); });
