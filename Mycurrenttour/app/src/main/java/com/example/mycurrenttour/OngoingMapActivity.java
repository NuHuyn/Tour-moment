package com.example.mycurrenttour;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import org.osmdroid.bonuspack.routing.OSRMRoadManager;
import org.osmdroid.bonuspack.routing.Road;
import org.osmdroid.bonuspack.routing.RoadManager;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OngoingMapActivity extends AppCompatActivity {

    private MapView map;
    private Tour tour;
    private TextView txtTitle;
    private RecyclerView recyclerWaypoints;
    private WaypointViewAdapter adapter;
    private List<GeoPoint> routePoints = new ArrayList<>();
    private View frameUnlockFull;
    private MaterialButton btnUnlockFull;
    private TextView badgeUnlockDiscount;

    // Demo waypoint-lock feature (chưa gắn cổng thanh toán thật, chỉ mô phỏng UI cho báo cáo đồ án).
    // Trạng thái khóa lấy từ WaypointLockManager (persist qua SharedPreferences, dùng chung với
    // TourDetailActivity/Discovery) chứ không tự tính lại từ đầu mỗi lần mở màn nữa.
    // TODO: thay bằng logic khóa dựa trên thanh toán thật khi có backend.
    private Set<Integer> lockedWaypoints = new HashSet<>();

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
        // Demo waypoint-lock: nút "Unlock" mở hết toàn bộ waypoint còn khóa (full trip, giảm 25%).
        btnUnlockFull.setOnClickListener(v -> showFullUnlockDialog());
    }

    private void initViews() {
        map = findViewById(R.id.mapOngoing);
        txtTitle = findViewById(R.id.txtOngoingTitle);
        recyclerWaypoints = findViewById(R.id.recyclerOngoingWaypoints);
        frameUnlockFull = findViewById(R.id.frameUnlockFull);
        btnUnlockFull = findViewById(R.id.btnUnlockFull);
        badgeUnlockDiscount = findViewById(R.id.badgeUnlockDiscount);
        styleDiscountBadge(badgeUnlockDiscount);
    }

    /** Demo discount badge: vẽ nền chip đỏ bo tròn hoàn toàn bằng code, không dùng ảnh/icon ngoài. */
    private void styleDiscountBadge(TextView badge) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(Color.parseColor("#E53935"));
        bg.setCornerRadius(999f); // giá trị lớn -> luôn ra dạng chip oval bất kể kích thước chữ
        badge.setBackground(bg);
    }

    private void setupMap() {
        map.setLayerType(View.LAYER_TYPE_SOFTWARE, null); 
        map.setMultiTouchControls(true); 
        map.getController().setZoom(14.0); 
        
        initRouteOnMap();
    }

    private void setupWaypointList() {
        if (tour.getWaypoints() == null) return;

        recyclerWaypoints.setLayoutManager(new LinearLayoutManager(this));
        adapter = new WaypointViewAdapter(tour.getWaypoints(), true, new WaypointViewAdapter.OnWaypointClickListener() {
            @Override
            public void onNavigateClick(int position) {
                // Chỉ được gọi khi step đã mở (adapter tự chặn khi đang khóa).
                if (position + 1 < routePoints.size()) {
                    drawStepRoad(position, position + 1);
                    map.getController().animateTo(routePoints.get(position + 1));
                }
            }

            @Override
            public void onLockClick(int position) {
                showStepUnlockDialog(position);
            }
        });
        recyclerWaypoints.setAdapter(adapter);

        refreshLockedState();
    }

    /** TODO: demo waypoint-lock - đọc lại trạng thái khóa đã lưu (persist theo tourId) và refresh UI. */
    private void refreshLockedState() {
        if (tour == null || tour.getWaypoints() == null) return;
        lockedWaypoints = WaypointLockManager.getLockedPositions(this, tour.getId(), tour.getWaypoints().size());
        if (adapter != null) adapter.setLockedPositions(lockedWaypoints);

        // Nút "Unlock" + badge "-25%" chỉ hiện khi còn step khóa; hết khóa thì ẩn luôn cả 2 (đã unlock hết, không còn gì để bán).
        boolean hasLocked = !lockedWaypoints.isEmpty();
        frameUnlockFull.setVisibility(hasLocked ? View.VISIBLE : View.GONE);
        btnUnlockFull.setEnabled(hasLocked);
    }

    /**
     * TODO: demo waypoint-lock - dialog thanh toán giả lập (chưa gọi API thanh toán thật, không validate gì).
     * Mở đúng waypoint tại vị trí đã bấm icon ổ khóa, giá cố định 2.000đ/step.
     */
    private void showStepUnlockDialog(int position) {
        if (tour == null || tour.getWaypoints() == null) return;
        int price = WaypointLockManager.stepPriceVnd();

        showPayDialog("Pay " + price + " vnd to unlock", () -> {
            WaypointLockManager.unlockWaypoint(this, tour.getId(), position);
            refreshLockedState();
            Toast.makeText(this, "Payment successful! Step " + (position + 1) + " unlocked.", Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * TODO: demo waypoint-lock - dialog thanh toán giả lập (chưa gọi API thanh toán thật, không validate gì).
     * Mở hết toàn bộ waypoint còn khóa cùng lúc, giá giảm 25% so với mua lẻ.
     */
    private void showFullUnlockDialog() {
        if (lockedWaypoints.isEmpty() || tour == null || tour.getWaypoints() == null) return;
        int totalWaypoints = tour.getWaypoints().size();
        int price = WaypointLockManager.fullUnlockPriceVnd(lockedWaypoints.size());

        showPayDialog("Pay " + price + " vnd to unlock", () -> {
            WaypointLockManager.unlockAll(this, tour.getId(), totalWaypoints);
            refreshLockedState();
            Toast.makeText(this, "Payment successful! All waypoints unlocked.", Toast.LENGTH_SHORT).show();
        });
    }

    /** Dialog Pay dùng chung cho cả 2 trường hợp mở 1 step lẻ và mở full trip - khác nhau ở amountText + onPaid. */
    private void showPayDialog(String amountText, Runnable onPaid) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_unlock_waypoint, null);
        TextView txtAmount = dialogView.findViewById(R.id.txtUnlockAmount);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancelUnlock);
        MaterialButton btnPay = dialogView.findViewById(R.id.btnPay);

        txtAmount.setText(amountText);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnPay.setOnClickListener(v -> {
            // Demo: không gọi API thanh toán thật, không validate gì - coi như thành công ngay lập tức.
            onPaid.run();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void displayTourInfo() {
        if (tour != null && txtTitle != null) {
            txtTitle.setText(tour.getTitle());
        }
    }

    private void initRouteOnMap() {
        GeoPoint startPoint = new GeoPoint(10.870587770354202, 106.80209416657385);
        routePoints.clear();
        routePoints.add(startPoint);

        Marker startMarker = new Marker(map);
        startMarker.setPosition(startPoint);
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        startMarker.setTitle("My Location");
        map.getOverlays().add(startMarker);

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
