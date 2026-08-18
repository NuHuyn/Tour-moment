package com.example.mycurrenttour;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GisHelper {

    public static class MockLocation {
        private final String locationName;
        private final double latitude;
        private final double longitude;

        public MockLocation(String locationName, double latitude, double longitude) {
            this.locationName = locationName;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public String getLocationName() {
            return locationName;
        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }
    }

    private static final List<MockLocation> MOCK_LOCATIONS = Arrays.asList(
            new MockLocation("Hồ Gươm - Hà Nội", 21.0285, 105.8542),
            new MockLocation("Cầu Rồng - Đà Nẵng", 16.0610, 108.2277),
            new MockLocation("Phố cổ Hội An - Quảng Nam", 15.8801, 108.3380),
            new MockLocation("Bãi Sao - Phú Quốc", 10.0206, 104.0148),
            new MockLocation("Dinh Độc Lập - TP.HCM", 10.7769, 106.6953),
            new MockLocation("Thác Datanla - Đà Lạt", 11.8894, 108.4453),
            new MockLocation("Vịnh Hạ Long - Quảng Ninh", 20.9101, 107.1824)
    );

    /**
     * Get a mock location by index. If the index exceeds the list size,
     * it wraps around using modulo to prevent out of bounds.
     */
    public static MockLocation getMockLocationForIndex(int index) {
        if (MOCK_LOCATIONS.isEmpty()) {
            return new MockLocation("Mock Location", 0.0, 0.0);
        }
        int safeIndex = Math.max(0, index) % MOCK_LOCATIONS.size();
        return MOCK_LOCATIONS.get(safeIndex);
    }

    /**
     * Standardizes coordinates to GeoJSON format: [longitude, latitude].
     */
    public static List<Double> swapToGeoJsonCoordinates(double latitude, double longitude) {
        return Arrays.asList(longitude, latitude);
    }
}
