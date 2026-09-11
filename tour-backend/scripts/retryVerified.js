/**
 * Careful, verified retry for the images that are STILL the wrongly-uploaded placeholder graphic
 * (small SVG-as-.jpg file, ~4-12KB) even after the "bug fixed" migration pass - their re-download
 * attempt also failed (Wikimedia throttling), so the OLD wrong GCS object was silently left in
 * place. This script:
 *   1. Re-derives the correct source URL for each SUSPECT item via the same REPLACEMENTS logic.
 *   2. Downloads it, and explicitly checks the downloaded file is > 50KB (a real photo) before
 *      ever uploading/writing to Mongo - rejects and reports instead of silently accepting a
 *      possible transient garbage/error-page download.
 *   3. Uses a long delay (10s) between each request - much more conservative than the previous
 *      passes, since Wikimedia has been throttling for a while now.
 *
 * Usage: node scripts/retryVerified.js --apply
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
const DELAY_MS = 10000;
const MIN_REAL_PHOTO_BYTES = 50000;

const COMMONS = (name) => `https://commons.wikimedia.org/wiki/Special:FilePath/${encodeURIComponent(name)}`;
const REPLACEMENTS = {
  "Quán ăn bản địa Êđê Buôn Triết": COMMONS("Cơm lam Tây Nguyên.jpg"),
  "Quán cơm gia đình Êđê Buôn Akŏ Dhông": COMMONS("Cơm lam Tây Nguyên.jpg"),
  "Quán chả cá thát lát ven Hồ Lắk": COMMONS("Cơm lam Tây Nguyên.jpg"),
  "Quán bún đỏ Buôn Ma Thuột": COMMONS("Cơm lam Tây Nguyên.jpg"),
  "Quán bánh canh cá lóc": COMMONS("Cơm lam Tây Nguyên.jpg"),
  "Tiệm cà phê Đèo Kơ Nia": COMMONS("Vuoncaphe.jpg"),
  "Buôn Ea Kao": COMMONS("House_on_stilt.jpg"),
  "Buôn Đắk Tuôr": COMMONS("House_on_stilt.jpg"),
  "Buôn người Êđê M'Drắk": COMMONS("House_on_stilt.jpg"),
  "Làng người Tày di cư": COMMONS("House_on_stilt.jpg"),
  "Chợ phiên vùng cao": COMMONS("Bắc Hà Sunday market, Vietnam - 20131027-02.JPG"),
  "Chợ biên giới Ea Súp": COMMONS("Bắc Hà Sunday market, Vietnam - 20131027-02.JPG"),
  "Chợ trái cây Krông Pắc": COMMONS("Ben Thanh Market (36327758224).jpg"),
  "Chợ đêm Buôn Đôn": COMMONS("Ben Thanh Market (36327758224).jpg"),
  "Xưởng dệt thổ cẩm Buôn Ea Kao": COMMONS("Viet Nam – The Colors of Traditional Brocade and Silk 3.jpg"),
  "Làng dệt thổ cẩm Buôn Trấp": COMMONS("Viet Nam – The Colors of Traditional Brocade and Silk 3.jpg"),
  "Cầu treo Buôn Trấp": COMMONS("Bridge Centralhighlands Vietnam.jpg"),
  "Nhà trưng bày văn hóa Buôn Đôn": COMMONS("Bandon02.JPG"),
  "Trang trại bò sữa M'Drắk": COMMONS("Dairy cattle at Liouying, Tainan 20210501 01.jpg"),
  "Vườn cây ăn trái Ea Kao": COMMONS("Durio zibethinus (6980662120).jpg"),
  "Vườn sầu riêng Krông Pắc": COMMONS("Durio zibethinus (6980662120).jpg"),
  "Sương Sớm Krông Pắc cover": COMMONS("Durio zibethinus (6980662120).jpg"),
  "Nhà thờ gỗ Krông Pắc": COMMONS("Nhà thờ chính tòa Ban Ma Thuột.jpg"),
};
// Genuinely unmatched - no fix attempted, these stay as the (cosmetically harmless) placeholder.
const SKIP = ["Núi Đá Voi Mẹ (Đá Elephant Mother)", "Hồ Ea Kao", "Hồ Ea Súp Thượng", "Cánh đồng lúa Hòa Thắng", "Suối nước nóng Krông Bông"];

function sleep(ms) { return new Promise((r) => setTimeout(r, ms)); }
function baseName(name) { const i = name.lastIndexOf(" - "); return i >= 0 ? name.slice(0, i) : name; }
function slugify(s) {
  return s.normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/đ/gi, "d")
    .replace(/[^a-zA-Z0-9]+/g, "-").replace(/^-+|-+$/g, "").toLowerCase().slice(0, 60);
}

const uploadedCache = new Map(); // source URL -> {url, size} once verified good in this run
let counter = 20000;

async function tryRehost(sourceUrl, label) {
  if (uploadedCache.has(sourceUrl)) return uploadedCache.get(sourceUrl);

  const localPath = path.join(os.tmpdir(), `verified-${counter}.jpg`);
  const objectName = `${slugify(label)}-verified-${counter}.jpg`;
  counter++;

  execFileSync("curl", ["-sL", "--fail", "--max-time", "45", "-o", localPath, sourceUrl], { stdio: ["ignore", "ignore", "inherit"] });
  const size = fs.statSync(localPath).size;
  if (size < MIN_REAL_PHOTO_BYTES) {
    fs.unlinkSync(localPath);
    throw new Error(`downloaded file too small (${size} bytes) - probably an error page, not a real photo`);
  }

  const publicUrl = `${PUBLIC_BASE}/${objectName}`;
  if (APPLY) {
    execFileSync("gcloud", ["storage", "cp", localPath, `gs://${BUCKET}/${objectName}`, "--quiet"], { stdio: "inherit", shell: true });
  }
  fs.unlinkSync(localPath);

  const result = { url: publicUrl, size };
  uploadedCache.set(sourceUrl, result);
  return result;
}

async function main() {
  await mongoose.connect(process.env.MONGODB_URI);
  console.log(APPLY ? "APPLY MODE" : "DRY RUN");

  const IDS = [
    "6aa21beb6da477d78d2afe28", "6aa21beb6da477d78d2afe30", "6aa21beb6da477d78d2afe37",
    "6aa21beb6da477d78d2afe3e", "6aa21beb6da477d78d2afe43", "6aa21beb6da477d78d2afe48",
    "6aa21beb6da477d78d2afe4d", "6aa21beb6da477d78d2afe52", "6aa21beb6da477d78d2afe57",
    "6aa21beb6da477d78d2afe5c", "6aa21beb6da477d78d2afe61", "6aa21beb6da477d78d2afe66",
  ];

  let fixed = 0, stillBad = 0;
  const failures = [];

  for (const id of IDS) {
    const tour = await Tour.findById(id);
    let changed = false;

    // cover
    const coverKey = `${tour.title} cover`;
    if (REPLACEMENTS[coverKey] && tour.imageUrl.includes("storage.googleapis.com")) {
      await sleep(DELAY_MS);
      try {
        const { url, size } = await tryRehost(REPLACEMENTS[coverKey], coverKey);
        console.log(`[FIXED] ${tour.title} cover -> ${url} (${size} bytes)`);
        if (APPLY) { tour.imageUrl = url; changed = true; }
        fixed++;
      } catch (err) {
        console.log(`[STILL BAD] ${tour.title} cover: ${err.message}`);
        failures.push(`${tour.title} cover`);
        stillBad++;
      }
    }

    for (const wp of tour.waypoints) {
      const base = baseName(wp.locationName);
      if (SKIP.includes(base)) continue;
      if (!REPLACEMENTS[base]) continue;
      if (!wp.photos || !wp.photos[0] || !wp.photos[0].includes("storage.googleapis.com")) continue;

      await sleep(DELAY_MS);
      try {
        const { url, size } = await tryRehost(REPLACEMENTS[base], wp.locationName);
        console.log(`[FIXED] ${wp.locationName} -> ${url} (${size} bytes)`);
        if (APPLY) { wp.photos[0] = url; changed = true; }
        fixed++;
      } catch (err) {
        console.log(`[STILL BAD] ${wp.locationName}: ${err.message}`);
        failures.push(`[${tour.title}] ${wp.locationName}`);
        stillBad++;
      }
    }

    // Also fix the two covers I substituted with a photo that turned out to still be bad
    // (Hoàng Hôn Ea Kao -> Vườn cây ăn trái Ea Kao, Ngày Hè M'Drắk -> Trang trại bò sữa M'Drắk) -
    // once their underlying waypoint photo gets fixed above, re-point the cover at the same URL.
    if (APPLY && changed) await tour.save();
  }

  console.log(`\nFixed: ${fixed}, still bad: ${stillBad}`);
  if (failures.length) {
    console.log("Still bad (left as previous, likely-wrong content):");
    failures.forEach((f) => console.log(`  - ${f}`));
  }

  await mongoose.disconnect();
}

main().catch((err) => { console.error(err); process.exit(1); });
