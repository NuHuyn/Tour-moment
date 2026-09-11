package com.example.mycurrenttour;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.squareup.picasso.Picasso;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class OngoingMapActivity extends AppCompatActivity {

    private MapView map;
    private Tour tour;
    private RecyclerView recyclerStops;
    private RouteStopAdapter adapter;
    private LinearLayout layoutStopDots;
    private TextView txtRouteProgressLabel;
    private View frameUnlockFull;
    private MaterialButton btnUnlockFull;
    private TextView badgeUnlockDiscount;
    private BottomSheetBehavior<View> sheetBehavior;

    // On-demand route info pill (Feature 1) - hidden until a route is actually drawn.
    private View cardRouteInfo;
    private TextView txtRouteDistance, txtRouteDuration;

    /** One GeoPoint per tour.getWaypoints() entry, 1:1 by index - used for marker placement and
     *  camera centering. No longer includes a leading "my location" entry (that point is now
     *  dynamic/asynchronous, fetched fresh only when the user actually taps a stop - see
     *  Feature 1's "does NOT auto-trigger on load" requirement). */
    private final List<GeoPoint> waypointPoints = new ArrayList<>();
    private final List<Marker> stopMarkers = new ArrayList<>();

    // Live user location, fetched on-demand (never auto-fetched on screen load) - null until the
    // first successful fix.
    private GeoPoint myLocation;
    private Marker myLocationMarker;

    // The single on-demand route currently drawn, if any - "per-waypoint, never more than one
    // route visible at once" (Feature 1.4): tapping a different unlocked stop clears this first.
    private Polyline activeRoutePolyline;
    private int activeRouteTargetIndex = -1;
    private static final int ACTIVE_ROUTE_COLOR = Color.parseColor("#1B5E20");

    // Permission was requested from a specific stop tap - resumed here once the result comes back,
    // so "tap stop -> grant permission" ends in a drawn route instead of the user having to tap
    // again.
    private int pendingNavigationTargetIndex = -1;
    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted && pendingNavigationTargetIndex != -1) {
                    int target = pendingNavigationTargetIndex;
                    pendingNavigationTargetIndex = -1;
                    resolveLocationThenRoute(target);
                } else {
                    pendingNavigationTargetIndex = -1;
                    Toast.makeText(this, "Cần quyền truy cập vị trí để chỉ đường tới điểm này.", Toast.LENGTH_LONG).show();
                }
            });

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
        configureTileCache();
        setContentView(R.layout.activity_ongoing_map);

        tour = (Tour) getIntent().getSerializableExtra("tour_item");
        if (tour == null) {
            Toast.makeText(this, "Tour data not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        prefetchStopPhotos(tour);
        initViews();
        setupBottomSheet();
        setupMap();
        setupStopCards();
        setupDots();
        refreshLockedState();

        // The floating back arrow over the map is the only back control now - the bottom action
        // row's redundant outlined "Back" button was removed (Unlock Now is the sole action left).
        findViewById(R.id.btnBackFromMap).setOnClickListener(v -> finish());
        findViewById(R.id.btnRecenter).setOnClickListener(v -> recenterMap());
        findViewById(R.id.btnZoomIn).setOnClickListener(v -> map.getController().zoomIn());
        findViewById(R.id.btnZoomOut).setOnClickListener(v -> map.getController().zoomOut());
        findViewById(R.id.btnClearRoute).setOnClickListener(v -> clearActiveRoute());
        // Demo waypoint-lock: nút "Unlock Now" mở hết toàn bộ waypoint còn khóa (full trip, giảm 25%).
        btnUnlockFull.setOnClickListener(v -> showFullUnlockDialog());
    }

    /** Cache-warming prefetch (no target view) for the first few stop cards' photos, called
     *  before setupStopCards() ever inflates the RecyclerView - same rationale as
     *  TourDetailActivity#prefetchImages. Locked waypoints are skipped (RouteStopAdapter never
     *  loads their real photo anyway, always the blurred generic placeholder). */
    private void prefetchStopPhotos(Tour tour) {
        if (tour.getWaypoints() == null) return;
        int count = 0;
        for (Tour.Waypoint wp : tour.getWaypoints()) {
            if (count >= 4) break;
            if (wp.isLocked()) continue;
            if (wp.getPhotos() != null && !wp.getPhotos().isEmpty() && !wp.getPhotos().get(0).isEmpty()) {
                Picasso.get().load(wp.getPhotos().get(0)).fetch();
                count++;
            }
        }
    }

    private void initViews() {
        map = findViewById(R.id.mapOngoing);
        recyclerStops = findViewById(R.id.recyclerRouteStops);
        layoutStopDots = findViewById(R.id.layoutStopDots);
        txtRouteProgressLabel = findViewById(R.id.txtRouteProgressLabel);
        frameUnlockFull = findViewById(R.id.frameUnlockFull);
        btnUnlockFull = findViewById(R.id.btnUnlockFull);
        badgeUnlockDiscount = findViewById(R.id.badgeUnlockDiscount);
        cardRouteInfo = findViewById(R.id.cardRouteInfo);
        txtRouteDistance = findViewById(R.id.txtRouteDistance);
        txtRouteDuration = findViewById(R.id.txtRouteDuration);
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

    /**
     * Tile caching/throughput tuning - must run before the MapView is created (osmdroid only
     * picks up base-path/tile-cache-dir changes at MapView construction time).
     *
     * Root-caused two things here, not just guessed:
     * 1. This app never called Configuration.getInstance().load(context, prefs) or set an
     *    explicit base path, so osmdroid resolved its tile cache directory via the no-Context
     *    overload of getOsmdroidBasePath() - which osmdroid's own docs flag as unreliable on
     *    API 29+ scoped storage. Passing this Activity's real external-files dir guarantees a
     *    directory that's always writable and actually persists between sessions, so previously
     *    seen areas stop re-downloading every time instead of using the disk cache osmdroid
     *    already has on by default (600MB, unrelated to this bug).
     * 2. osmdroid's default tileDownloadThreads is 2 - a courtesy limit for OSM's shared,
     *    volunteer-run tile servers (see OSM's tile usage policy), not a limit this app's own
     *    paid Mapbox account needs to respect. That's the direct cause of tiles visibly trickling
     *    in one at a time instead of a screenful arriving together.
     */
    private void configureTileCache() {
        File externalDir = getExternalFilesDir(null);
        File baseDir = new File(externalDir != null ? externalDir : getFilesDir(), "osmdroid");
        Configuration.getInstance().setOsmdroidBasePath(baseDir);
        Configuration.getInstance().setOsmdroidTileCache(new File(baseDir, "tiles"));

        Configuration.getInstance().setTileDownloadThreads((short) 6);
        Configuration.getInstance().setTileDownloadMaxQueueSize((short) 40);
        // More tiles kept decoded in memory (default 9) - fewer disk re-reads/re-decodes when
        // panning back over recently-seen tiles.
        Configuration.getInstance().setCacheMapTileCount((short) 18);
        // Trust the disk cache for a full week regardless of whatever cache headers Mapbox's
        // raster tile endpoint sends back, so a previously-visited area never re-fetches over
        // the network at all within that window.
        Configuration.getInstance().setExpirationOverrideDuration(7L * 24 * 60 * 60 * 1000L);
    }

    private void setupMap() {
        map.setTileSource(MAPBOX_TILE_SOURCE);
        map.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        map.setMultiTouchControls(true);
        map.getController().setZoom(14.0);

        // Just places markers - no route line, no ETA, nothing drawn until the user explicitly
        // taps a specific waypoint (Feature 1.1). Previously this auto-drew a full multi-stop
        // route through every unlocked waypoint on load; that's gone in favor of the on-demand,
        // single-destination navigation below.
        placeMarkers();
    }

    private void setupStopCards() {
        if (tour.getWaypoints() == null) return;

        recyclerStops.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        adapter = new RouteStopAdapter(tour.getWaypoints(), new RouteStopAdapter.OnStopClickListener() {
            @Override
            public void onStopClick(int position) {
                navigateToWaypoint(position);
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
        if (myLocation != null) {
            map.getController().animateTo(myLocation);
            map.getController().setZoom(14.0);
        } else if (!waypointPoints.isEmpty()) {
            map.getController().animateTo(waypointPoints.get(0));
            map.getController().setZoom(14.0);
        }
    }

    /** Recomputes lock/progress state straight from the tour data currently in memory (server is
     *  the source of truth for isLocked() - see Tour.Waypoint) and redraws markers accordingly.
     *  Called once on load and again after refetchTourAndRefresh() following a successful unlock. */
    private void refreshLockedState() {
        if (tour == null || tour.getWaypoints() == null) return;
        if (adapter != null) adapter.updateWaypoints(tour.getWaypoints());
        updateProgressLabel();
        placeMarkers();

        int lockedCount = 0;
        List<Tour.Waypoint> lockedWaypoints = new ArrayList<>();
        for (Tour.Waypoint wp : tour.getWaypoints()) {
            if (wp.isLocked()) { lockedCount++; lockedWaypoints.add(wp); }
        }
        // Nút "Unlock Now" + badge "-25%" chỉ hiện khi còn step khóa; hết khóa thì ẩn luôn cả 2.
        frameUnlockFull.setVisibility(lockedCount > 0 ? View.VISIBLE : View.GONE);
        btnUnlockFull.setEnabled(lockedCount > 0);
    }

    /** "Bạn đang ở điểm X/Y" - X = số điểm đã mở, Y = tổng số điểm. */
    private void updateProgressLabel() {
        if (tour.getWaypoints() == null || txtRouteProgressLabel == null) return;
        int total = tour.getWaypoints().size();
        int locked = 0;
        for (Tour.Waypoint wp : tour.getWaypoints()) if (wp.isLocked()) locked++;
        int unlocked = total - locked;
        txtRouteProgressLabel.setText(String.format(Locale.getDefault(), "Bạn đang ở điểm %d/%d", unlocked, total));
    }

    /**
     * Demo paywall dialog (chưa gắn cổng thanh toán thật, không validate gì) - "Pay" calls the
     * real server-side unlock endpoint though, so the record actually persists per-device (see
     * WaypointLockManager). Amount shown is that waypoint's real price from the server, not a
     * hardcoded constant.
     */
    private void showStepUnlockDialog(int position) {
        if (tour == null || tour.getWaypoints() == null) return;
        Tour.Waypoint wp = tour.getWaypoints().get(position);
        if (!wp.isLocked()) return; // stale tap after it was already unlocked elsewhere
        int price = wp.getPrice();

        showPayDialog("Pay " + price + " vnd to unlock", () ->
                WaypointLockManager.unlockWaypoint(this, tour.getId(), position, new WaypointLockManager.UnlockCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(OngoingMapActivity.this,
                                "Payment successful! Step " + (position + 1) + " unlocked.", Toast.LENGTH_SHORT).show();
                        refetchTourAndRefresh();
                    }

                    @Override
                    public void onFailure(String message) {
                        Toast.makeText(OngoingMapActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                }));
    }

    /** Same idea as showStepUnlockDialog but for every currently-locked waypoint at once, at a
     *  25%-off bundle price (real per-waypoint prices from the server, not a hardcoded constant). */
    private void showFullUnlockDialog() {
        if (tour == null || tour.getWaypoints() == null) return;
        List<Tour.Waypoint> lockedWaypoints = new ArrayList<>();
        for (Tour.Waypoint wp : tour.getWaypoints()) if (wp.isLocked()) lockedWaypoints.add(wp);
        if (lockedWaypoints.isEmpty()) return;
        int price = WaypointLockManager.fullUnlockPriceVnd(lockedWaypoints);

        showPayDialog("Pay " + price + " vnd to unlock", () ->
                WaypointLockManager.unlockAllWaypoints(this, tour.getId(), new WaypointLockManager.UnlockCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(OngoingMapActivity.this, "Payment successful! All waypoints unlocked.", Toast.LENGTH_SHORT).show();
                        refetchTourAndRefresh();
                    }

                    @Override
                    public void onFailure(String message) {
                        Toast.makeText(OngoingMapActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                }));
    }

    /** Re-fetches this tour from the server (real data for whatever this device just unlocked -
     *  the Tour object already in memory still holds the redacted placeholder for it) and rebinds
     *  the stop cards + markers. */
    private void refetchTourAndRefresh() {
        String deviceId = DeviceIdProvider.getOrCreate(this);
        ApiService api = ApiClient.getClient().create(ApiService.class);
        api.getTourById(tour.getId(), deviceId).enqueue(new retrofit2.Callback<Tour>() {
            @Override
            public void onResponse(retrofit2.Call<Tour> call, retrofit2.Response<Tour> response) {
                if (response.isSuccessful() && response.body() != null) {
                    tour.setWaypoints(response.body().getWaypoints());
                    refreshLockedState();
                } else {
                    Toast.makeText(OngoingMapActivity.this, "Đã mở khóa, nhưng không tải lại được dữ liệu mới.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(retrofit2.Call<Tour> call, Throwable t) {
                Toast.makeText(OngoingMapActivity.this, "Đã mở khóa, nhưng không tải lại được dữ liệu mới.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Dialog Pay dùng chung - khác nhau ở amountText + onPaid. */
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
            // Demo: không gọi cổng thanh toán thật, không validate gì - coi như thành công ngay,
            // nhưng onPaid ở trên vẫn gọi API unlock thật để server ghi nhận cho đúng thiết bị.
            onPaid.run();
            dialog.dismiss();
        });

        dialog.show();
    }

    // ===================== FEATURE 1: on-demand per-waypoint navigation =====================

    /** Tapped an unlocked stop card. Gets a fresh location fix (requesting permission first if
     *  needed) and draws a driving route from there to exactly this one waypoint - nothing is
     *  drawn until this is called, and calling it again for a different stop clears whatever was
     *  drawn before (see clearActiveRoute()). */
    private void navigateToWaypoint(int position) {
        if (tour == null || tour.getWaypoints() == null || position >= tour.getWaypoints().size()) return;
        Tour.Waypoint wp = tour.getWaypoints().get(position);
        if (wp.isLocked()) return; // defense in depth - the adapter shouldn't even call this for a locked stop

        if (position == activeRouteTargetIndex) {
            // Tapping the same already-routed stop again - just recenter, don't refetch/redraw.
            if (activeRoutePolyline != null) map.getController().animateTo(waypointPoints.get(position));
            return;
        }

        if (!LocationHelper.hasLocationPermission(this)) {
            pendingNavigationTargetIndex = position;
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }
        resolveLocationThenRoute(position);
    }

    /** Shown in the route-info pill while a fix is being acquired - LocationHelper's own
     *  HIGH_ACCURACY_TIMEOUT_MS (12s) bounds how long this can stay up before it falls back to a
     *  cached location or gives up, so this never hangs indefinitely with no feedback. */
    private void showLocationLoading() {
        txtRouteDistance.setText("Đang xác định vị trí...");
        txtRouteDuration.setText("");
        cardRouteInfo.setVisibility(View.VISIBLE);
    }

    private void resolveLocationThenRoute(int position) {
        showLocationLoading();
        LocationHelper.getCurrentLocation(this, new LocationHelper.LocationCallback() {
            @Override
            public void onLocation(double lat, double lng, boolean approximate) {
                myLocation = new GeoPoint(lat, lng);
                placeMyLocationMarker();
                if (approximate) {
                    // Timed out on a fresh high-accuracy fix, or none was available - routing off
                    // a cached (possibly old/wrong) location, so the user should know the route
                    // might not start from where they actually are right now.
                    Toast.makeText(OngoingMapActivity.this,
                            "Không lấy được vị trí chính xác - dùng vị trí gần đây nhất, có thể không đúng.",
                            Toast.LENGTH_LONG).show();
                }
                fetchAndDrawRoute(position);
            }

            @Override
            public void onUnavailable(String message) {
                cardRouteInfo.setVisibility(View.GONE);
                Toast.makeText(OngoingMapActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void fetchAndDrawRoute(int position) {
        if (myLocation == null || position >= waypointPoints.size()) return;
        GeoPoint dest = waypointPoints.get(position);

        MapboxDirectionsClient.fetchRoute(myLocation.getLatitude(), myLocation.getLongitude(),
                dest.getLatitude(), dest.getLongitude(), new MapboxDirectionsClient.RouteCallback() {
                    @Override
                    public void onRouteReady(MapboxDirectionsClient.Route route) {
                        runOnUiThread(() -> {
                            clearActiveRoute();
                            drawRoute(route);
                            activeRouteTargetIndex = position;
                            showRouteInfo(route.distanceMeters, route.durationSeconds);
                            map.getController().animateTo(dest);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            cardRouteInfo.setVisibility(View.GONE); // clear the "Đang xác định vị trí..." pill
                            Toast.makeText(OngoingMapActivity.this, message, Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    private void drawRoute(MapboxDirectionsClient.Route route) {
        Polyline polyline = new Polyline();
        polyline.setPoints(route.geometry);
        polyline.getOutlinePaint().setColor(ACTIVE_ROUTE_COLOR);
        polyline.getOutlinePaint().setStrokeWidth(9f);
        map.getOverlays().add(polyline);
        activeRoutePolyline = polyline;
        map.invalidate();
    }

    /** Clears whatever on-demand route is currently drawn - called before drawing a new one (only
     *  ever one route visible at a time, per Feature 1.4) and by the "✕" button. */
    private void clearActiveRoute() {
        if (activeRoutePolyline != null) {
            map.getOverlays().remove(activeRoutePolyline);
            map.invalidate();
            activeRoutePolyline = null;
        }
        activeRouteTargetIndex = -1;
        cardRouteInfo.setVisibility(View.GONE);
    }

    private void showRouteInfo(double distanceMeters, double durationSeconds) {
        String distanceText = distanceMeters >= 1000
                ? String.format(Locale.getDefault(), "%.1f km", distanceMeters / 1000.0)
                : String.format(Locale.getDefault(), "%.0f m", distanceMeters);

        int totalMinutes = (int) Math.round(durationSeconds / 60.0);
        String durationText = totalMinutes >= 60
                ? String.format(Locale.getDefault(), "%d giờ %d phút", totalMinutes / 60, totalMinutes % 60)
                : String.format(Locale.getDefault(), "%d phút", totalMinutes);

        txtRouteDistance.setText(distanceText);
        txtRouteDuration.setText(durationText);
        cardRouteInfo.setVisibility(View.VISIBLE);
    }

    // ===================== Markers =====================

    /** (Re)draws every waypoint marker from scratch - a numbered green pin for a free/unlocked
     *  stop at its real coordinate, or a muted "mystery" pin for a still-locked stop at the
     *  approximate/fuzzed coordinate the server already substituted (see waypointVisibility.js;
     *  the client never sees the real one to begin with, so there's nothing to additionally hide
     *  here beyond the marker's own look). Does not touch the active route or "my location"
     *  marker. */
    private void placeMarkers() {
        for (Marker m : stopMarkers) map.getOverlays().remove(m);
        stopMarkers.clear();
        waypointPoints.clear();

        if (tour.getWaypoints() == null) return;
        for (int i = 0; i < tour.getWaypoints().size(); i++) {
            Tour.Waypoint wp = tour.getWaypoints().get(i);
            if (wp.getCoordinate() == null || wp.getCoordinate().getCoordinates() == null) continue;

            List<Double> coords = wp.getCoordinate().getCoordinates();
            GeoPoint stopPoint = new GeoPoint(coords.get(1), coords.get(0));
            waypointPoints.add(stopPoint);

            Marker m = new Marker(map);
            m.setPosition(stopPoint);
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            boolean locked = wp.isLocked();
            m.setTitle(locked ? "Điểm chưa mở khóa" : "Step " + (i + 1) + ": " + wp.getLocationName());
            m.setIcon(locked ? mysteryPinDrawable() : numberedPinDrawable(i + 1));
            map.getOverlays().add(m);
            stopMarkers.add(m);
        }
        map.invalidate();

        if (!waypointPoints.isEmpty() && myLocation == null) {
            map.getController().setCenter(waypointPoints.get(0));
        }
    }

    private void placeMyLocationMarker() {
        if (myLocation == null) return;
        if (myLocationMarker != null) map.getOverlays().remove(myLocationMarker);
        myLocationMarker = new Marker(map);
        myLocationMarker.setPosition(myLocation);
        myLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        myLocationMarker.setTitle("My Location");
        myLocationMarker.setIcon(currentLocationDrawable());
        map.getOverlays().add(myLocationMarker);
        map.invalidate();
    }

    /** Small filled dot with a white ring - the "My Location" marker, placed only once a real fix
     *  comes back (Feature 1.1) - deliberately plain/small so it doesn't compete visually with the
     *  numbered stop pins. */
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
        String label = number < 10 ? "0" + number : String.valueOf(number);
        return pinDrawable("#2E7D32", label, false);
    }

    /** Muted gray teardrop pin with a small "?" - a still-locked waypoint's approximate/fuzzed
     *  location (Feature 2): visible enough to say "there's something here" without the numbered,
     *  fully-confident look of an unlocked stop. */
    private Drawable mysteryPinDrawable() {
        return pinDrawable("#9E9E9E", "?", true);
    }

    private Drawable pinDrawable(String colorHex, String label, boolean dashedRing) {
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
        pinPaint.setColor(Color.parseColor(colorHex));
        pinPaint.setStyle(Paint.Style.FILL);
        if (dashedRing) pinPaint.setAlpha(200); // slightly translucent - "approximate", not exact

        float cx = w / 2f;
        float cy = radius + density;

        Path pinPath = new Path();
        pinPath.addCircle(cx, cy, radius, Path.Direction.CW);
        pinPath.moveTo(cx - radius * 0.55f, cy + radius * 0.7f);
        pinPath.lineTo(cx + radius * 0.55f, cy + radius * 0.7f);
        pinPath.lineTo(cx, h - density);
        pinPath.close();
        canvas.drawPath(pinPath, pinPaint);

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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() { super.onResume(); if (map != null) map.onResume(); }
    @Override
    protected void onPause() { super.onPause(); if (map != null) map.onPause(); }
}
