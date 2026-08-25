package com.example.mycurrenttour;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;

import org.osmdroid.bonuspack.routing.OSRMRoadManager;
import org.osmdroid.bonuspack.routing.Road;
import org.osmdroid.bonuspack.routing.RoadManager;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class OngoingMapActivity extends AppCompatActivity {

    private MapView map;
    private Tour tour;
    private RecyclerView recyclerStops;
    private RouteStopAdapter adapter;
    private LinearLayout layoutStopDots;
    private TextView txtRouteProgressLabel;
    private List<GeoPoint> routePoints = new ArrayList<>();
    private View frameUnlockFull;
    private MaterialButton btnUnlockFull;
    private TextView badgeUnlockDiscount;
    private BottomSheetBehavior<View> sheetBehavior;

    // Fixed "My Location" start point used by both initRouteOnMap() (full stop list/markers) and
    // buildUnlockedRoutePoints() (paywall-aware route line) - kept as one field so both stay in
    // sync instead of two copies of the same literal coordinate drifting apart.
    private final GeoPoint myLocationStart = new GeoPoint(10.870587770354202, 106.80209416657385);
    // Outline color of the main dashed route line, so refreshLockedState() can find-and-remove
    // just that overlay (not the lighter-green per-step highlight from drawStepRoad) before
    // redrawing it with the current unlock state.
    private static final int MAIN_ROUTE_COLOR = Color.parseColor("#1B5E20");

    // Demo waypoint-lock feature (chưa gắn cổng thanh toán thật, chỉ mô phỏng UI cho báo cáo đồ án).
    // Trạng thái khóa lấy từ WaypointLockManager (persist qua SharedPreferences, dùng chung với
    // TourDetailActivity/Discovery) chứ không tự tính lại từ đầu mỗi lần mở màn nữa.
    // TODO: thay bằng logic khóa dựa trên thanh toán thật khi có backend.
    private Set<Integer> lockedWaypoints = new HashSet<>();

    // Mapbox raster tiles - replaces osmdroid's default TileSourceFactory.MAPNIK, which points
    // straight at tile.openstreetmap.org. That server is OSMF's volunteer-run demo endpoint and is
    // explicitly not for production app traffic per their tile usage policy; using it here is what
    // caused the "Access blocked" 403s. (MapTiler was tried first but its key/account never
    // resolved; switched to Mapbox's free tier, verified working with a real rendered tile before
    // wiring in.) Uses Mapbox's current Styles API tile endpoint - the older v4 classic endpoint
    // returns 410 Gone, Mapbox retired it. XYTileSource appends mImageFilenameEnding right after
    // {y}, so "?access_token=..." rides along on every tile request with no custom TileSource
    // subclass needed.
    private static final XYTileSource MAPBOX_TILE_SOURCE = new XYTileSource(
            "MapboxStreets",
            0, 20, 256,
            "?access_token=" + BuildConfig.MAPBOX_ACCESS_TOKEN,
            new String[]{"https://api.mapbox.com/styles/v1/mapbox/streets-v12/tiles/256/"},
            "© Mapbox © OpenStreetMap contributors"
    );

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
        setupBottomSheet();
        setupMap();
        setupStopCards();
        setupDots();

        // The floating back arrow over the map is the only back control now - the bottom action
        // row's redundant outlined "Back" button was removed (Unlock Now is the sole action left).
        findViewById(R.id.btnBackFromMap).setOnClickListener(v -> finish());
        findViewById(R.id.btnRecenter).setOnClickListener(v -> recenterMap());
        findViewById(R.id.btnZoomIn).setOnClickListener(v -> map.getController().zoomIn());
        findViewById(R.id.btnZoomOut).setOnClickListener(v -> map.getController().zoomOut());
        // Demo waypoint-lock: nút "Unlock Now" mở hết toàn bộ waypoint còn khóa (full trip, giảm 25%).
        btnUnlockFull.setOnClickListener(v -> showFullUnlockDialog());
    }

    private void initViews() {
        map = findViewById(R.id.mapOngoing);
        recyclerStops = findViewById(R.id.recyclerRouteStops);
        layoutStopDots = findViewById(R.id.layoutStopDots);
        txtRouteProgressLabel = findViewById(R.id.txtRouteProgressLabel);
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

    /** Draggable bottom sheet: STATE_EXPANDED shows the full content (default), STATE_COLLAPSED
     *  peeks just the drag handle so dragging down actually reveals the map underneath, and
     *  BottomSheetBehavior's own gesture handling gives the spring/snap-to-nearest-state behavior
     *  on release for free - no manual animation code needed. */
    private void setupBottomSheet() {
        View sheet = findViewById(R.id.sheetRoute);
        sheetBehavior = BottomSheetBehavior.from(sheet);
        sheetBehavior.setHideable(false);
        sheetBehavior.setDraggable(true);
        sheetBehavior.setSkipCollapsed(false);
        // A bit taller than just the handle's own height so the touchable peek strip sits clear
        // of the screen's very bottom edge, where system gesture-nav (swipe-up-for-home) can
        // otherwise steal the drag before the sheet ever sees it.
        sheetBehavior.setPeekHeight(dp(52));
        sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);

        // The collapsed peek strip sits right at the screen's bottom edge, exactly where
        // gesture-nav (swipe-up-for-home) listens - without this, a user's drag-up-to-expand can
        // get stolen by the system before the sheet ever sees it. Claiming the sheet's own bounds
        // as a gesture-exclusion rect (re-evaluated by the system on every layout pass, so it
        // tracks the sheet as it moves) hands drags starting on it to the app instead. No-op
        // below API 29, which is fine - older gesture-nav didn't have this edge-swipe behavior.
        sheet.post(() -> ViewCompat.setSystemGestureExclusionRects(sheet,
                Collections.singletonList(new Rect(0, 0, sheet.getWidth(), sheet.getHeight()))));
    }

    private void setupMap() {
        map.setTileSource(MAPBOX_TILE_SOURCE);
        map.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        map.setMultiTouchControls(true);
        map.getController().setZoom(14.0);

        initRouteOnMap();
    }

    private void setupStopCards() {
        if (tour.getWaypoints() == null) return;

        recyclerStops.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        adapter = new RouteStopAdapter(tour.getWaypoints(), new RouteStopAdapter.OnStopClickListener() {
            @Override
            public void onStopClick(int position) {
                // Unlocked card tapped - center the map on that stop and highlight the leg leading to it.
                if (position + 1 < routePoints.size()) {
                    if (position > 0) drawStepRoad(position, position + 1);
                    map.getController().animateTo(routePoints.get(position + 1));
                }
            }

            @Override
            public void onLockClick(int position) {
                showStepUnlockDialog(position);
            }
        });
        recyclerStops.setAdapter(adapter);

        recyclerStops.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@androidx.annotation.NonNull RecyclerView rv, int dx, int dy) {
                updateActiveDot(currentCenteredPosition());
            }
        });

        refreshLockedState();
    }

    /**
     * Bug fix: this used to be findFirstVisibleItemPosition(), which returns the leftmost item
     * that has ANY pixel on screen - including a card just barely peeking in from the padded
     * start edge (recyclerRouteStops uses paddingStart/paddingEnd + clipToPadding=false so
     * neighboring cards always peek). After scrolling to the last 2-3 stops, the previous stop's
     * sliver on the left was still technically "first visible", so the dot indicator stayed stuck
     * around the middle instead of advancing to the stops actually in view. Finding the card whose
     * center is closest to the RecyclerView's own center matches what the user is actually looking
     * at, the same way a ViewPager's currentPage works. */
    private int currentCenteredPosition() {
        if (recyclerStops.getChildCount() == 0) return 0;
        int rvCenterX = recyclerStops.getWidth() / 2;
        int bestPosition = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < recyclerStops.getChildCount(); i++) {
            View child = recyclerStops.getChildAt(i);
            int adapterPos = recyclerStops.getChildAdapterPosition(child);
            if (adapterPos == RecyclerView.NO_POSITION) continue;
            int childCenterX = (child.getLeft() + child.getRight()) / 2;
            int distance = Math.abs(childCenterX - rvCenterX);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestPosition = adapterPos;
            }
        }
        return bestPosition;
    }

    /** One dot per stop under the card row - filled dark green for the current scroll position. */
    private void setupDots() {
        if (tour.getWaypoints() == null) return;
        layoutStopDots.removeAllViews();
        int count = tour.getWaypoints().size();
        int dotSize = dp(8);
        for (int i = 0; i < count; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dotSize, dotSize);
            lp.setMarginStart(dp(3));
            lp.setMarginEnd(dp(3));
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(i == 0 ? R.drawable.bg_dot_active : R.drawable.bg_dot_inactive);
            layoutStopDots.addView(dot);
        }
    }

    private void updateActiveDot(int activeIndex) {
        for (int i = 0; i < layoutStopDots.getChildCount(); i++) {
            layoutStopDots.getChildAt(i).setBackgroundResource(
                    i == activeIndex ? R.drawable.bg_dot_active : R.drawable.bg_dot_inactive);
        }
    }

    private void recenterMap() {
        if (!routePoints.isEmpty()) {
            map.getController().animateTo(routePoints.get(0));
            map.getController().setZoom(14.0);
        }
    }

    /** TODO: demo waypoint-lock - đọc lại trạng thái khóa đã lưu (persist theo tourId) và refresh UI. */
    private void refreshLockedState() {
        if (tour == null || tour.getWaypoints() == null) return;
        lockedWaypoints = WaypointLockManager.getLockedPositions(this, tour.getId(), tour.getWaypoints().size());
        if (adapter != null) adapter.setLockedPositions(lockedWaypoints);
        updateProgressLabel();
        redrawMarkers();
        redrawMainRoute();

        // Nút "Unlock Now" + badge "-25%" chỉ hiện khi còn step khóa; hết khóa thì ẩn luôn cả 2 (đã unlock hết, không còn gì để bán).
        boolean hasLocked = !lockedWaypoints.isEmpty();
        frameUnlockFull.setVisibility(hasLocked ? View.VISIBLE : View.GONE);
        btnUnlockFull.setEnabled(hasLocked);
    }

    /** "Bạn đang ở điểm X/Y" - X = số điểm đã mở, Y = tổng số điểm. */
    private void updateProgressLabel() {
        if (tour.getWaypoints() == null || txtRouteProgressLabel == null) return;
        int total = tour.getWaypoints().size();
        int unlocked = total - lockedWaypoints.size();
        txtRouteProgressLabel.setText(String.format(Locale.getDefault(), "Bạn đang ở điểm %d/%d", unlocked, total));
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

    private List<Marker> stopMarkers = new ArrayList<>();

    private void initRouteOnMap() {
        GeoPoint startPoint = myLocationStart;
        routePoints.clear();
        routePoints.add(startPoint);

        Marker startMarker = new Marker(map);
        startMarker.setPosition(startPoint);
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        startMarker.setTitle("My Location");
        // Explicit small dot icon - osmdroid's bundled default marker drawable is a large
        // hand-pointer-style pin that reads as a stray cursor once the map is full-bleed, so it
        // needs its own icon just like the numbered stop pins below.
        startMarker.setIcon(currentLocationDrawable());
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
                    m.setIcon(numberedPinDrawable(i + 1));
                    map.getOverlays().add(m);
                    stopMarkers.add(m);
                }
            }
        }

        // Only route through unlocked stops - drawing the road all the way to a locked/paywalled
        // waypoint would show its location for free on the map even though the stop card itself
        // is locked, defeating the paywall.
        drawFullDetailedRoad(buildUnlockedRoutePoints());
        map.getController().setCenter(startPoint);
    }

    /** Start point + every currently-unlocked waypoint, in order, skipping locked ones (which can
     *  leave gaps - unlockWaypoint() lets a specific step be paid for individually, not just as a
     *  contiguous prefix). Queries WaypointLockManager directly rather than the lockedWaypoints
     *  field so it's correct even the very first time it's called from initRouteOnMap(), before
     *  refreshLockedState() has run. Deliberately a separate list from routePoints, which keeps
     *  one entry per waypoint regardless of lock state - onStopClick/drawStepRoad/recenterMap all
     *  index into routePoints assuming that 1:1 correspondence with tour.getWaypoints(). */
    private List<GeoPoint> buildUnlockedRoutePoints() {
        List<GeoPoint> points = new ArrayList<>();
        points.add(myLocationStart);
        if (tour.getWaypoints() != null) {
            for (int i = 0; i < tour.getWaypoints().size(); i++) {
                if (!WaypointLockManager.isUnlocked(this, tour.getId(), i)) continue;
                Tour.Waypoint wp = tour.getWaypoints().get(i);
                if (wp.getCoordinate() != null && wp.getCoordinate().getCoordinates() != null) {
                    List<Double> coords = wp.getCoordinate().getCoordinates();
                    points.add(new GeoPoint(coords.get(1), coords.get(0)));
                }
            }
        }
        return points;
    }

    /** Re-fetches and redraws the main route line after a lock-state change, so unlocking a
     *  waypoint (individually or via "Unlock full") extends the visible path to it. Removes the
     *  previous main-route overlay first (matched by MAIN_ROUTE_COLOR, same pattern drawStepRoad
     *  already uses for its own highlight overlay) so unlocks don't stack multiple stale routes
     *  on top of each other. */
    private void redrawMainRoute() {
        if (map == null) return;
        map.getOverlays().removeIf(o -> o instanceof Polyline && ((Polyline) o).getOutlinePaint().getColor() == MAIN_ROUTE_COLOR);
        map.invalidate();
        drawFullDetailedRoad(buildUnlockedRoutePoints());
    }

    /** Re-icons the numbered pins after a lock-state change (kept plain green for now - the
     *  lock/unlock distinction lives on the stop cards, not the map pins, per the design spec). */
    private void redrawMarkers() {
        map.invalidate();
    }

    /** Small filled dot with a white ring - the "My Location" start-point marker. Deliberately
     *  plain/small so it doesn't compete visually with the numbered stop pins. */
    private Drawable currentLocationDrawable() {
        float density = getResources().getDisplayMetrics().density * 2f;
        int size = Math.round(22 * density);
        float radius = size / 2f - density;

        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(Math.round(getResources().getDisplayMetrics().densityDpi * 2f));
        Canvas canvas = new Canvas(bitmap);
        float c = size / 2f;

        Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(Color.parseColor("#1B5E20"));
        dotPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(c, c, radius, dotPaint);

        Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setColor(Color.WHITE);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(density * 1.5f);
        canvas.drawCircle(c, c, radius - density * 0.75f, ringPaint);

        return new BitmapDrawable(getResources(), bitmap);
    }

    /** Draws a green teardrop map pin with a bold white stop number inside, matching the
     *  "green pin with white number badge" look in the design spec - built purely on a Canvas so
     *  no extra image asset is needed per stop count. Rendered at 2x the target dp size (then
     *  BitmapDrawable scales it back down) so the digits stay crisp instead of blocky/aliased.
     *  Zero-padded by hand (not String.format's locale-sensitive %d) so the glyph is always a
     *  plain ASCII "0"-"9", regardless of the device's default locale/numbering system. */
    private Drawable numberedPinDrawable(int number) {
        // Supersample at 2x, then tell the Bitmap its density is 2x the real one so
        // BitmapDrawable scales it back down to the intended 40x52dp footprint on screen -
        // without this the pin would render twice too big.
        float baseDensity = getResources().getDisplayMetrics().density;
        float density = baseDensity * 2f;
        int w = Math.round(40 * density);
        int h = Math.round(52 * density);
        float radius = w / 2f - density;

        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(Math.round(getResources().getDisplayMetrics().densityDpi * 2f));
        Canvas canvas = new Canvas(bitmap);

        Paint pinPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pinPaint.setColor(Color.parseColor("#2E7D32"));
        pinPaint.setStyle(Paint.Style.FILL);

        float cx = w / 2f;
        float cy = radius + density;

        Path pinPath = new Path();
        pinPath.addCircle(cx, cy, radius, Path.Direction.CW);
        pinPath.moveTo(cx - radius * 0.55f, cy + radius * 0.7f);
        pinPath.lineTo(cx + radius * 0.55f, cy + radius * 0.7f);
        pinPath.lineTo(cx, h - density);
        pinPath.close();
        canvas.drawPath(pinPath, pinPaint);

        String label = number < 10 ? "0" + number : String.valueOf(number);
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(15 * density);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textY = cy - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(label, cx, textY, textPaint);

        return new BitmapDrawable(getResources(), bitmap);
    }

    private void drawFullDetailedRoad(List<GeoPoint> points) {
        if (points.size() < 2) return;
        new Thread(() -> {
            try {
                RoadManager roadManager = new OSRMRoadManager(getApplicationContext(), getPackageName());
                Road road = roadManager.getRoad(new ArrayList<>(points));
                if (road.mStatus == Road.STATUS_OK) {
                    Polyline roadOverlay = RoadManager.buildRoadOverlay(road);
                    roadOverlay.getOutlinePaint().setColor(MAIN_ROUTE_COLOR);
                    roadOverlay.getOutlinePaint().setStrokeWidth(9f);
                    roadOverlay.getOutlinePaint().setPathEffect(new DashPathEffect(new float[]{22f, 16f}, 0));

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
                    int highlightColor = Color.parseColor("#66BB6A");
                    stepOverlay.getOutlinePaint().setColor(highlightColor);
                    stepOverlay.getOutlinePaint().setStrokeWidth(12f);

                    runOnUiThread(() -> {
                        if (map != null) {
                            map.getOverlays().removeIf(o -> o instanceof Polyline && ((Polyline) o).getOutlinePaint().getColor() == highlightColor);
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() { super.onResume(); if (map != null) map.onResume(); }
    @Override
    protected void onPause() { super.onPause(); if (map != null) map.onPause(); }
}
