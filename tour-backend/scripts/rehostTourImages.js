/**
 * One-time migration: re-host every tour cover photo / waypoint photo for the 12 original
 * Đắk Lắk seed tours into our own GCS bucket (gs://tour-momen-tour-photos), so the Android app
 * never hotlinks Wikimedia Commons directly.
 *
 * WHY: Wikimedia's edge returns HTTP 403 to Android/OkHttp's TLS+HTTP2 client fingerprint
 * specifically - confirmed via direct testing: identical URL, identical User-Agent, identical
 * source IP; curl gets a normal 301 redirect, the Android app's Picasso/OkHttp client gets 403.
 * A custom User-Agent (already added in JourneyLogApp.java) was NOT enough to fix this - it's a
 * client-fingerprint-level block, not a UA-content one. Since server-side Node fetches aren't
 * subject to the same block, re-hosting once here permanently fixes it without depending on
 * Wikimedia's availability/blocking behavior at all going forward.
 *
 * Also replaces every placehold.co (colored text-box placeholder, not a real photo) URL with a
 * real or strongly-matching-generic photo sourced via Wikimedia Commons category search (see the
 * REPLACEMENTS map below) - each one verified to resolve before being added here.
 *
 * Same source URL is only downloaded+uploaded once even if reused across multiple waypoints/tours
 * (e.g. the Ede longhouse photo appears on ~5 different waypoints) - see urlCache.
 *
 * Scope: only the 12 original tours (ORIGINAL_TOUR_IDS below) - explicitly excludes the 3
 * duplicate/test tours created while testing the reviews/unlock features earlier.
 *
 * Usage: node scripts/rehostTourImages.js           (dry run - prints the plan, changes nothing)
 *        node scripts/rehostTourImages.js --apply   (actually downloads/uploads/writes to Mongo)
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

const ORIGINAL_TOUR_IDS = [
  "6aa21beb6da477d78d2afe28", "6aa21beb6da477d78d2afe30", "6aa21beb6da477d78d2afe37",
  "6aa21beb6da477d78d2afe3e", "6aa21beb6da477d78d2afe43", "6aa21beb6da477d78d2afe48",
  "6aa21beb6da477d78d2afe4d", "6aa21beb6da477d78d2afe52", "6aa21beb6da477d78d2afe57",
  "6aa21beb6da477d78d2afe5c", "6aa21beb6da477d78d2afe61", "6aa21beb6da477d78d2afe66",
];

// placehold.co URL -> real replacement, sourced from Wikimedia Commons (category search,
// verified 200 OK via curl before being listed here). Matched to the actual subject where a
// specific photo exists; falls back to a strongly-matching *category* photo otherwise (never the
// single reused rice-terrace placeholder) - each such fallback is commented with what it actually
// shows vs. the real subject, so it's easy to swap later if a better match turns up.
const COMMONS = (name) => `https://commons.wikimedia.org/wiki/Special:FilePath/${encodeURIComponent(name)}`;
const REPLACEMENTS = {
  // Food stalls/local eateries - no dedicated photos exist for these specific small businesses;
  // falls back to a real Central Highlands food dish photo already used elsewhere in this seed
  // data (Cơm lam - bamboo rice), which is at least genuinely Tây Nguyên food, though not the
  // exact dish named (bún đỏ / chả cá thát lát are different dishes) - flagged to the user.
  "Quán ăn bản địa Êđê Buôn Triết": COMMONS("Cơm lam Tây Nguyên.jpg"),
  "Quán cơm gia đình Êđê Buôn Akŏ Dhông": COMMONS("Cơm lam Tây Nguyên.jpg"),
  "Quán chả cá thát lát ven Hồ Lắk": COMMONS("Cơm lam Tây Nguyên.jpg"),
  "Quán bún đỏ Buôn Ma Thuột": COMMONS("Cơm lam Tây Nguyên.jpg"),
  "Quán bánh canh cá lóc": COMMONS("Cơm lam Tây Nguyên.jpg"),

  "Tiệm cà phê Đèo Kơ Nia": COMMONS("Vuoncaphe.jpg"), // reuse: real BMT coffee-garden photo

  // Villages / longhouse communities - reuse the real Ede stilt-longhouse photo already used for
  // Tour 1's wp0 (correct ethnicity for Ede buôn; used as a generic "Central Highlands stilt
  // village" stand-in for Buôn Đắk Tuôr, which has no dedicated photo).
  "Buôn Ea Kao": COMMONS("House_on_stilt.jpg"),
  "Buôn Đắk Tuôr": COMMONS("House_on_stilt.jpg"),
  "Buôn người Êđê M'Drắk": COMMONS("House_on_stilt.jpg"),
  // Different ethnicity (Tày, not Êđê) - flagged, no Tày-specific Đắk Lắk photo found; same stilt-
  // house category stands in.
  "Làng người Tày di cư": COMMONS("House_on_stilt.jpg"),

  "Chợ phiên vùng cao": COMMONS("Bắc Hà Sunday market, Vietnam - 20131027-02.JPG"), // real Vietnamese highland market (Bắc Hà, not Đắk Lắk specifically)
  "Chợ biên giới Ea Súp": COMMONS("Bắc Hà Sunday market, Vietnam - 20131027-02.JPG"),
  "Chợ trái cây Krông Pắc": COMMONS("Ben Thanh Market (36327758224).jpg"), // real VN market, not fruit-specific
  "Chợ đêm Buôn Đôn": COMMONS("Ben Thanh Market (36327758224).jpg"), // real VN market, not a night market photo

  "Xưởng dệt thổ cẩm Buôn Ea Kao": COMMONS("Viet Nam – The Colors of Traditional Brocade and Silk 3.jpg"),
  "Làng dệt thổ cẩm Buôn Trấp": COMMONS("Viet Nam – The Colors of Traditional Brocade and Silk 3.jpg"),

  "Cầu treo Buôn Trấp": COMMONS("Bridge Centralhighlands Vietnam.jpg"), // real Central Highlands suspension bridge

  "Nhà trưng bày văn hóa Buôn Đôn": COMMONS("Bandon02.JPG"), // reuse: real Buôn Đôn cultural-area photo already in this seed data

  "Trang trại bò sữa M'Drắk": COMMONS("Dairy cattle at Liouying, Tainan 20210501 01.jpg"), // generic Asian dairy farm, not M'Drắk-specific

  "Vườn cây ăn trái Ea Kao": COMMONS("Durio zibethinus (6980662120).jpg"), // durian tree, not a general orchard - imperfect but a real fruit tree
  "Vườn sầu riêng Krông Pắc": COMMONS("Durio zibethinus (6980662120).jpg"), // real durian tree/fruit photo
  "Vườn sầu riêng Krông Pắc (cover)": COMMONS("Durio zibethinus (6980662120).jpg"), // unused key (kept for reference) - see the real one below
  // Cover lookups are keyed by "<tour title> cover" (see coverLabel in main()), NOT the
  // placehold.co URL's embedded text param - this is the key that actually gets looked up.
  "Sương Sớm Krông Pắc cover": COMMONS("Durio zibethinus (6980662120).jpg"),

  "Nhà thờ gỗ Krông Pắc": COMMONS("Nhà thờ chính tòa Ban Ma Thuột.jpg"), // real Đắk Lắk wooden cathedral, but in Buôn Ma Thuột, not literally Krông Pắc - flagged
};

// Explicitly could NOT find a real or reasonably-matching photo for these within a scriptable
// search (Wikimedia's full-text search returns only unrelated scanned-book noise for hyper-local
// subjects like these, and no relevant Commons category exists) - left as their existing
// placehold.co placeholder, NOT force-fit to something misleading. Flagged for you to source
// manually (e.g. from Google Maps reviews/photos for the actual place).
const UNMATCHED = [
  "Núi Đá Voi Mẹ (Đá Elephant Mother)", // specific rock formation - no Commons photo found
  "Hồ Ea Kao", // specific lake - no Commons photo found; declined to use an unrelated famous lake (e.g. Hoàn Kiếm) as it would actively mislead
  "Hồ Ea Súp Thượng", // same
  "Cánh đồng lúa Hòa Thắng", // specific rice field - no Commons category/photo found
  "Suối nước nóng Krông Bông", // specific hot spring - no Commons photo found
];

// Shells out to curl rather than using Node's https module with a custom User-Agent: Wikimedia's
// media CDN (upload.wikimedia.org) 403s a custom/descriptive UA string outright (even a properly
// ASCII, policy-shaped one), but succeeds with curl's own bare default UA - and with a real
// browser UA - so the simplest reliable fix is to just let curl be curl instead of trying to
// reverse-engineer whatever pattern their WAF is matching on.
function downloadToFile(url, destPath, attempt = 1) {
  try {
    execFileSync("curl", ["-sL", "--fail", "--max-time", "60", "-o", destPath, url], { stdio: ["ignore", "ignore", "inherit"] });
  } catch (err) {
    // A couple of these came back genuinely slow (partial bytes within the old 30s cap, not
    // outright blocked) rather than failing outright - retry once with a longer window before
    // giving up on this one file.
    if (attempt < 2) {
      console.log(`  (retrying after slow/failed transfer: ${url})`);
      return downloadToFile(url, destPath, attempt + 1);
    }
    throw err;
  }
}

function extFromUrl(url) {
  const clean = url.split("?")[0];
  const match = clean.match(/\.([a-zA-Z0-9]+)$/);
  return match ? match[1].toLowerCase() : "jpg";
}

function slugify(s) {
  return s
    .normalize("NFD").replace(/[̀-ͯ]/g, "")
    .replace(/đ/gi, "d")
    .replace(/[^a-zA-Z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .toLowerCase()
    .slice(0, 60);
}

const urlCache = new Map(); // original source URL -> new GCS public URL (dedupes repeated photos)
let uploadCount = 0;
const FAILURES = []; // download/upload failures that didn't stop the batch - reported at the end

async function rehost(sourceUrl, labelForFilename) {
  if (urlCache.has(sourceUrl)) return urlCache.get(sourceUrl);

  const ext = extFromUrl(sourceUrl);
  const objectName = `${slugify(labelForFilename)}-${uploadCount}.${ext}`;
  const localPath = path.join(os.tmpdir(), `rehost-${uploadCount}.${ext}`);
  uploadCount++;

  console.log(`  downloading: ${sourceUrl}`);
  await downloadToFile(sourceUrl, localPath);

  const publicUrl = `${PUBLIC_BASE}/${objectName}`;
  if (APPLY) {
    // shell: true - on Windows, gcloud is a .cmd wrapper; execFileSync without a shell doesn't
    // resolve PATHEXT the way an interactive shell does and fails with ENOENT even though
    // `gcloud` works fine when typed directly into a terminal.
    execFileSync("gcloud", ["storage", "cp", localPath, `gs://${BUCKET}/${objectName}`, "--quiet"], { stdio: "inherit", shell: true });
    fs.unlinkSync(localPath);
  } else {
    console.log(`  [dry run] would upload -> gs://${BUCKET}/${objectName}`);
    fs.unlinkSync(localPath);
  }

  urlCache.set(sourceUrl, publicUrl);
  return publicUrl;
}

// locationName is always "<place> - <District>" - REPLACEMENTS/UNMATCHED are keyed on
// just <place> (the part before " - "), so strip the suffix before looking up. BUG FIXED:
// the first version of this function did an exact-string lookup against the FULL
// "<place> - <District>" name, which never matched any REPLACEMENTS/UNMATCHED key (all defined
// without the suffix) - every placehold.co gap silently fell through to the final
// "return currentUrl" branch and got the placehold.co placeholder GRAPHIC ITSELF re-hosted to
// GCS, not the intended real replacement photo.
function baseName(locationName) {
  const idx = locationName.lastIndexOf(" - ");
  return idx >= 0 ? locationName.slice(0, idx) : locationName;
}

async function resolveSourceUrl(currentUrl, locationName) {
  const base = baseName(locationName);
  if (REPLACEMENTS[base]) return REPLACEMENTS[base];
  if (UNMATCHED.includes(base)) return null; // leave untouched
  return currentUrl; // already a real Wikimedia URL - just needs re-hosting as-is
}

async function main() {
  await mongoose.connect(process.env.MONGODB_URI);
  console.log(APPLY ? "APPLY MODE - will write to GCS + MongoDB" : "DRY RUN - no changes will be made (pass --apply to commit)");

  const examples = [];

  for (const tourId of ORIGINAL_TOUR_IDS) {
    const tour = await Tour.findById(tourId);
    if (!tour) { console.warn(`Tour ${tourId} not found, skipping`); continue; }
    console.log(`\n=== ${tour.title} (${tourId}) ===`);

    // Cover image
    const coverLabel = `${tour.title} cover`;
    const newCoverSource = await resolveSourceUrl(tour.imageUrl, coverLabel);
    let newCoverUrl = tour.imageUrl;
    if (newCoverSource) {
      try {
        newCoverUrl = await rehost(newCoverSource, coverLabel);
        console.log(`  cover: ${tour.imageUrl} -> ${newCoverUrl}`);
      } catch (err) {
        console.log(`  cover: FAILED (${err.message.split("\n")[0]}), leaving old URL in place - ${tour.imageUrl}`);
        FAILURES.push({ tour: tour.title, item: "cover", url: newCoverSource });
      }
    } else {
      console.log(`  cover: UNMATCHED, left as-is (${tour.imageUrl})`);
    }

    // Waypoint photos (first photo only - that's the one actually rendered as a thumbnail;
    // additional photos beyond [0] are left untouched, none of the seed data has more than one
    // per waypoint today)
    for (const wp of tour.waypoints) {
      if (!wp.photos || wp.photos.length === 0) continue; // e.g. redacted/locked waypoints on copies - not in scope anyway (originals only)
      const oldUrl = wp.photos[0];
      const source = await resolveSourceUrl(oldUrl, wp.locationName);
      if (!source) {
        console.log(`  wp "${wp.locationName}": UNMATCHED, left as-is`);
        continue;
      }
      try {
        const newUrl = await rehost(source, wp.locationName);
        console.log(`  wp "${wp.locationName}": ${oldUrl} -> ${newUrl}`);
        if (APPLY) wp.photos[0] = newUrl;
      } catch (err) {
        console.log(`  wp "${wp.locationName}": FAILED (${err.message.split("\n")[0]}), leaving old URL in place`);
        FAILURES.push({ tour: tour.title, item: wp.locationName, url: source });
      }
    }

    if (APPLY) {
      tour.imageUrl = newCoverUrl;
      await tour.save();
    }

    if (examples.length < 3) {
      examples.push({ title: tour.title, imageUrl: newCoverUrl, waypoints: tour.waypoints.map(w => ({ name: w.locationName, photo: w.photos && w.photos[0] })) });
    }
  }

  console.log("\n\n=== SAMPLE RESULT (first 3 tours) ===");
  console.log(JSON.stringify(examples, null, 2));
  console.log(`\nTotal unique images ${APPLY ? "uploaded" : "that would be uploaded"}: ${urlCache.size}`);
  console.log(`Unmatched (left as placehold.co, need manual sourcing): ${UNMATCHED.join(", ")}`);
  if (FAILURES.length) {
    console.log(`\nFAILED downloads (old URL left in place, retry these separately):`);
    FAILURES.forEach(f => console.log(`  - [${f.tour}] ${f.item}: ${f.url}`));
  } else {
    console.log("\nNo download/upload failures.");
  }

  await mongoose.disconnect();
}

main().catch((err) => { console.error(err); process.exit(1); });
