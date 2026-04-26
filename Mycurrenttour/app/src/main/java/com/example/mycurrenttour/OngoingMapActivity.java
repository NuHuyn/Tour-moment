package com.example.mycurrenttour;

import android.Manifest;
import android.content.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.osmdroid.bonuspack.routing.OSRMRoadManager;
import org.osmdroid.bonuspack.routing.Road;
import org.osmdroid.bonuspack.routing.RoadManager;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;

public class OngoingMapActivity extends AppCompatActivity {

    private MapView map;
    private MyLocationNewOverlay mLocationOverlay;
    private Tour tour;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_ongoing_map);

        tour = (Tour) getIntent().getSerializableExtra("tour_item");
        if (tour == null) {
            Toast.makeText(this, "Không tìm thấy dữ liệu tour", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        map = findViewById(R.id.mapOngoing);
        map.setMultiTouchControls(true);
        map.getController().setZoom(15.0);

        initLocationOverlay();

        findViewById(R.id.btnBackFromMap).setOnClickListener(v -> finish());
    }

    private void initLocationOverlay() {
        mLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), map);
        mLocationOverlay.enableMyLocation();
        mLocationOverlay.enableFollowLocation();
        map.getOverlays().add(mLocationOverlay);

        // Chờ vị trí GPS được xác định rồi vẽ đường đi
        mLocationOverlay.runOnFirstFix(() -> {
            GeoPoint startPoint = mLocationOverlay.getMyLocation();
            if (startPoint != null && tour.getWaypoints() != null && !tour.getWaypoints().isEmpty()) {
                Tour.Waypoint firstWp = tour.getWaypoints().get(0);
                if (firstWp.getCoordinate() != null) {
                    GeoPoint endPoint = new GeoPoint(
                            firstWp.getCoordinate().getCoordinates().get(1),
                            firstWp.getCoordinate().getCoordinates().get(0)
                    );
                    drawRoute(startPoint, endPoint);
                }
            }
        });
    }

    private void drawRoute(GeoPoint start, GeoPoint end) {
        new Thread(() -> {
            RoadManager roadManager = new OSRMRoadManager(this, getPackageName());
            ArrayList<GeoPoint> waypoints = new ArrayList<>();
            waypoints.add(start);
            waypoints.add(end);

            Road road = roadManager.getRoad(waypoints);
            Polyline roadOverlay = RoadManager.buildRoadOverlay(road);
            roadOverlay.setColor(Color.RED);
            roadOverlay.setWidth(12f);

            runOnUiThread(() -> {
                map.getOverlays().add(roadOverlay);
                
                Marker destMarker = new Marker(map);
                destMarker.setPosition(end);
                destMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                destMarker.setTitle("Điểm dừng đầu tiên");
                map.getOverlays().add(destMarker);
                
                map.getController().animateTo(start);
                map.invalidate();
            });
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
        if (mLocationOverlay != null) mLocationOverlay.enableMyLocation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        map.onPause();
        if (mLocationOverlay != null) mLocationOverlay.disableMyLocation();
    }
}
