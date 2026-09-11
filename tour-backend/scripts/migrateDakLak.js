/**
 * One-shot content pivot: Vietnam-wide tours -> Đắk Lắk / Tây Nguyên only.
 *
 * Steps (run in order, all against the live `tours` collection - no other collection is
 * touched; `users` is not read or written):
 *   1. Backup: dump every existing Tour document (including embedded waypoints) to a local
 *      JSON file under tour-backend/backups/, timestamped.
 *   2. Delete: Tour.deleteMany({}) - wipes ALL tour documents (curated + guest-copied), since
 *      waypoints are embedded sub-documents there is no separate collection to clean up.
 *   3. Seed: insert 12 new Đắk Lắk-focused tours from DAK_LAK_TOURS below.
 *   4. Verify: re-count tours/waypoints/free-vs-locked and print a summary.
 *
 * Per-waypoint `price` is this app's existing paywall convention (see scripts/seedTours.js):
 * price 0 = "free", price > 0 = "locked" (unlock cost in VND). NOTE: the Android app's actual
 * lock icons (WaypointLockManager.java) currently use a fixed client-side rule - first 2
 * waypoints of every tour always free, rest locked by default - and do not read this `price`
 * field at all. So the exact free/locked pattern requested per itinerary is stored correctly
 * here (and is what the chatbot / any future backend-driven paywall would read), but the
 * *running app UI* will still show its own first-2-free pattern until that logic is wired up
 * to read it. Flagged in the migration's final report - not changed by this script.
 *
 * Region/tag display: TourDetailActivity/TourAdapter auto-derive "destination chips" from the
 * substring after the LAST " - " in each waypoint's locationName (no separate tags/region field
 * exists in the schema) - e.g. "Vườn quốc gia Yok Đôn - Buôn Đôn" -> chip "Buôn Đôn". Every
 * waypoint below is named "<place> - <Đắk Lắk district>" for that reason, and each trip's
 * description also mentions "Đắk Lắk" / "Tây Nguyên" explicitly as plain text.
 *
 * Duration display: TourDetailActivity computes "3N2Đ" style duration from startDate/endDate
 * (nights = date diff), there is no separate duration field - startDate/endDate below are
 * chosen so the diff matches the intended duration.
 *
 * Images: real Wikimedia Commons photos (Special:FilePath - stable, license-checked, redirects
 * to the actual file) are used wherever a subject-matched photo exists (waterfalls, Hồ Lắk,
 * elephants, longhouses, coffee, gong culture, Yok Đôn forest, etc.). Everything else (a named
 * local eatery, a specific homestay/workshop with no public photo, etc.) gets an explicit
 * placehold.co placeholder labelled with the waypoint name - see PLACEHOLDER_WAYPOINTS list
 * printed at the end for exactly which ones still need a real photo.
 *
 * Usage: node scripts/migrateDakLak.js
 */
const path = require("path");
const fs = require("fs");
require("dotenv").config({ path: path.resolve(__dirname, "../.env") });

const mongoose = require("mongoose");
const Tour = require("../src/models/Tour");

const SEED_AUTHOR_ID = "seed-dak-lak-tours-2026";

// ---------- image helpers ----------
function commons(filename) {
  // Stable Commons redirect -> actual upload.wikimedia.org file. Real, license-checked photos.
  return `https://commons.wikimedia.org/wiki/Special:FilePath/${encodeURIComponent(filename)}`;
}
const placeholderLog = [];
function placeholder(label) {
  placeholderLog.push(label);
  return `https://placehold.co/800x600/2d5a3d/ffffff?text=${encodeURIComponent(label)}`;
}

// ---------- waypoint helper ----------
// status: "free" -> price 0, "locked" -> price 2000 (VND, same per-step price as the rest of
// the app's paywall convention).
function wp(name, district, note, status, lat, lng, photoUrl) {
  return {
    locationName: `${name} - ${district}`,
    note,
    price: status === "locked" ? 2000 : 0,
    coordinate: { type: "Point", coordinates: [lng, lat] },
    photos: [photoUrl],
  };
}

const TOURS = [
  {
    title: "Vệt Nắng Bản Đôn: Dấu Chân Đi Tới Đại Ngàn",
    description:
      "Hành trình 3 ngày 2 đêm xuyên buôn làng Bản Đôn (Đắk Lắk, Tây Nguyên) - nơi từng vang danh " +
      "nghề săn voi: nhà sàn cổ, khu mộ vua săn voi, rừng quốc gia Yok Đôn và bến nước Sêrêpôk.",
    imageUrl: commons("Voi ở Bản Đôn.jpg"),
    startDate: "2026-10-01T01:00:00.000Z",
    endDate: "2026-10-03T01:00:00.000Z",
    waypoints: [
      wp("Nhà sàn cổ Vua Săn Voi Amakông (Buôn Trí)", "Buôn Đôn",
        "Nhà sàn cổ hơn trăm năm tuổi của vua săn voi Amakông, còn lưu giữ nhiều hiện vật săn voi.",
        "free", 12.9080, 107.7930, commons("The_house_on_stilts_of_Ede_people_in_a_central_highland.JPG")),
      wp("Quán ăn bản địa Êđê Buôn Triết", "Buôn Đôn",
        "Bữa ăn theo phong cách Êđê bản địa với cơm lam, gà nướng, canh chua lá bép.",
        "locked", 12.9150, 107.8050, placeholder("Quán ăn Êđê Buôn Triết")),
      wp("Vườn quốc gia Yok Đôn", "Buôn Đôn",
        "Khu rừng khộp đặc trưng lớn nhất Việt Nam, môi trường sống của voi hoang dã.",
        "free", 12.9500, 107.6667, commons("Yokdon01.JPG")),
      wp("Bến nước Buôn Bay (Bờ sông Sêrêpôk)", "Buôn Đôn",
        "Bến nước sinh hoạt truyền thống bên dòng Sêrêpôk chảy ngược huyền thoại.",
        "locked", 12.9167, 107.8000, commons("Serepôk1.JPG")),
      wp("Tiệm Cà Phê Mộc Buôn Đôn", "Buôn Đôn",
        "Quán cà phê mộc mạc nhìn ra rừng, thưởng thức cà phê phin Tây Nguyên nguyên bản.",
        "free", 12.9200, 107.7900, commons("Vuoncaphe.jpg")),
      wp("Homestay Nhà sàn Buôn Niêng", "Buôn Đôn",
        "Nghỉ đêm trong nhà sàn truyền thống, trải nghiệm sinh hoạt cùng người bản địa.",
        "locked", 12.9230, 107.7850, commons("House_on_stilt.jpg")),
      wp("Khu Mộ Vua Săn Voi Khunjunob (Buôn Trí)", "Buôn Đôn",
        "Khu mộ cổ theo kiến trúc nhà mồ Tây Nguyên của Khunjunob, ông tổ nghề săn voi.",
        "free", 12.9070, 107.7910, commons("Mo_vua_voi.JPG")),
    ],
  },
  {
    title: "Vị Đất & Hương Cà Phê: Chuyện Xứ Đỏ",
    description:
      "2 ngày 1 đêm giữa thủ phủ cà phê Buôn Ma Thuột và Cư M'gar (Đắk Lắk, Tây Nguyên): đồi cà phê " +
      "bạt ngàn, Bảo tàng Thế giới Cà phê và buôn làng cổ giữa lòng phố núi.",
    imageUrl: commons("Langcaphetrungnguyen.JPG"),
    startDate: "2026-10-08T01:00:00.000Z",
    endDate: "2026-10-09T01:00:00.000Z",
    waypoints: [
      wp("Nông trường & Đồi cà phê Cư M'gar", "Cư M'gar",
        "Đồi cà phê bậc thang xanh mướt trải dài tầm mắt, mùa hoa cà phê trắng xóa tháng 2-3.",
        "locked", 12.8666, 108.1666, commons("Terraced_Coffee_Plants_in_Vietnam.jpg")),
      wp("Bảo tàng Thế giới Cà phê", "Buôn Ma Thuột",
        "Không gian trưng bày văn hóa cà phê từ khắp thế giới, kiến trúc lấy cảm hứng nhà dài Êđê.",
        "free", 12.6839, 108.0378, commons("Baotangcaphe.JPG")),
      wp("Quán cơm gia đình Êđê Buôn Akŏ Dhông", "Buôn Ma Thuột",
        "Bữa cơm gia đình đậm chất Êđê ngay trong buôn cổ giữa lòng thành phố.",
        "free", 12.6830, 108.0330, placeholder("Quán cơm Êđê Akŏ Dhông")),
      wp("Buôn Akŏ Dhông (Buôn Cô Thôn)", "Buôn Ma Thuột",
        "Buôn làng Êđê cổ với những ngôi nhà dài truyền thống nằm giữa lòng đô thị.",
        "free", 12.6833, 108.0333, commons("Cothon01.JPG")),
      wp("Xưởng rang xay cà phê thủ công Buôn Ma Thuột", "Buôn Ma Thuột",
        "Tham quan quy trình rang xay cà phê thủ công, thử cách pha cà phê phin truyền thống.",
        "locked", 12.6700, 108.0500, commons("Coffee_tree_in_Buon_Me_Thuot_city.jpg")),
      wp("Tiệm cà phê Đèo Kơ Nia", "Buôn Ma Thuột",
        "Quán cà phê ngắm hoàng hôn phố núi, không gian mộc gợi nhớ đại ngàn Tây Nguyên.",
        "free", 12.6600, 108.0400, placeholder("Cà phê Đèo Kơ Nia")),
    ],
  },
  {
    title: "Thầm Thì Núi Đá: Tiếng Gọi Lắk & Chu Yang Sin",
    description:
      "3 ngày 2 đêm bên Hồ Lắk và chân núi Chư Yang Sin (huyện Lắk, Đắk Lắk, Tây Nguyên): thuyền độc " +
      "mộc, Biệt điện Bảo Đại, buôn làng ven hồ và không gian cồng chiêng nguyên bản.",
    imageUrl: commons("Holak02.JPG"),
    startDate: "2026-10-15T01:00:00.000Z",
    endDate: "2026-10-17T01:00:00.000Z",
    waypoints: [
      wp("Núi Đá Voi Mẹ (Đá Elephant Mother)", "Lắk",
        "Khối đá nguyên khối lớn nhất Việt Nam, hình dáng gợi liên tưởng chú voi khổng lồ.",
        "free", 12.3833, 108.1000, placeholder("Núi Đá Voi Mẹ")),
      wp("Biệt điện Bảo Đại (Hồ Lắk)", "Lắk",
        "Dinh thự nghỉ dưỡng cũ của vua Bảo Đại, nhìn thẳng ra toàn cảnh Hồ Lắk.",
        "free", 12.4181, 108.1662, commons("Bietdien1.JPG")),
      wp("Quán chả cá thát lát ven Hồ Lắk", "Lắk",
        "Đặc sản chả cá thát lát Hồ Lắk nướng lá chuối, ăn kèm rau rừng.",
        "free", 12.4150, 108.1700, placeholder("Chả cá thát lát Hồ Lắk")),
      wp("Buôn Jun / Buôn Lê", "Lắk",
        "Buôn làng người M'nông ven hồ, còn giữ nghề dệt và đàn voi nhà.",
        "free", 12.4139, 108.1722, commons("Buonjuin.jpg")),
      wp("Bến thuyền độc mộc truyền thống Hồ Lắk", "Lắk",
        "Chèo thuyền độc mộc nguyên khối len lỏi giữa mặt hồ tĩnh lặng.",
        "locked", 12.4170, 108.1680, commons("Thuyendocmoc.JPG")),
      wp("Nhà cộng đồng Buôn Búp (Không gian Cồng chiêng)", "Lắk",
        "Không gian trình diễn cồng chiêng Tây Nguyên - di sản văn hóa phi vật thể thế giới.",
        "locked", 12.4300, 108.1600, commons("Congchieng01.JPG")),
    ],
  },
  {
    title: "Thác Ngàn Sương Khói: Khám Phá Luồng Thác Bất Tận",
    description:
      "2 ngày 1 đêm chinh phục cụm thác lớn nhất Tây Nguyên trên sông Sêrêpôk (Krông Ana, Đắk Lắk): " +
      "Dray Nur hùng vĩ, Dray Sáp mờ sương và làng nghề đan lát Buôn Kuốp.",
    imageUrl: commons("Thacgialong04.JPG"),
    startDate: "2026-10-22T01:00:00.000Z",
    endDate: "2026-10-23T01:00:00.000Z",
    waypoints: [
      wp("Thác Dray Nur", "Krông Ana",
        "Một trong những thác nước hùng vĩ nhất Tây Nguyên, dòng nước đổ trắng xóa quanh năm.",
        "free", 12.6314, 108.0989, commons("Buôn Ma Thuột banner Đray Nur waterfall.jpg")),
      wp("Thác Dray Sáp Lơ (Thác Gia Long)", "Krông Ana",
        "Thác nước ẩn mình trong sương mờ, gắn với truyền thuyết vua Gia Long.",
        "free", 12.6280, 108.0800, commons("Thacgialong02.JPG")),
      wp("Quán cơm lam gà nướng chân thác", "Krông Ana",
        "Cơm lam ống tre, gà nướng mật ong thưởng thức ngay dưới chân thác.",
        "free", 12.6300, 108.0850, commons("Cơm lam Tây Nguyên.jpg")),
      wp("Buôn Kuốp (Làng đan lát tre nứa thủ công)", "Krông Ana",
        "Làng nghề đan lát tre nứa truyền thống ven sông Sêrêpôk.",
        "locked", 12.6350, 108.1100, commons("Kuop highland village.jpg")),
    ],
  },
  {
    title: "Bản Giao Hưởng Chiêng Ba",
    description:
      "2 ngày 1 đêm khám phá buôn Ea Kao (Buôn Ma Thuột, Đắk Lắk, Tây Nguyên): nhà dài Êđê, phiên chợ " +
      "vùng cao và nghề dệt thổ cẩm truyền thống.",
    imageUrl: commons("The_house_on_stilts_of_Ede_people_in_a_central_highland.JPG"),
    startDate: "2026-10-29T01:00:00.000Z",
    endDate: "2026-10-30T01:00:00.000Z",
    waypoints: [
      wp("Buôn Ea Kao", "Buôn Ma Thuột",
        "Buôn làng Êđê ven hồ Ea Kao, nhịp sống nông nghiệp gắn với ruộng lúa và cà phê.",
        "free", 12.5900, 108.0700, placeholder("Buôn Ea Kao")),
      wp("Nhà dài Êđê buôn Ea Kao", "Buôn Ma Thuột",
        "Nhà dài truyền thống của một đại gia đình mẫu hệ Êđê, dài hàng chục mét.",
        "locked", 12.5920, 108.0680, commons("House_on_stilt.jpg")),
      wp("Chợ phiên vùng cao", "Buôn Ma Thuột",
        "Phiên chợ họp sớm, nơi bà con các buôn mang nông sản, thổ cẩm đến trao đổi.",
        "free", 12.5950, 108.0650, placeholder("Chợ phiên vùng cao")),
      wp("Xưởng dệt thổ cẩm Buôn Ea Kao", "Buôn Ma Thuột",
        "Xem nghệ nhân dệt thổ cẩm hoa văn truyền thống Êđê trên khung cửi gỗ.",
        "locked", 12.5880, 108.0720, placeholder("Xưởng dệt thổ cẩm Ea Kao")),
    ],
  },
  {
    title: "Sắc Chàm Thổ Cẩm",
    description:
      "2 ngày 1 đêm ở thị trấn Buôn Trấp, Krông Ana (Đắk Lắk, Tây Nguyên): làng dệt thổ cẩm, chợ quê " +
      "và cây cầu treo bắc qua sông Krông Ana.",
    imageUrl: commons("Krong Ana district.jpg"),
    startDate: "2026-11-05T01:00:00.000Z",
    endDate: "2026-11-06T01:00:00.000Z",
    waypoints: [
      wp("Làng dệt thổ cẩm Buôn Trấp", "Krông Ana",
        "Làng nghề dệt thổ cẩm lâu đời, hoa văn mang đậm bản sắc dân tộc thiểu số Tây Nguyên.",
        "locked", 12.4990, 108.0230, placeholder("Làng dệt thổ cẩm Buôn Trấp")),
      wp("Chợ Krông Ana", "Krông Ana",
        "Chợ huyện sầm uất, đặc sản nông sản và cà phê Tây Nguyên.",
        "free", 12.4990, 108.0230, commons("Krong Ana district.jpg")),
      wp("Cầu treo Buôn Trấp", "Krông Ana",
        "Cây cầu treo bắc qua sông Krông Ana, điểm ngắm hoàng hôn quen thuộc của người dân.",
        "free", 12.5020, 108.0210, placeholder("Cầu treo Buôn Trấp")),
      wp("Quán bún đỏ Buôn Ma Thuột", "Krông Ana",
        "Món bún đỏ đặc sản Đắk Lắk, nước dùng đậm đà nấu cùng gạch cua.",
        "free", 12.5000, 108.0250, placeholder("Bún đỏ Buôn Ma Thuột")),
    ],
  },
  {
    title: "Rừng Sâu Chư Yang Sin",
    description:
      "2 ngày 1 đêm trekking Vườn quốc gia Chư Yang Sin, Krông Bông (Đắk Lắk, Tây Nguyên): rừng nguyên " +
      "sinh, thác Krông Kmar và buôn làng dưới chân núi.",
    imageUrl: commons("Đỉnh Chư Yang Sin.jpg"),
    startDate: "2026-11-12T01:00:00.000Z",
    endDate: "2026-11-13T01:00:00.000Z",
    waypoints: [
      wp("Vườn quốc gia Chư Yang Sin", "Krông Bông",
        "Một trong những khu rừng nguyên sinh đa dạng sinh học bậc nhất Tây Nguyên.",
        "free", 12.4667, 108.3667, commons("Chuyangsin03.JPG")),
      wp("Thác Krông Kmar", "Krông Bông",
        "Thác nước len lỏi giữa rừng già, nước trong mát quanh năm.",
        "free", 12.4700, 108.3720, commons("Krongkma.jpg")),
      wp("Buôn Đắk Tuôr", "Krông Bông",
        "Buôn làng người M'nông dưới chân Chư Yang Sin, từng là căn cứ cách mạng xưa.",
        "locked", 12.4200, 108.3800, placeholder("Buôn Đắk Tuôr")),
      wp("Suối nước nóng Krông Bông", "Krông Bông",
        "Suối khoáng nóng tự nhiên giữa thung lũng, thư giãn sau ngày trekking.",
        "locked", 12.4500, 108.3000, placeholder("Suối nước nóng Krông Bông")),
    ],
  },
  {
    title: "Huyền Thoại Săn Voi",
    description:
      "2 ngày 1 đêm ngược dòng lịch sử nghề săn voi ở Buôn Đôn (Đắk Lắk, Tây Nguyên): cầu treo Sêrêpôk, " +
      "làng nghề đóng thuyền độc mộc và nhà trưng bày văn hóa bản địa.",
    imageUrl: commons("Bandon13.JPG"),
    startDate: "2026-11-19T01:00:00.000Z",
    endDate: "2026-11-20T01:00:00.000Z",
    waypoints: [
      wp("Cầu treo Buôn Đôn", "Buôn Đôn",
        "Cây cầu treo bằng tre nứa nổi tiếng bắc qua sông Sêrêpôk, lắc lư giữa tán cây cổ thụ.",
        "free", 12.9167, 107.8050, commons("Bandon02.JPG")),
      wp("Làng nghề đóng thuyền độc mộc", "Buôn Đôn",
        "Nghệ nhân đục đẽo thuyền độc mộc từ nguyên khối gỗ theo kỹ thuật cổ truyền.",
        "locked", 12.9200, 107.7980, commons("Thuyendocmoc1.JPG")),
      wp("Nhà trưng bày văn hóa Buôn Đôn", "Buôn Đôn",
        "Trưng bày hiện vật, trang phục và dụng cụ săn voi của các tộc người bản địa.",
        "free", 12.9223, 107.7947, placeholder("Nhà trưng bày văn hóa Buôn Đôn")),
      wp("Chợ đêm Buôn Đôn", "Buôn Đôn",
        "Phiên chợ đêm nhỏ bán đặc sản rượu cần, thổ cẩm và đồ lưu niệm.",
        "locked", 12.9210, 107.7960, placeholder("Chợ đêm Buôn Đôn")),
    ],
  },
  {
    title: "Hoàng Hôn Ea Kao",
    description:
      "2 ngày 1 đêm thư thái bên Hồ Ea Kao (Buôn Ma Thuột, Đắk Lắk, Tây Nguyên): cánh đồng lúa Hòa " +
      "Thắng, vườn cây ăn trái và những buổi hoàng hôn trên mặt hồ.",
    imageUrl: commons("The central highlands in daklak Vietnam.jpg"),
    startDate: "2026-11-26T01:00:00.000Z",
    endDate: "2026-11-27T01:00:00.000Z",
    waypoints: [
      wp("Hồ Ea Kao", "Buôn Ma Thuột",
        "Hồ nước ngọt lớn ven thành phố, mặt hồ phẳng lặng in bóng núi đồi.",
        "free", 12.5833, 108.0667, placeholder("Hồ Ea Kao")),
      wp("Vườn cây ăn trái Ea Kao", "Buôn Ma Thuột",
        "Vườn sầu riêng, bơ, cà phê xen canh trĩu quả quanh hồ Ea Kao.",
        "locked", 12.5860, 108.0690, placeholder("Vườn cây ăn trái Ea Kao")),
      wp("Cánh đồng lúa Hòa Thắng", "Buôn Ma Thuột",
        "Cánh đồng lúa xanh mướt xen giữa các buôn làng ngoại thành Buôn Ma Thuột.",
        "free", 12.6100, 108.0500, placeholder("Cánh đồng lúa Hòa Thắng")),
      wp("Quán cà phê view hồ Ea Kao", "Buôn Ma Thuột",
        "Quán cà phê sân vườn nhìn thẳng ra hồ, lý tưởng để ngắm hoàng hôn.",
        "free", 12.5840, 108.0660, commons("Vuoncaphe.jpg")),
    ],
  },
  {
    title: "Nắng Gió Ea Súp",
    description:
      "2 ngày 1 đêm khám phá vùng biên viễn Ea Súp (Đắk Lắk, Tây Nguyên): hồ thủy lợi lớn, rừng khộp " +
      "đặc trưng và chợ phiên vùng biên giới Việt - Campuchia.",
    imageUrl: commons("Easup.JPG"),
    startDate: "2026-12-03T01:00:00.000Z",
    endDate: "2026-12-04T01:00:00.000Z",
    waypoints: [
      wp("Hồ Ea Súp Thượng", "Ea Súp",
        "Hồ thủy lợi rộng lớn giữa vùng bán ngập, hoàng hôn phản chiếu mặt nước tuyệt đẹp.",
        "free", 13.0500, 107.8667, placeholder("Hồ Ea Súp Thượng")),
      wp("Rừng khộp Ea Súp", "Ea Súp",
        "Rừng khộp thay lá theo mùa, mùa khô lá vàng rực cả một vùng biên giới.",
        "locked", 13.1200, 107.9000, commons("Rừng khộp Buôn Mê Thuật.jpg")),
      wp("Chợ biên giới Ea Súp", "Ea Súp",
        "Chợ phiên vùng biên, giao thương hàng hóa giữa cư dân hai bên biên giới.",
        "free", 13.1000, 107.9333, placeholder("Chợ biên giới Ea Súp")),
      wp("Làng người Tày di cư", "Ea Súp",
        "Cộng đồng người Tày di cư từ phía Bắc, còn giữ nhà sàn và phong tục quê gốc.",
        "locked", 13.0900, 107.9100, placeholder("Làng người Tày di cư")),
    ],
  },
  {
    title: "Ngày Hè M'Drắk",
    description:
      "2 ngày 1 đêm giữa cao nguyên M'Drắk (Đắk Lắk, Tây Nguyên): đồi cỏ hồng bạt ngàn, trang trại bò " +
      "sữa và thác Bìm Bịp giữa rừng.",
    imageUrl: commons("Madrak02.JPG"),
    startDate: "2026-12-10T01:00:00.000Z",
    endDate: "2026-12-11T01:00:00.000Z",
    waypoints: [
      wp("Đồi cỏ hồng M'Drắk", "M'Drắk",
        "Đồi cỏ chuyển hồng rực rỡ vào mùa cuối năm, view 360 độ toàn cảnh cao nguyên.",
        "free", 12.7500, 108.7500, commons("Mdrak.JPG")),
      wp("Trang trại bò sữa M'Drắk", "M'Drắk",
        "Trang trại bò sữa quy mô lớn giữa vùng đồi cỏ M'Drắk.",
        "locked", 12.7600, 108.7600, placeholder("Trang trại bò sữa M'Drắk")),
      wp("Thác Bìm Bịp", "M'Drắk",
        "Thác nước nhỏ ẩn giữa rừng, dòng nước trong vắt đổ qua các bậc đá.",
        "free", 12.7300, 108.7800, commons("Thác bìm bịp.jpg")),
      wp("Buôn người Êđê M'Drắk", "M'Drắk",
        "Buôn làng Êđê giữ nếp sinh hoạt truyền thống giữa vùng cao nguyên M'Drắk.",
        "locked", 12.7700, 108.7700, placeholder("Buôn người Êđê M'Drắk")),
    ],
  },
  {
    title: "Sương Sớm Krông Pắc",
    description:
      "2 ngày 1 đêm ở vựa sầu riêng Krông Pắc (Đắk Lắk, Tây Nguyên): vườn trái cây trĩu quả, chợ trái " +
      "cây sớm và nhà thờ gỗ giữa thị trấn cao nguyên.",
    imageUrl: placeholder("Vườn sầu riêng Krông Pắc (cover)"),
    startDate: "2026-12-17T01:00:00.000Z",
    endDate: "2026-12-18T01:00:00.000Z",
    waypoints: [
      wp("Vườn sầu riêng Krông Pắc", "Krông Pắc",
        "Thủ phủ sầu riêng Đắk Lắk, vườn trái cây trĩu quả vào mùa thu hoạch.",
        "locked", 12.7167, 108.3200, placeholder("Vườn sầu riêng Krông Pắc")),
      wp("Chợ trái cây Krông Pắc", "Krông Pắc",
        "Chợ đầu mối trái cây sầm uất, đặc biệt vào mùa sầu riêng, bơ, sầu riêng.",
        "free", 12.7167, 108.3167, placeholder("Chợ trái cây Krông Pắc")),
      wp("Nhà thờ gỗ Krông Pắc", "Krông Pắc",
        "Nhà thờ gỗ mang kiến trúc đặc trưng cao nguyên giữa thị trấn nhỏ.",
        "free", 12.7180, 108.3150, placeholder("Nhà thờ gỗ Krông Pắc")),
      wp("Quán bánh canh cá lóc", "Krông Pắc",
        "Bánh canh cá lóc đặc sản, sợi bánh canh dai mềm, nước dùng ngọt thanh.",
        "free", 12.7150, 108.3180, placeholder("Bánh canh cá lóc Krông Pắc")),
    ],
  },
];

async function main() {
  const uri = process.env.MONGODB_URI;
  if (!uri) throw new Error("MONGODB_URI is missing - check tour-backend/.env");

  await mongoose.connect(uri);
  console.log(`[migrate] Connected to database: ${mongoose.connection.name}\n`);

  // ---------- STEP 1: backup ----------
  const existing = await Tour.find({}).lean();
  const backupsDir = path.resolve(__dirname, "../backups");
  fs.mkdirSync(backupsDir, { recursive: true });
  const stamp = new Date().toISOString().replace(/[-:]/g, "").replace(/\..+/, "").replace("T", "_");
  const backupPath = path.join(backupsDir, `backup_trips_${stamp}.json`);
  fs.writeFileSync(backupPath, JSON.stringify(existing, null, 2), "utf8");
  const backedUpWaypoints = existing.reduce((sum, t) => sum + (t.waypoints || []).length, 0);
  console.log(`[migrate] STEP 1 - Backup: ${existing.length} tour doc(s), ${backedUpWaypoints} waypoint(s)`);
  console.log(`[migrate]   -> ${backupPath}\n`);

  // ---------- STEP 2: delete ----------
  const deleted = await Tour.deleteMany({});
  console.log(`[migrate] STEP 2 - Deleted ${deleted.deletedCount} tour document(s) (waypoints are embedded, removed with them).`);
  console.log(`[migrate]   users collection untouched (not read or written by this script).\n`);

  // ---------- STEP 3: seed ----------
  const docs = TOURS.map((t) => ({
    ...t,
    authorId: SEED_AUTHOR_ID,
    status: "Upcoming",
    isShared: true,
  }));
  const inserted = await Tour.insertMany(docs, { ordered: true });
  console.log(`[migrate] STEP 3 - Inserted ${inserted.length} new Đắk Lắk / Tây Nguyên tour(s).\n`);

  // ---------- STEP 4/5: verify ----------
  const finalCount = await Tour.countDocuments({});
  const finalTours = await Tour.find({}).lean();
  let totalWp = 0, freeWp = 0, lockedWp = 0;
  console.log("[migrate] STEP 5 - Verification:\n");
  finalTours.forEach((t) => {
    const wps = t.waypoints || [];
    const free = wps.filter((w) => w.price === 0).length;
    const locked = wps.filter((w) => w.price > 0).length;
    totalWp += wps.length; freeWp += free; lockedWp += locked;
    console.log(`  - "${t.title}" : ${wps.length} waypoints (${free} free / ${locked} locked)`);
  });
  console.log(`\n[migrate] TOTAL: ${finalCount} tours, ${totalWp} waypoints (${freeWp} free / ${lockedWp} locked)`);
  const leftover = finalTours.filter((t) => t.authorId !== SEED_AUTHOR_ID).length;
  console.log(`[migrate] Old tour data present? ${leftover > 0 ? `YES - ${leftover} doc(s) not authored by ${SEED_AUTHOR_ID}` : "No - all tours are the new Đắk Lắk set"}`);

  console.log(`\n[migrate] Placeholder images used (${placeholderLog.length}) - need real photos:`);
  placeholderLog.forEach((label) => console.log(`  - ${label}`));

  await mongoose.disconnect();
  console.log("\n[migrate] Done.");
}

main().catch((err) => {
  console.error("[migrate] FAILED:", err);
  process.exit(1);
});
