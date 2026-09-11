package com.example.mycurrenttour;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * On-demand single-destination routing via Mapbox's Directions API (driving profile) - reuses the
 * same MAPBOX_ACCESS_TOKEN already wired into BuildConfig for OngoingMapActivity's map tiles, no
 * extra setup needed. Separate from the app's other routing path (OSRMRoadManager, still used
 * elsewhere for the between-two-already-unlocked-stops highlight) since this one is specifically
 * "from wherever the user actually is right now, to exactly one tapped waypoint" - a live
 * point-to-point request, not a pre-planned multi-stop road.
 *
 * Plain OkHttp + org.json rather than Retrofit - this is one-off GET request to a fixed external
 * host, not part of the app's own backend API surface that ApiService/Retrofit models.
 */
public final class MapboxDirectionsClient {

    private static final String TAG = "MapboxDirections";

    /** Confirmed with the user: "driving" - standard car-routing profile, appropriate for Đắk
     *  Lắk's rural highland roads. (driving-traffic was considered but rejected: it costs more
     *  and rural Central Highlands has sparse/unreliable live-traffic data coverage.) */
    private static final String PROFILE = "driving";
    private static final OkHttpClient client = new OkHttpClient();

    public interface RouteCallback {
        void onRouteReady(Route route);
        void onError(String message);
    }

    public static class Route {
        public final List<GeoPoint> geometry;
        public final double distanceMeters;
        public final double durationSeconds;

        Route(List<GeoPoint> geometry, double distanceMeters, double durationSeconds) {
            this.geometry = geometry;
            this.distanceMeters = distanceMeters;
            this.durationSeconds = durationSeconds;
        }
    }

    private MapboxDirectionsClient() {}

    /** Fetches a driving route from (fromLat, fromLng) to (toLat, toLng). Callback is invoked off
     *  the main thread (OkHttp's own callback thread) - callers must post back to the UI thread.
     *  Coordinates go into the URL as "lng,lat;lng,lat" - Mapbox's expected order, the reverse of
     *  Android's Location (lat, lng). */
    public static void fetchRoute(double fromLat, double fromLng, double toLat, double toLng, RouteCallback callback) {
        String coordinates = String.format(Locale.US, "%f,%f;%f,%f", fromLng, fromLat, toLng, toLat);
        String url = String.format(Locale.US,
                "https://api.mapbox.com/directions/v5/mapbox/%s/%s?geometries=geojson&overview=full&access_token=%s",
                PROFILE, coordinates, BuildConfig.MAPBOX_ACCESS_TOKEN);

        Log.d(TAG, "Requesting route: profile=" + PROFILE + " from=(" + fromLat + "," + fromLng
                + ") to=(" + toLat + "," + toLng + ")");

        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Network failure calling Directions API", e);
                callback.onError("Không thể kết nối để chỉ đường: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response r = response) {
                    String body = r.body() != null ? r.body().string() : "";

                    if (!r.isSuccessful()) {
                        // Full status + body, always logged - not just a generic toast (this is
                        // what caught "Route exceeds maximum distance limitation" under a bare
                        // "mã lỗi 422" before).
                        Log.e(TAG, "Directions API error: HTTP " + r.code() + " body=" + body);
                        callback.onError(describeError(r.code(), body));
                        return;
                    }

                    Route route = parseRoute(body);
                    if (route == null) {
                        Log.e(TAG, "Directions API returned no usable route: HTTP " + r.code() + " body=" + body);
                        callback.onError("Không tìm thấy tuyến đường phù hợp.");
                    } else {
                        callback.onRouteReady(route);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse Directions API response", e);
                    callback.onError("Lỗi xử lý dữ liệu chỉ đường: " + e.getMessage());
                }
            }
        });
    }

    /** Turns Mapbox's {"code": "...", "message": "..."} error body into a specific, user-facing
     *  Vietnamese message where the cause is known, falling back to a generic one with the raw
     *  status code otherwise (never just a bare code with no context). */
    private static String describeError(int httpCode, String body) {
        String mapboxCode = null;
        String mapboxMessage = null;
        try {
            JSONObject json = new JSONObject(body);
            mapboxCode = json.optString("code", null);
            mapboxMessage = json.optString("message", null);
        } catch (Exception ignored) {
            // Body wasn't JSON (e.g. an upstream proxy error page) - fall through to the generic message.
        }

        if ("InvalidInput".equals(mapboxCode) && mapboxMessage != null && mapboxMessage.contains("maximum distance")) {
            return "Điểm đến quá xa vị trí hiện tại của bạn để chỉ đường bằng ô tô.";
        }
        if (httpCode == 401 || httpCode == 403) {
            return "Không thể chỉ đường: token Mapbox không hợp lệ hoặc không có quyền (mã lỗi " + httpCode + ")";
        }
        if (mapboxMessage != null && !mapboxMessage.isEmpty()) {
            return "Không tìm được đường đi: " + mapboxMessage;
        }
        return "Không tìm được đường đi (mã lỗi " + httpCode + ")";
    }

    private static Route parseRoute(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray routes = root.optJSONArray("routes");
        if (routes == null || routes.length() == 0) return null;

        JSONObject firstRoute = routes.getJSONObject(0);
        double distance = firstRoute.optDouble("distance", 0);
        double duration = firstRoute.optDouble("duration", 0);

        JSONArray coords = firstRoute.getJSONObject("geometry").getJSONArray("coordinates");
        List<GeoPoint> points = new ArrayList<>(coords.length());
        for (int i = 0; i < coords.length(); i++) {
            JSONArray pair = coords.getJSONArray(i);
            double lng = pair.getDouble(0);
            double lat = pair.getDouble(1);
            points.add(new GeoPoint(lat, lng));
        }
        return new Route(points, distance, duration);
    }
}
