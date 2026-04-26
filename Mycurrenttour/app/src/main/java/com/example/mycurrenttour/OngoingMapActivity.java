package com.example.mycurrenttour;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.osmdroid.bonuspack.routing.OSRMRoadManager;
import org.osmdroid.bonuspack.routing.Road;
import org.osmdroid.bonuspack.routing.RoadManager;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;

public class OngoingMapActivity extends AppCompatActivity {

    private MapView map;
    private Tour tour;
    private TextView txtTitle;
    private RecyclerView recyclerWaypoints;
    private WaypointViewAdapter adapter;
    private List<GeoPoint> routePoints = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_ongoing_map);

        tour = (Tour) getIntent().getSerializableExtra("tour_item");
        if (tour == null) {
            Toast.makeText(this, "Tour data not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupMap();
        setupWaypointList();
        displayTourInfo();

        findViewById(R.id.btnBackFromMap).setOnClickListener(v -> finish());
        findViewById(R.id.btnCompleteStep).setOnClickListener(v -> {
            Toast.makeText(this, "Congratulations! You have completed this journey.", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void initViews() {
        map = findViewById(R.id.mapOngoing);
        txtTitle = findViewById(R.id.txtOngoingTitle);
        recyclerWaypoints = findViewById(R.id.recyclerOngoingWaypoints);
    }

    private void setupMap() {
        // --- ULTRA LIGHTWEIGHT CONFIG FOR EMULATOR ---
        map.setLayerType(View.LAYER_TYPE_SOFTWARE, null); 
        map.setMultiTouchControls(true); 
        map.getController().setZoom(13.0); 
        
        initStaticRoute();
    }

    private void setupWaypointList() {
        recyclerWaypoints.setLayoutManager(new LinearLayoutManager(this));
        // Pass 'true' for isOngoing
        adapter = new WaypointViewAdapter(tour.getWaypoints(), true, position -> {
            // routePoints index 0 is My Location, index 1 is Waypoint 1, etc.
            if (position + 1 < routePoints.size()) {
                drawStepRoute(position, position + 1);
                map.getController().animateTo(routePoints.get(position + 1));
            }
        });
        recyclerWaypoints.setAdapter(adapter);
    }

    private void displayTourInfo() {
        if (tour != null) {
            txtTitle.setText(tour.getTitle());
        }
    }

    private void initStaticRoute() {
        // 1. Fixed position (Starting point)
        GeoPoint startPoint = new GeoPoint(10.870587770354202, 106.80209416657385);
        routePoints.clear();
        routePoints.add(startPoint);

        // Marker for my location
        Marker startMarker = new Marker(map);
        startMarker.setPosition(startPoint);
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        startMarker.setTitle("My Location");
        map.getOverlays().add(startMarker);

        // 2. Add all stops and collect points
        if (tour.getWaypoints() != null) {
            for (int i = 0; i < tour.getWaypoints().size(); i++) {
                Tour.Waypoint wp = tour.getWaypoints().get(i);
                if (wp.getCoordinate() != null) {
                    GeoPoint wpPoint = new GeoPoint(
                            wp.getCoordinate().getCoordinates().get(1),
                            wp.getCoordinate().getCoordinates().get(0)
                    );
                    routePoints.add(wpPoint);

                    Marker m = new Marker(map);
                    m.setPosition(wpPoint);
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                    m.setTitle("Step " + (i + 1) + ": " + wp.getLocationName());
                    map.getOverlays().add(m);
                }
            }
        }

        // 3. Draw the full detailed road initially
        drawFullRoute();

        map.getController().setCenter(startPoint);
        map.invalidate();
    }

    private void drawFullRoute() {
        if (routePoints.size() < 2) return;
        new Thread(() -> {
            try {
                RoadManager roadManager = new OSRMRoadManager(this, getPackageName());
                Road road = roadManager.getRoad(new ArrayList<>(routePoints));
                if (road.mStatus == Road.STATUS_OK) {
                    Polyline roadOverlay = RoadManager.buildRoadOverlay(road);
                    roadOverlay.getOutlinePaint().setColor(Color.RED);
                    roadOverlay.getOutlinePaint().setStrokeWidth(10f);

                    runOnUiThread(() -> {
                        map.getOverlays().add(roadOverlay);
                        map.invalidate();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void drawStepRoute(int startIndex, int endIndex) {
        // Clear previous lines
        map.getOverlays().removeIf(overlay -> overlay instanceof Polyline);
        
        new Thread(() -> {
            try {
                RoadManager roadManager = new OSRMRoadManager(this, getPackageName());
                ArrayList<GeoPoint> stepPoints = new ArrayList<>();
                stepPoints.add(routePoints.get(startIndex));
                stepPoints.add(routePoints.get(endIndex));

                Road road = roadManager.getRoad(stepPoints);
                if (road.mStatus == Road.STATUS_OK) {
                    Polyline stepOverlay = RoadManager.buildRoadOverlay(road);
                    stepOverlay.getOutlinePaint().setColor(Color.BLUE); // Highlight current step in Blue
                    stepOverlay.getOutlinePaint().setStrokeWidth(14f);

                    runOnUiThread(() -> {
                        map.getOverlays().add(stepOverlay);
                        map.invalidate();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        map.onPause();
    }
}
