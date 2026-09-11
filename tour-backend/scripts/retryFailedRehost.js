/**
 * Retry pass for rehostTourImages.js: finds any of the 12 original tours' cover/waypoint photos
 * still pointing at commons.wikimedia.org (i.e. the ones that failed to re-host on the first pass
 * - Wikimedia throttled/slowed down partway through that run after the volume of requests during
 * this whole investigation) and retries them one at a time with a deliberate delay in between, to
 * avoid re-triggering the same throttling.
 *
 * Usage: node scripts/retryFailedRehost.js           (dry run)
 *        node scripts/retryFailedRehost.js --apply   (commit)
 */
const path = require("path");
require("dotenv").config({ path: path.resolve(__dirname, "../.env") });
const mongoose = require("mongoose");
const fs = require("fs");
const os = require("os");
const { execFileSync } = require("child_process");
const Tour = require("../src/models/Tour");

const BUCKET = "tour-momen-tour-photos";
const PUBLIC_BASE = `https://storage.googleapis.com/${BUCKET}`;
const APPLY = process.argv.includes("--apply");
const DELAY_MS = 4000; // deliberately slow - one request every 4s, to stay well under whatever
                        // rate Wikimedia was throttling at during the first pass

const ORIGINAL_TOUR_IDS = [
  "6aa21beb6da477d78d2afe28", "6aa21beb6da477d78d2afe30", "6aa21beb6da477d78d2afe37",
  "6aa21beb6da477d78d2afe3e", "6aa21beb6da477d78d2afe43", "6aa21beb6da477d78d2afe48",
  "6aa21beb6da477d78d2afe4d", "6aa21beb6da477d78d2afe52", "6aa21beb6da477d78d2afe57",
  "6aa21beb6da477d78d2afe5c", "6aa21beb6da477d78d2afe61", "6aa21beb6da477d78d2afe66",
];

function sleep(ms) { return new Promise((r) => setTimeout(r, ms)); }

function downloadToFile(url, destPath) {
  execFileSync("curl", ["-sL", "--fail", "--max-time", "60", "-o", destPath, url], { stdio: ["ignore", "ignore", "inherit"] });
}

function extFromUrl(url) {
  const clean = url.split("?")[0];
  const match = clean.match(/\.([a-zA-Z0-9]+)$/);
  return match ? match[1].toLowerCase() : "jpg";
}

function slugify(s) {
  return s.normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/đ/gi, "d")
    .replace(/[^a-zA-Z0-9]+/g, "-").replace(/^-+|-+$/g, "").toLowerCase().slice(0, 60);
}

const urlCache = new Map();
let counter = 9000; // high offset so filenames never collide with the first pass's 0-62 range

async function rehost(sourceUrl, label) {
  if (urlCache.has(sourceUrl)) return urlCache.get(sourceUrl);

  const ext = extFromUrl(sourceUrl);
  const objectName = `${slugify(label)}-retry-${counter}.${ext}`;
  const localPath = path.join(os.tmpdir(), `retry-${counter}.${ext}`);
  counter++;

  console.log(`  downloading: ${sourceUrl}`);
  downloadToFile(sourceUrl, localPath);

  const publicUrl = `${PUBLIC_BASE}/${objectName}`;
  if (APPLY) {
    execFileSync("gcloud", ["storage", "cp", localPath, `gs://${BUCKET}/${objectName}`, "--quiet"], { stdio: "inherit", shell: true });
  }
  fs.unlinkSync(localPath);

  urlCache.set(sourceUrl, publicUrl);
  return publicUrl;
}

async function main() {
  await mongoose.connect(process.env.MONGODB_URI);
  console.log(APPLY ? "APPLY MODE" : "DRY RUN (pass --apply to commit)");

  let attempted = 0, succeeded = 0;
  const stillFailing = [];

  for (const tourId of ORIGINAL_TOUR_IDS) {
    const tour = await Tour.findById(tourId);
    if (!tour) continue;
    let changed = false;

    if (tour.imageUrl && tour.imageUrl.includes("commons.wikimedia.org")) {
      attempted++;
      await sleep(DELAY_MS);
      try {
        const newUrl = await rehost(tour.imageUrl, `${tour.title} cover`);
        console.log(`[OK] ${tour.title} cover -> ${newUrl}`);
        if (APPLY) { tour.imageUrl = newUrl; changed = true; }
        succeeded++;
      } catch (err) {
        console.log(`[STILL FAILING] ${tour.title} cover: ${err.message.split("\n")[0]}`);
        stillFailing.push(`${tour.title} cover`);
      }
    }

    for (const wp of tour.waypoints) {
      if (!wp.photos || !wp.photos[0] || !wp.photos[0].includes("commons.wikimedia.org")) continue;
      attempted++;
      await sleep(DELAY_MS);
      try {
        const newUrl = await rehost(wp.photos[0], wp.locationName);
        console.log(`[OK] ${wp.locationName} -> ${newUrl}`);
        if (APPLY) { wp.photos[0] = newUrl; changed = true; }
        succeeded++;
      } catch (err) {
        console.log(`[STILL FAILING] ${wp.locationName}: ${err.message.split("\n")[0]}`);
        stillFailing.push(`[${tour.title}] ${wp.locationName}`);
      }
    }

    if (APPLY && changed) await tour.save();
  }

  console.log(`\nAttempted: ${attempted}, succeeded: ${succeeded}`);
  if (stillFailing.length) {
    console.log("Still failing (old URL left in place):");
    stillFailing.forEach((f) => console.log(`  - ${f}`));
  }

  await mongoose.disconnect();
}

main().catch((err) => { console.error(err); process.exit(1); });
