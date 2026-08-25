/**
 * Seed script: inserts 10 sample Vietnam tours into the existing Tour collection.
 *
 * Uses the project's existing Mongoose Tour schema (src/models/Tour.js) as-is - no new
 * fields added. Per-waypoint `price > 0` is this app's existing paywall convention
 * (locked/premium waypoint), `price: 0` means free/unlocked - same as used elsewhere
 * in the app, there is no separate `isPremium` field in the schema.
 *
 * Idempotent: since the schema has no `slug` field to dedupe on, this instead deletes
 * any existing tours matching { authorId: SEED_AUTHOR_ID, title } for each of the 10
 * titles below before inserting, so re-running never creates duplicates. Only touches
 * documents with authorId === SEED_AUTHOR_ID - does not touch any other Tour or
 * collection in the database.
 *
 * Usage: node scripts/seedTours.js   (run from tour-backend/)
 */
const path = require("path");
require("dotenv").config({ path: path.resolve(__dirname, "../.env") });

const mongoose = require("mongoose");
const Tour = require("../src/models/Tour");

const SEED_AUTHOR_ID = "seed-vn-tours-2026";

function cover(seed) {
  return `https://picsum.photos/seed/${seed}/800/500`;
}

function waypoint(locationName, note, price, lat, lng, photoSeed) {
  return {
    locationName,
    note,
    price,
    coordinate: { type: "Point", coordinates: [lng, lat] },
    photos: [cover(photoSeed)],
  };
}

const TOURS = [
  {
    title: "Vịnh Hạ Long Huyền Ảo 3N2Đ",
    description:
      "Du thuyền khám phá kỳ quan thiên nhiên thế giới Vịnh Hạ Long, chèo kayak giữa những đảo đá vôi, " +
      "khám phá hang động huyền bí và trải nghiệm cuộc sống làng chài trên vịnh.",
    imageUrl: cover("halong-cover"),
    startDate: "2026-09-05T00:00:00.000Z",
    endDate: "2026-09-07T00:00:00.000Z",
    waypoints: [
      waypoint("Vịnh Hạ Long - Bãi Cháy", "Lên du thuyền, ngắm hoàng hôn trên vịnh.", 0, 20.9101, 107.1824, "halong-1"),
      waypoint("Hang Sửng Sốt", "Khám phá hang động lớn nhất Hạ Long, nhũ đá kỳ vĩ.", 90000, 20.8967, 107.0619, "halong-2"),
      waypoint("Đảo Titop", "Leo núi ngắm toàn cảnh vịnh, tắm biển bãi cát trắng.", 60000, 20.8886, 107.0692, "halong-3"),
      waypoint("Làng chài Cửa Vạn", "Chèo thuyền nan thăm làng chài nổi trên vịnh.", 0, 20.7975, 107.0453, "halong-4"),
      waypoint("Hang Đầu Gỗ", "Hang động từng là nơi cất giấu cọc gỗ Bạch Đằng.", 70000, 20.9024, 107.0611, "halong-5"),
    ],
  },
  {
    title: "Đà Lạt Ngàn Hoa 3N2Đ",
    description:
      "Chuyến đi thư giãn giữa thành phố ngàn hoa: hồ nước thơ mộng, đồi chè xanh mướt, " +
      "thung lũng tình yêu và không khí se lạnh đặc trưng cao nguyên.",
    imageUrl: cover("dalat-cover"),
    startDate: "2026-09-19T00:00:00.000Z",
    endDate: "2026-09-21T00:00:00.000Z",
    waypoints: [
      waypoint("Hồ Xuân Hương", "Dạo bộ quanh hồ trung tâm thành phố.", 0, 11.9404, 108.4411, "dalat-1"),
      waypoint("Thung lũng Tình Yêu", "Chụp ảnh cùng đồi thông và vườn hoa.", 50000, 11.9639, 108.4506, "dalat-2"),
      waypoint("Đồi chè Cầu Đất", "Tham quan đồi chè, thử trà tại nhà máy cổ.", 30000, 11.8167, 108.5167, "dalat-3"),
      waypoint("Ga Đà Lạt", "Tham quan nhà ga xe lửa cổ kiến trúc Pháp.", 0, 11.9462, 108.4437, "dalat-4"),
      waypoint("Thác Datanla", "Trải nghiệm máng trượt xuống thác nước.", 80000, 11.8894, 108.4453, "dalat-5"),
    ],
  },
  {
    title: "Phố Cổ Hội An 2N1Đ",
    description:
      "Dạo bước qua từng con phố đèn lồng cổ kính, thưởng thức ẩm thực địa phương và " +
      "khám phá làng nghề truyền thống ven sông Thu Bồn.",
    imageUrl: cover("hoian-cover"),
    startDate: "2026-10-03T00:00:00.000Z",
    endDate: "2026-10-04T00:00:00.000Z",
    waypoints: [
      waypoint("Chùa Cầu Hội An", "Biểu tượng phố cổ, cây cầu gỗ hơn 400 năm tuổi.", 0, 15.8794, 108.3260, "hoian-1"),
      waypoint("Phố cổ đèn lồng", "Dạo phố đêm rực rỡ ánh đèn lồng, thả hoa đăng.", 0, 15.8801, 108.3380, "hoian-2"),
      waypoint("Rừng dừa Bảy Mẫu", "Ngồi thuyền thúng giữa rừng dừa nước Cẩm Thanh.", 100000, 15.8590, 108.3480, "hoian-3"),
      waypoint("Cù Lao Chàm", "Lặn ngắm san hô, tắm biển đảo hoang sơ.", 250000, 15.9500, 108.5167, "hoian-4"),
      waypoint("Làng gốm Thanh Hà", "Tự tay nặn gốm cùng nghệ nhân làng nghề.", 0, 15.8825, 108.3080, "hoian-5"),
    ],
  },
  {
    title: "Sapa Mây Núi 3N2Đ",
    description:
      "Trekking giữa ruộng bậc thang, chinh phục nóc nhà Đông Dương và tìm hiểu văn hóa " +
      "các bản làng dân tộc vùng cao Tây Bắc.",
    imageUrl: cover("sapa-cover"),
    startDate: "2026-10-17T00:00:00.000Z",
    endDate: "2026-10-19T00:00:00.000Z",
    waypoints: [
      waypoint("Bản Cát Cát", "Tham quan bản làng người Mông, thác nước nhỏ.", 0, 22.3364, 103.8409, "sapa-1"),
      waypoint("Đỉnh Fansipan", "Cáp treo lên nóc nhà Đông Dương, độ cao 3.143m.", 150000, 22.3033, 103.7756, "sapa-2"),
      waypoint("Thung lũng Mường Hoa", "Ngắm ruộng bậc thang mùa lúa chín.", 0, 22.2853, 103.8494, "sapa-3"),
      waypoint("Nhà thờ đá Sapa", "Tham quan nhà thờ cổ giữa trung tâm thị trấn.", 0, 22.3357, 103.8438, "sapa-4"),
      waypoint("Bản Tả Van", "Trekking xuyên bản làng người Giáy, homestay bản địa.", 60000, 22.2833, 103.8500, "sapa-5"),
    ],
  },
  {
    title: "Phú Quốc Biển Đảo 4N3Đ",
    description:
      "Đảo ngọc với biển xanh cát trắng, lặn ngắm san hô, khám phá safari bán hoang dã " +
      "và thưởng thức hải sản tươi sống mỗi tối.",
    imageUrl: cover("phuquoc-cover"),
    startDate: "2026-11-07T00:00:00.000Z",
    endDate: "2026-11-10T00:00:00.000Z",
    waypoints: [
      waypoint("Bãi Sao", "Tắm biển nước trong xanh, cát trắng mịn.", 0, 10.0206, 104.0148, "phuquoc-1"),
      waypoint("Vinpearl Safari", "Tham quan vườn thú bán hoang dã lớn nhất Việt Nam.", 650000, 10.3574, 103.8608, "phuquoc-2"),
      waypoint("Cáp treo Hòn Thơm", "Cáp treo vượt biển dài nhất thế giới.", 400000, 10.0447, 104.0217, "phuquoc-3"),
      waypoint("Chợ đêm Dinh Cậu", "Ăn hải sản tươi, mua quà lưu niệm.", 0, 10.2202, 103.9605, "phuquoc-4"),
      waypoint("Nhà tù Phú Quốc", "Tham quan di tích lịch sử thời kháng chiến.", 0, 10.1875, 104.0055, "phuquoc-5"),
      waypoint("Bãi Dài", "Bãi biển hoang sơ, ngắm hoàng hôn tuyệt đẹp.", 50000, 10.3167, 103.8667, "phuquoc-6"),
    ],
  },
  {
    title: "Ninh Bình Tràng An 2N1Đ",
    description:
      "Ngồi thuyền len lỏi qua hang động và núi đá vôi giữa 'Vịnh Hạ Long trên cạn', " +
      "viếng thăm ngôi chùa lớn nhất Đông Nam Á.",
    imageUrl: cover("ninhbinh-cover"),
    startDate: "2026-09-26T00:00:00.000Z",
    endDate: "2026-09-27T00:00:00.000Z",
    waypoints: [
      waypoint("Tràng An", "Ngồi thuyền xuyên hang động, núi đá vôi kỳ vĩ.", 120000, 20.2494, 105.9147, "ninhbinh-1"),
      waypoint("Tam Cốc", "Chèo thuyền qua ba hang, ngắm cánh đồng lúa.", 0, 20.2167, 105.9333, "ninhbinh-2"),
      waypoint("Chùa Bái Đính", "Tham quan quần thể chùa lớn nhất Đông Nam Á.", 50000, 20.2739, 105.8672, "ninhbinh-3"),
      waypoint("Hang Múa", "Leo 500 bậc đá ngắm toàn cảnh Tam Cốc từ trên cao.", 0, 20.2244, 105.9333, "ninhbinh-4"),
      waypoint("Cố đô Hoa Lư", "Tham quan kinh đô xưa của Đại Cồ Việt.", 0, 20.2833, 105.9167, "ninhbinh-5"),
    ],
  },
  {
    title: "Huế Cố Đô 2N1Đ",
    description:
      "Ngược dòng sông Hương, thăm Đại Nội và các lăng tẩm triều Nguyễn, cảm nhận " +
      "nhịp sống trầm mặc của cố đô.",
    imageUrl: cover("hue-cover"),
    startDate: "2026-10-10T00:00:00.000Z",
    endDate: "2026-10-11T00:00:00.000Z",
    waypoints: [
      waypoint("Đại Nội Huế", "Tham quan Hoàng thành, cung điện triều Nguyễn.", 200000, 16.4698, 107.5796, "hue-1"),
      waypoint("Chùa Thiên Mụ", "Ngôi chùa cổ linh thiêng bên bờ sông Hương.", 0, 16.4539, 107.5450, "hue-2"),
      waypoint("Lăng Khải Định", "Kiến trúc lăng tẩm giao thoa Đông - Tây độc đáo.", 150000, 16.4131, 107.5764, "hue-3"),
      waypoint("Sông Hương - thuyền rồng", "Nghe ca Huế trên thuyền rồng lúc hoàng hôn.", 0, 16.4667, 107.5833, "hue-4"),
      waypoint("Cầu Trường Tiền", "Dạo bộ ngắm cầu về đêm, biểu tượng của Huế.", 0, 16.4675, 107.5844, "hue-5"),
    ],
  },
  {
    title: "Nha Trang Biển Xanh 3N2Đ",
    description:
      "Tắm biển, lặn ngắm san hô, vui chơi tại đảo giải trí và khám phá tháp Chăm cổ " +
      "bên bờ vịnh biển đẹp nhất Việt Nam.",
    imageUrl: cover("nhatrang-cover"),
    startDate: "2026-11-21T00:00:00.000Z",
    endDate: "2026-11-23T00:00:00.000Z",
    waypoints: [
      waypoint("Bãi biển Nha Trang", "Tắm biển, dạo bộ công viên bờ biển.", 0, 12.2388, 109.1967, "nhatrang-1"),
      waypoint("Vinpearl Land", "Cáp treo vượt biển, công viên giải trí trên đảo.", 700000, 12.2172, 109.2361, "nhatrang-2"),
      waypoint("Tháp Bà Ponagar", "Tham quan quần thể tháp Chăm cổ hơn nghìn năm.", 0, 12.2653, 109.1936, "nhatrang-3"),
      waypoint("Vịnh Ninh Vân", "Nghỉ dưỡng biệt lập, lặn ngắm san hô.", 300000, 12.4167, 109.3167, "nhatrang-4"),
      waypoint("Chợ Đầm", "Mua đặc sản khô cá, yến sào làm quà.", 0, 12.2500, 109.1944, "nhatrang-5"),
    ],
  },
  {
    title: "Mộc Châu Cao Nguyên 2N1Đ",
    description:
      "Rong ruổi giữa đồi chè xanh mướt, thác nước hùng vĩ và rừng thông cao nguyên " +
      "Tây Bắc trong sương sớm.",
    imageUrl: cover("mocchau-cover"),
    startDate: "2026-12-05T00:00:00.000Z",
    endDate: "2026-12-06T00:00:00.000Z",
    waypoints: [
      waypoint("Đồi chè trái tim Mộc Châu", "Chụp ảnh đồi chè hình trái tim nổi tiếng.", 0, 20.8333, 104.6167, "mocchau-1"),
      waypoint("Thác Dải Yếm", "Tham quan thác nước đôi dòng huyền thoại.", 40000, 20.7500, 104.5500, "mocchau-2"),
      waypoint("Rừng thông bản Áng", "Cắm trại, dạo bộ giữa rừng thông ba lá.", 0, 20.8500, 104.6333, "mocchau-3"),
      waypoint("Hang Dơi Mộc Châu", "Khám phá hang động đá vôi có đàn dơi trú ngụ.", 30000, 20.8333, 104.5833, "mocchau-4"),
      waypoint("Đỉnh Pha Luông", "Trekking chinh phục nóc nhà Mộc Châu.", 90000, 20.7000, 104.4333, "mocchau-5"),
    ],
  },
  {
    title: "Côn Đảo Hoang Sơ 3N2Đ",
    description:
      "Hòn đảo linh thiêng với bãi biển hoang sơ, di tích lịch sử nhà tù và rạn san hô " +
      "nguyên vẹn bậc nhất Việt Nam.",
    imageUrl: cover("condao-cover"),
    startDate: "2026-12-12T00:00:00.000Z",
    endDate: "2026-12-14T00:00:00.000Z",
    waypoints: [
      waypoint("Bãi biển Đầm Trầu", "Một trong những bãi biển đẹp nhất Việt Nam.", 0, 8.6833, 106.6167, "condao-1"),
      waypoint("Nhà tù Côn Đảo", "Di tích lịch sử, chuồng cọp Pháp - Mỹ.", 40000, 8.6833, 106.6000, "condao-2"),
      waypoint("Nghĩa trang Hàng Dương", "Viếng thăm nghĩa trang liệt sĩ linh thiêng.", 0, 8.6900, 106.6100, "condao-3"),
      waypoint("Vườn quốc gia Côn Đảo", "Lặn ngắm san hô, rùa biển đẻ trứng mùa hè.", 350000, 8.7000, 106.6333, "condao-4"),
      waypoint("Miếu Bà Phi Yến", "Tham quan miếu thờ linh thiêng của người dân đảo.", 0, 8.6750, 106.5917, "condao-5"),
    ],
  },
];

async function main() {
  const uri = process.env.MONGODB_URI;
  if (!uri) throw new Error("MONGODB_URI is missing - check tour-backend/.env");

  await mongoose.connect(uri);
  console.log(`[seed] Connected to database: ${mongoose.connection.name}`);

  const titles = TOURS.map((t) => t.title);
  const deleted = await Tour.deleteMany({ authorId: SEED_AUTHOR_ID, title: { $in: titles } });
  console.log(`[seed] Removed ${deleted.deletedCount} pre-existing seed tour(s) with matching titles (idempotency).`);

  const docs = TOURS.map((t) => ({
    ...t,
    authorId: SEED_AUTHOR_ID,
    status: "Upcoming",
    isShared: true,
  }));

  const inserted = await Tour.insertMany(docs, { ordered: true });
  console.log(`\n[seed] Inserted ${inserted.length} tours:\n`);
  inserted.forEach((t) => console.log(`  - ${t.title}  ->  ${t._id}`));

  await mongoose.disconnect();
  console.log("\n[seed] Done.");
}

main().catch((err) => {
  console.error("[seed] FAILED:", err);
  process.exit(1);
});
