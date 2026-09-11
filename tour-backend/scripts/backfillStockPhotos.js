/**
 * Final pass over the 12 original tours: anything still not a real photo on our own GCS bucket
 * gets backfilled. Two cases, handled differently:
 *   - Still on the original commons.wikimedia.org URL: this IS a real, correctly-matched photo
 *     that just hasn't made it through Wikimedia's throttling yet - one more direct, verified
 *     (>20KB) attempt is made to recover the REAL photo before giving up on it.
 *   - Still a placehold.co placeholder (the 5 genuinely-unmatched waypoints from the original
 *     migration): no real source exists to retry, so these go straight to a Pexels stock photo.
 * Either way nothing stays on a colored placeholder box or a broken/blocked hotlink by the end.
 *
 * Same pipeline as the GCS migration: fetch once (from Wikimedia or Pexels), resize to max 1600px
 * + JPEG q82 (Jimp), upload to our bucket, store that permanent URL - the Android app never talks
 * to Pexels directly, same reasoning as not hotlinking Wikimedia.
 *
 * Usage: node scripts/backfillStockPhotos.js           (dry run - reports the plan)
 *        node scripts/backfillStockPhotos.js --apply   (commit)
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

if (!PEXELS_KEY) {
  console.error("PEXELS_API_KEY is not set (check .env)");
  process.exit(1);
}

const IDS = [
  "6aa21beb6da477d78d2afe28", "6aa21beb6da477d78d2afe30", "6aa21beb6da477d78d2afe37",
  "6aa21beb6da477d78d2afe3e", "6aa21beb6da477d78d2afe43", "6aa21beb6da477d78d2afe48",
  "6aa21beb6da477d78d2afe4d", "6aa21beb6da477d78d2afe52", "6aa21beb6da477d78d2afe57",
  "6aa21beb6da477d78d2afe5c", "6aa21beb6da477d78d2afe61", "6aa21beb6da477d78d2afe66",
];

const QUERIES = [
  "green mountains nature", "waterfall forest", "tropical rainforest", "misty mountains landscape",
  "river valley nature", "lush green hills", "forest sunlight", "mountain lake nature",
];
let queryIndex = 0;

function isBroken(url) {
  return !url || url.includes("placehold.co") || url.includes("commons.wikimedia.org") || !url.includes("storage.googleapis.com");
}

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
            reject(new Error(`Pexels response parse failed: ${data.slice(0, 200)}`));
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

let counter = 30000;

async function fetchProcessUpload(sourceUrl, label, tag) {
  const image = await Jimp.read(sourceUrl);
  if (image.bitmap.width > MAX_DIMENSION || image.bitmap.height > MAX_DIMENSION) {
    image.scaleToFit({ w: MAX_DIMENSION, h: MAX_DIMENSION });
  }
  const buffer = await image.getBuffer("image/jpeg", { quality: JPEG_QUALITY });

  const objectName = `${slugify(label)}-${tag}-${counter++}.jpg`;
  const publicUrl = `${PUBLIC_BASE}/${objectName}`;

  if (APPLY) {
    const localOut = path.join(os.tmpdir(), `backfill-${Date.now()}-${Math.random().toString(36).slice(2)}.jpg`);
    fs.writeFileSync(localOut, buffer);
    execFileSync("gcloud", ["storage", "cp", localOut, `gs://${BUCKET}/${objectName}`, "--quiet"], { stdio: "inherit", shell: true });
    fs.unlinkSync(localOut);
  }

  return { url: publicUrl, bytes: buffer.length };
}

/** A commons.wikimedia.org URL still on file at this point is a REAL, correctly-matched photo
 *  that just hasn't made it through Wikimedia's throttling yet - worth one more direct, verified
 *  attempt before replacing it with a generic stand-in. Returns null (not a thrown error) on
 *  failure so the caller falls through to the Pexels path. */
async function tryRealPhotoFirst(url, label) {
  if (!url.includes("commons.wikimedia.org")) return null;
  try {
    const { url: gcsUrl, bytes } = await fetchProcessUpload(url, label, "recovered");
    if (bytes < 20000) throw new Error(`downloaded file too small (${bytes} bytes) - likely still blocked`);
    return { url: gcsUrl, bytes };
  } catch (err) {
    console.log(`  (real photo retry failed for "${label}": ${err.message.split("\n")[0]} - falling back to stock)`);
    return null;
  }
}

async function backfillWithStock(label) {
  const query = QUERIES[queryIndex++ % QUERIES.length];
  const sourceUrl = await pexelsSearch(query);
  const { url, bytes } = await fetchProcessUpload(sourceUrl, label, "stock");
  return { url, bytes, query };
}

async function main() {
  await mongoose.connect(process.env.MONGODB_URI);
  console.log(APPLY ? "APPLY MODE" : "DRY RUN (pass --apply to commit)");

  let alreadyFine = 0, recovered = 0, stockBackfilled = 0, failed = 0;
  const examples = [];

  async function handleItem(tour, label, getUrl, setUrl) {
    const current = getUrl();
    if (!isBroken(current)) { alreadyFine++; return false; }

    const real = await tryRealPhotoFirst(current, label);
    if (real) {
      console.log(`[REAL PHOTO RECOVERED] ${tour.title} | ${label} -> ${real.url} (${(real.bytes/1024).toFixed(0)}KB)`);
      if (APPLY) setUrl(real.url);
      recovered++;
      return true;
    }

    try {
      const { url, bytes, query } = await backfillWithStock(label);
      console.log(`[STOCK BACKFILL] ${tour.title} | ${label} ("${query}") -> ${url} (${(bytes/1024).toFixed(0)}KB)`);
      if (APPLY) setUrl(url);
      stockBackfilled++;
      if (examples.length < 4) examples.push({ tour: tour.title, item: label, query, url });
      return true;
    } catch (err) {
      console.log(`[FAILED] ${tour.title} | ${label}: ${err.message}`);
      failed++;
      return false;
    }
  }

  for (const id of IDS) {
    const t = await Tour.findById(id);
    let changed = false;

    await handleItem(t, "cover", () => t.imageUrl, (u) => { t.imageUrl = u; changed = true; });

    for (const wp of t.waypoints) {
      await handleItem(t, wp.locationName, () => wp.photos && wp.photos[0], (u) => { wp.photos = [u]; changed = true; });
    }

    if (APPLY && changed) await t.save();
  }

  console.log(`\n=== SUMMARY ===`);
  console.log(`Already fine (real photo, no action needed): ${alreadyFine}`);
  console.log(`Real photo recovered (Wikimedia retry succeeded): ${recovered}`);
  console.log(`Backfilled with a Pexels stock photo: ${stockBackfilled}`);
  console.log(`Failed (left as previous state): ${failed}`);
  console.log(`\nExample stock backfills:`, JSON.stringify(examples, null, 2));

  await mongoose.disconnect();
}

main().catch((err) => { console.error(err); process.exit(1); });
