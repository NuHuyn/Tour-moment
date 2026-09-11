/**
 * One-time cleanup: every image re-hosted to gs://tour-momen-tour-photos so far is an
 * unprocessed original (many straight from Wikimedia Commons, which often serves multi-MB
 * originals) - measured average 1.6MB, several nearly 7MB, 24 of 59 over 1MB. That's a real,
 * concrete contributor to the "images pop in 2-3s late" symptom - a phone has to actually
 * download those bytes before Picasso can decode/show anything, regardless of caching.
 *
 * Downloads each object already in the bucket, resizes to max 1600px on the long edge (more than
 * enough for any view in this app - the hero carousel is the biggest, and it's still just a phone
 * screen) and re-encodes as JPEG quality 82, then overwrites the SAME object path - no DB/URL
 * changes needed, every existing tour.imageUrl / waypoint.photos[0] keeps working unchanged.
 *
 * Usage: node scripts/compressGcsImages.js           (dry run - reports sizes, changes nothing)
 *        node scripts/compressGcsImages.js --apply   (commit)
 */
const path = require("path");
require("dotenv").config({ path: path.resolve(__dirname, "../.env") });
const mongoose = require("mongoose");
const fs = require("fs");
const os = require("os");
const { execFileSync } = require("child_process");
const { Jimp } = require("jimp");
const Tour = require("../src/models/Tour");

const BUCKET = "tour-momen-tour-photos";
const APPLY = process.argv.includes("--apply");
const MAX_DIMENSION = 1600;
const JPEG_QUALITY = 82;
const SKIP_UNDER_BYTES = 400 * 1024; // already small enough, don't bother re-encoding (would just add JPEG generation-loss for no size win)

const IDS = [
  "6aa21beb6da477d78d2afe28", "6aa21beb6da477d78d2afe30", "6aa21beb6da477d78d2afe37",
  "6aa21beb6da477d78d2afe3e", "6aa21beb6da477d78d2afe43", "6aa21beb6da477d78d2afe48",
  "6aa21beb6da477d78d2afe4d", "6aa21beb6da477d78d2afe52", "6aa21beb6da477d78d2afe57",
  "6aa21beb6da477d78d2afe5c", "6aa21beb6da477d78d2afe61", "6aa21beb6da477d78d2afe66",
];

function objectNameFromUrl(url) {
  return decodeURIComponent(url.split(`/${BUCKET}/`)[1]);
}

/** Downloads+resizes+(optionally) re-uploads one GCS object, in place - cached by object name so
 *  a photo shared across multiple waypoints/tours is only ever processed once. Returns
 *  `fresh: false` on a cache hit so the caller doesn't double-count bytes/log duplicate lines. */
async function processOne(url, cache) {
  const objectName = objectNameFromUrl(url);
  if (cache.has(objectName)) return { ...cache.get(objectName), fresh: false };

  // Jimp.read() fetches http(s) URLs directly - no separate curl download step needed for the
  // source; content-length isn't known ahead of time so "before" size comes from a HEAD request.
  const headBytes = await new Promise((resolve) => {
    const https = require("https");
    const req = https.request(url, { method: "HEAD" }, (res) => resolve(parseInt(res.headers["content-length"] || "0", 10)));
    req.on("error", () => resolve(0));
    req.end();
  });

  if (headBytes > 0 && headBytes < SKIP_UNDER_BYTES) {
    const result = { objectName, before: headBytes, after: headBytes, skipped: true };
    cache.set(objectName, result);
    return { ...result, fresh: true };
  }

  const image = await Jimp.read(url);
  const beforeSize = headBytes; // best-effort; only used for the before/after report

  if (image.bitmap.width > MAX_DIMENSION || image.bitmap.height > MAX_DIMENSION) {
    // scaleToFit (not resize) - fits within a MAX_DIMENSION x MAX_DIMENSION box while preserving
    // aspect ratio (verified: a 2560x1920 source became 1600x1200, not stretched to 1600x1600).
    image.scaleToFit({ w: MAX_DIMENSION, h: MAX_DIMENSION });
  }
  const buffer = await image.getBuffer("image/jpeg", { quality: JPEG_QUALITY });
  const afterSize = buffer.length;

  if (APPLY) {
    const localOut = path.join(os.tmpdir(), `compress-out-${Date.now()}-${Math.random().toString(36).slice(2)}.jpg`);
    fs.writeFileSync(localOut, buffer);
    execFileSync("gcloud", ["storage", "cp", localOut, `gs://${BUCKET}/${objectName}`, "--quiet"], { stdio: "inherit", shell: true });
    fs.unlinkSync(localOut);
  }

  const result = { objectName, before: beforeSize, after: afterSize, skipped: false };
  cache.set(objectName, result);
  return { ...result, fresh: true };
}

async function main() {
  await mongoose.connect(process.env.MONGODB_URI);
  console.log(APPLY ? "APPLY MODE" : "DRY RUN (pass --apply to commit)");

  const cache = new Map();
  let totalBefore = 0, totalAfter = 0, processed = 0, skipped = 0;

  for (const id of IDS) {
    const t = await Tour.findById(id).lean();
    const urls = [];
    if (t.imageUrl && t.imageUrl.includes("storage.googleapis.com")) urls.push(t.imageUrl);
    for (const w of t.waypoints) {
      if (w.photos && w.photos[0] && w.photos[0].includes("storage.googleapis.com")) urls.push(w.photos[0]);
    }

    for (const url of urls) {
      try {
        const r = await processOne(url, cache);
        if (!r.fresh) continue; // already processed this exact object via another waypoint/tour
        if (r.skipped) { skipped++; continue; }
        totalBefore += r.before;
        totalAfter += r.after;
        processed++;
        console.log(`${t.title} | ${r.objectName}: ${(r.before/1024).toFixed(0)}KB -> ${(r.after/1024).toFixed(0)}KB`);
      } catch (err) {
        console.log(`FAILED: ${t.title} | ${url}: ${err.message.split("\n")[0]}`);
      }
    }
  }

  console.log(`\nProcessed: ${processed} (skipped, already small: ${skipped})`);
  console.log(`Total before: ${(totalBefore/1024/1024).toFixed(1)}MB, after: ${(totalAfter/1024/1024).toFixed(1)}MB`);
  await mongoose.disconnect();
}

main().catch((err) => { console.error(err); process.exit(1); });
