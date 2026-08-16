package com.example.mycurrenttour;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Nguồn dữ liệu giả (hardcode) để test UI trong lúc backend chưa chạy.
 *
 * TODO: set USE_MOCK_DATA = false khi backend sẵn sàng
 *
 * Cách dùng: các màn hình gọi API danh sách tour (DiscoveryFragment, MyTourFragment...)
 * kiểm tra cờ USE_MOCK_DATA ở đầu hàm load; nếu true thì dùng getMockTours()
 * thay vì gọi ApiService, code gọi API thật vẫn được giữ nguyên bên dưới.
 */
public class MockDataProvider {

    // TODO: set USE_MOCK_DATA = false khi backend sẵn sàng
    public static boolean USE_MOCK_DATA = true;

    public static List<Tour> getMockTours() {
        List<Tour> tours = new ArrayList<>();
        tours.add(buildHanoiSapaTour());
        tours.add(buildDaNangHoiAnTour());
        tours.add(buildPhuQuocTour());
        // 2 tour nhiều waypoint hơn (7-8), dùng để test nhiều step khóa cùng lúc + giá Unlock full.
        tours.add(buildMienTayTour());
        tours.add(buildTayNguyenTour());
        return tours;
    }

    private static Tour buildHanoiSapaTour() {
        List<Tour.Waypoint> waypoints = Arrays.asList(
                buildWaypoint("Hồ Gươm - Hà Nội", "Dạo quanh hồ, chụp ảnh Tháp Rùa", 0, 21.0285, 105.8542),
                buildWaypoint("Bản Cát Cát - Sa Pa", "Tham quan bản làng người Mông", 15, 22.3364, 103.8409),
                buildWaypoint("Đỉnh Fansipan - Sa Pa", "Đi cáp treo lên đỉnh Fansipan", 40, 22.3033, 103.7756),
                buildWaypoint("Thung lũng Mường Hoa - Sa Pa", "Ngắm ruộng bậc thang", 10, 22.2853, 103.8494),
                buildWaypoint("Nhà thờ đá Sa Pa", "Tham quan nhà thờ cổ", 0, 22.3357, 103.8438)
        );
        return buildTour(
                "mock-tour-1",
                "Hà Nội - Sa Pa 3N2Đ",
                "Chuyến đi khám phá Thủ đô và núi rừng Tây Bắc",
                null,
                "Upcoming",
                "2026-08-15T00:00:00.000Z",
                "2026-08-17T00:00:00.000Z",
                false,
                waypoints
        );
    }

    private static Tour buildDaNangHoiAnTour() {
        List<Tour.Waypoint> waypoints = Arrays.asList(
                buildWaypoint("Cầu Rồng - Đà Nẵng", "Xem cầu phun lửa phun nước", 0, 16.0610, 108.2277),
                buildWaypoint("Bà Nà Hills", "Tham quan Cầu Vàng", 35, 15.9977, 107.9911),
                buildWaypoint("Phố cổ Hội An", "Dạo phố đèn lồng buổi tối", 5, 15.8801, 108.3380),
                buildWaypoint("Bãi biển An Bàng", "Tắm biển, thư giãn", 0, 15.9333, 108.3439)
        );
        return buildTour(
                "mock-tour-2",
                "Đà Nẵng - Hội An 2N1Đ",
                "Biển, cầu Vàng và phố cổ",
                null,
                "Ongoing",
                "2026-08-05T00:00:00.000Z",
                "2026-08-07T00:00:00.000Z",
                true,
                waypoints
        );
    }

    private static Tour buildPhuQuocTour() {
        List<Tour.Waypoint> waypoints = Arrays.asList(
                buildWaypoint("Bãi Sao - Phú Quốc", "Tắm biển nước trong xanh", 0, 10.0206, 104.0148),
                buildWaypoint("Vinpearl Safari", "Tham quan vườn thú bán hoang dã", 30, 10.3574, 103.8608),
                buildWaypoint("Cáp treo Hòn Thơm", "Ngắm toàn cảnh biển từ cáp treo", 25, 10.0447, 104.0217),
                buildWaypoint("Chợ đêm Dinh Cậu", "Ăn hải sản, mua quà lưu niệm", 15, 10.2202, 103.9605),
                buildWaypoint("Nhà tù Phú Quốc", "Tham quan di tích lịch sử", 0, 10.1875, 104.0055)
        );
        return buildTour(
                "mock-tour-3",
                "Phú Quốc Biển Đảo 4N3Đ",
                "Đảo ngọc với biển xanh và hải sản tươi ngon",
                null,
                "Completed",
                "2026-07-01T00:00:00.000Z",
                "2026-07-04T00:00:00.000Z",
                true,
                waypoints
        );
    }

    private static Tour buildMienTayTour() {
        List<Tour.Waypoint> waypoints = Arrays.asList(
                buildWaypoint("Chợ nổi Cái Răng - Cần Thơ", "Đi thuyền ngắm chợ nổi buổi sáng sớm", 0, 10.0247, 105.7469),
                buildWaypoint("Vườn trái cây Vĩnh Long", "Tham quan miệt vườn, ăn trái cây tại chỗ", 20, 10.2397, 105.9722),
                buildWaypoint("Cồn Phụng - Bến Tre", "Tham quan làng nghề kẹo dừa", 15, 10.3670, 106.3757),
                buildWaypoint("Chùa Vĩnh Tràng - Tiền Giang", "Tham quan ngôi chùa cổ", 0, 10.3600, 106.3606),
                buildWaypoint("Làng hoa Sa Đéc - Đồng Tháp", "Dạo vườn hoa, chụp ảnh", 10, 10.2908, 105.7601),
                buildWaypoint("Núi Sam - Châu Đốc", "Viếng miếu Bà Chúa Xứ", 5, 10.6960, 105.1180),
                buildWaypoint("Rừng tràm Trà Sư - An Giang", "Đi xuồng ba lá xuyên rừng tràm", 25, 10.5808, 105.0350),
                buildWaypoint("Thạch Động - Hà Tiên", "Tham quan hang động, ngắm biên giới", 0, 10.3830, 104.4780)
        );
        return buildTour(
                "mock-tour-4",
                "Miền Tây sông nước 5N4Đ",
                "Hành trình khám phá miệt vườn và chợ nổi Đồng bằng sông Cửu Long",
                null,
                "Upcoming",
                "2026-09-01T00:00:00.000Z",
                "2026-09-05T00:00:00.000Z",
                false,
                waypoints
        );
    }

    private static Tour buildTayNguyenTour() {
        List<Tour.Waypoint> waypoints = Arrays.asList(
                buildWaypoint("Hồ Xuân Hương - Đà Lạt", "Dạo bộ quanh hồ trung tâm thành phố", 0, 11.9404, 108.4411),
                buildWaypoint("Thác Datanla - Đà Lạt", "Trải nghiệm máng trượt xuống thác", 20, 11.8894, 108.4453),
                buildWaypoint("Đồi chè Cầu Đất - Đà Lạt", "Chụp ảnh đồi chè, tham quan nhà máy trà", 10, 11.8167, 108.5167),
                buildWaypoint("Hồ Lắk - Đắk Lắk", "Đi thuyền độc mộc trên hồ", 15, 12.4167, 108.1667),
                buildWaypoint("Buôn Đôn - Đắk Lắk", "Tham quan làng voi, cầu treo", 20, 12.9167, 107.8167),
                buildWaypoint("Thác Dray Nur - Đắk Lắk", "Ngắm thác nước lớn nhất Tây Nguyên", 10, 12.6167, 108.1167),
                buildWaypoint("Biển Hồ Chè - Gia Lai", "Ngắm hồ nước giữa rừng thông", 0, 14.0833, 108.2500)
        );
        return buildTour(
                "mock-tour-5",
                "Tây Nguyên đại ngàn 4N3Đ",
                "Chuyến đi khám phá cao nguyên, thác nước và văn hóa Tây Nguyên",
                null,
                "Ongoing",
                "2026-08-20T00:00:00.000Z",
                "2026-08-23T00:00:00.000Z",
                true,
                waypoints
        );
    }

    private static Tour buildTour(String id, String title, String description, String imageUrl,
                                   String status, String startDate, String endDate, boolean isShared,
                                   List<Tour.Waypoint> waypoints) {
        Tour tour = new Tour();
        tour.setId(id);
        tour.setTitle(title);
        tour.setDescription(description);
        tour.setImageUrl(imageUrl);
        tour.setVideoUrl(null);
        tour.setStatus(status);
        tour.setStartDate(startDate);
        tour.setEndDate(endDate);
        tour.setShared(isShared);
        tour.setAuthorId("mock-user-1");

        Tour.UserDetails author = new Tour.UserDetails();
        author.setDisplayName("Mock User");
        author.setPhotoUrl(null);
        tour.setAuthor(author);

        tour.setWaypoints(new ArrayList<>(waypoints));
        return tour;
    }

    private static Tour.Waypoint buildWaypoint(String locationName, String note, int price, double lat, double lng) {
        Tour.Waypoint wp = new Tour.Waypoint();
        wp.setLocationName(locationName);
        wp.setNote(note);
        wp.setPrice(price);
        wp.setPhotos(new ArrayList<>());

        Tour.Coordinate coordinate = new Tour.Coordinate();
        coordinate.setCoordinates(Arrays.asList(lng, lat));
        wp.setCoordinate(coordinate);

        return wp;
    }
}
