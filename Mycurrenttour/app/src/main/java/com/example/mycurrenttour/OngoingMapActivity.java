package com.example.mycurrenttour;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

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

        // Receive tour safely
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
            Toast.makeText(this, "Congratulations! Journey point completed.", Toast.LENGTH_SHORT).show();
        });
    }

    private void initViews() {
        map = findViewById(R.id.mapOngoing);
        txtTitle = findViewById(R.id.txtOngoingTitle);
        recyclerWaypoints = findViewById(R.id.recyclerOngoingWaypoints);
    }

    private void setupMap() {
        // High stability mode for Emulator
        map.setLayerType(View.LAYER_TYPE_SOFTWARE, null); 
        map.setMultiTouchControls(true); 
        map.getController().setZoom(14.0); 
        
        initRouteOnMap();
    }

    private void setupWaypointList() {
        if (tour.getWaypoints() == null) return;
        
        recyclerWaypoints.setLayoutManager(new LinearLayoutManager(this));
        adapter = new WaypointViewAdapter(tour.getWaypoints(), true, position -> {
            // Draw specific road segment from My Location/Previous Stop to current stop
            // routePoints: [0] MyLocation, [1] Step 1, [2] Step 2...
            if (position + 1 < routePoints.size()) {
                drawStepRoad(position, position + 1);
                map.getController().animateTo(routePoints.get(position + 1));
            }
        });
        recyclerWaypoints.setAdapter(adapter);
    }

    private void displayTourInfo() {
        if (tour != null && txtTitle != null) {
            txtTitle.setText(tour.getTitle());
        }
    }

    private void initRouteOnMap() {
        // Use your fixed coordinates as the start
        GeoPoint startPoint = new GeoPoint(10.870587770354202, 106.80209416657385);
        routePoints.clear();
        routePoints.add(startPoint);

        // Add Marker for starting position
        Marker startMarker = new Marker(map);
        startMarker.setPosition(startPoint);
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        startMarker.setTitle("My Location");
        map.getOverlays().add(startMarker);

        // Add all stops from tour
        if (tour.getWaypoints() != null) {
            for (int i = 0; i < tour.getWaypoints().size(); i++) {
                Tour.Waypoint wp = tour.getWaypoints().get(i);
                if (wp.getCoordinate() != null && wp.getCoordinate().getCoordinates() != null) {
                    List<Double> coords = wp.getCoordinate().getCoordinates();
                    GeoPoint stopPoint = new GeoPoint(coords.get(1), coords.get(0));
                    routePoints.add(stopPoint);

                    Marker m = new Marker(map);
                    m.setPosition(stopPoint);
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                    m.setTitle("Step " + (i + 1) + ": " + wp.getLocationName());
                    map.getOverlays().add(m);
                }
            }
        }

        // Draw the full meandering road initially
        drawFullDetailedRoad();
        map.getController().setCenter(startPoint);
    }

    private void drawFullDetailedRoad() {
        if (routePoints.size() < 2) return;
        new Thread(() -> {
            try {
                RoadManager roadManager = new OSRMRoadManager(getApplicationContext(), getPackageName());
                Road road = roadManager.getRoad(new ArrayList<>(routePoints));
                if (road.mStatus == Road.STATUS_OK) {
                    Polyline roadOverlay = RoadManager.buildRoadOverlay(road);
                    roadOverlay.getOutlinePaint().setColor(Color.RED);
                    roadOverlay.getOutlinePaint().setStrokeWidth(10f);

                    runOnUiThread(() -> {
                        if (map != null) {
                            map.getOverlays().add(roadOverlay);
                            map.invalidate();
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void drawStepRoad(int startIdx, int endIdx) {
        // Highlight current chặng in Blue
        new Thread(() -> {
            try {
                RoadManager roadManager = new OSRMRoadManager(getApplicationContext(), getPackageName());
                ArrayList<GeoPoint> points = new ArrayList<>();
                points.add(routePoints.get(startIdx));
                points.add(routePoints.get(endIdx));

                Road road = roadManager.getRoad(points);
                if (road.mStatus == Road.STATUS_OK) {
                    Polyline stepOverlay = RoadManager.buildRoadOverlay(road);
                    stepOverlay.getOutlinePaint().setColor(Color.BLUE);
                    stepOverlay.getOutlinePaint().setStrokeWidth(14f);

                    runOnUiThread(() -> {
                        if (map != null) {
                            // Remove previous blue lines if any
                            map.getOverlays().removeIf(o -> o instanceof Polyline && ((Polyline) o).getOutlinePaint().getColor() == Color.BLUE);
                            map.getOverlays().add(stepOverlay);
                            map.invalidate();
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    protected void onResume() { super.onResume(); if (map != null) map.onResume(); }
    @Override
    protected void onPause() { super.onPause(); if (map != null) map.onPause(); }
}
