package com.example.mycurrenttour;

import android.graphics.Color;
import android.media.MediaPlayer; // THÊM DÒNG NÀY
import android.os.Bundle;
import android.os.Handler; // THÊM DÒNG NÀY
import android.os.Looper; // THÊM DÒNG NÀY
import android.view.View; // THÊM DÒNG NÀY
import android.widget.ImageButton; // THÊM DÒNG NÀY
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2; // THÊM DÒNG NÀY

import com.squareup.picasso.Picasso;

import org.osmdroid.bonuspack.routing.OSRMRoadManager;
import org.osmdroid.bonuspack.routing.Road;
import org.osmdroid.bonuspack.routing.RoadManager;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TourDetailActivity extends AppCompatActivity {

    private ImageView imgTour;
    private ViewPager2 viewPagerSlideshow; // THÊM DÒNG NÀY
    private ImageButton btnPlaySlideshow; // THÊM DÒNG NÀY

    private LinearLayout btnWholeRoute;
    private TextView txtTitle, txtPrice, txtDescription;
    private TextView txtIdDetail, txtStartDetail, txtEndDetail, txtLocationDetail, txtDurationDetail;

    private RecyclerView recyclerWaypoints;
    private WaypointViewAdapter waypointAdapter;
    private MapView mapView;

    private List<GeoPoint> routePoints = new ArrayList<>();
    private List<Tour.Waypoint> tourWaypoints = new ArrayList<>();

    // Biến cho Slideshow
    private List<String> photos = new ArrayList<>();
    private Handler sliderHandler = new Handler(Looper.getMainLooper());
    private MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_tour_detail);

        initViews();

        Tour tour = (Tour) getIntent().getSerializableExtra("tour_item");
        if (tour != null) {
            this.tourWaypoints = tour.getWaypoints();
            setupMapConfig();
            displayTourData(tour);
            setupWaypointList();

            // Chuẩn bị danh sách ảnh cho slideshow
            preparePhotos(tour);

            // Sự kiện nhấn nút Play Video
            btnPlaySlideshow.setOnClickListener(v -> startInternalSlideshow());
        } else {
            Toast.makeText(this, "Invalid data!", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initViews() {
        imgTour = findViewById(R.id.imgTourDetail);
        viewPagerSlideshow = findViewById(R.id.viewPagerDetailSlideshow); // Khai báo trong XML
        btnPlaySlideshow = findViewById(R.id.btnPlaySlideshow); // Khai báo trong XML

        txtTitle = findViewById(R.id.txtTitleDetail);
        txtPrice = findViewById(R.id.txtPriceDetail);
        txtDescription = findViewById(R.id.txtDescription);

        btnWholeRoute = findViewById(R.id.btnWholeRoute);
        mapView = findViewById(R.id.mapView);
        txtIdDetail = findViewById(R.id.txtIdDetail);
        txtStartDetail = findViewById(R.id.txtStartDetail);
        txtEndDetail = findViewById(R.id.txtEndDetail);
        txtLocationDetail = findViewById(R.id.txtLocationDetail);
        txtDurationDetail = findViewById(R.id.txtDurationDetail);
        recyclerWaypoints = findViewById(R.id.recyclerWaypointsDetail);
    }

    private void preparePhotos(Tour tour) {
        photos.clear();
        if (tour.getImageUrl() != null) photos.add(tour.getImageUrl());
        for (Tour.Waypoint wp : tour.getWaypoints()) {
            if (wp.getPhotos() != null) photos.addAll(wp.getPhotos());
        }
    }

    private void startInternalSlideshow() {
        if (photos.isEmpty()) {
            Toast.makeText(this, "No photos to play video!", Toast.LENGTH_SHORT).show();
            return;
        }

        // KHÔNG dùng imgTour.setVisibility(View.GONE) nữa để ảnh bìa luôn hiển thị ở dưới
        // Bạn có thể giảm độ mờ (alpha) của ảnh bìa một chút để slideshow nổi bật hơn (tùy chọn)
        imgTour.setAlpha(0.6f);

        // Chỉ ẩn nút Play và hiện ViewPager2 lên trên
        btnPlaySlideshow.setVisibility(View.GONE);
        viewPagerSlideshow.setVisibility(View.VISIBLE);

        SlideshowAdapter adapter = new SlideshowAdapter(photos);
        viewPagerSlideshow.setAdapter(adapter);

        // HIỆU ỨNG CHUYỂN MƯỢT KHÔNG KHOẢNG CÁCH (Cross-fade)
        viewPagerSlideshow.setPageTransformer((page, position) -> {
            page.setTranslationX(-position * page.getWidth()); // Giữ các ảnh đè lên nhau tại một vị trí

            if (position < -1 || position > 1) {
                page.setAlpha(0f);
            } else {
                // Khi chuyển, ảnh cũ mờ dần (alpha giảm) và ảnh mới hiện lên (alpha tăng)
                page.setAlpha(1 - Math.abs(position));
            }
        });

        // Rút ngắn thời gian chuyển để tạo cảm giác liên tục
        initMusic();
        sliderHandler.postDelayed(sliderRunnable, 2500);
    }

    private Runnable sliderRunnable = new Runnable() {
        @Override
        public void run() {
            if (viewPagerSlideshow.getAdapter() != null) {
                int nextItem = (viewPagerSlideshow.getCurrentItem() + 1) % photos.size();
                viewPagerSlideshow.setCurrentItem(nextItem, true);
                sliderHandler.postDelayed(this, 3000);
            }
        }
    };

    private void initMusic() {
        if (mediaPlayer != null) mediaPlayer.release();
        mediaPlayer = MediaPlayer.create(this, R.raw.background_music);
        if (mediaPlayer != null) {
            mediaPlayer.setLooping(true);
            mediaPlayer.start();
        }
    }

    private void displayTourData(Tour tour) {
        txtTitle.setText(tour.getTitle());
        txtDescription.setText(tour.getDescription());
        txtIdDetail.setText(tour.getId());
        txtPrice.setText("Total expense: $" + tour.getTotalPrice());

        Picasso.get().load(tour.getImageUrl())
                .placeholder(R.drawable.centralvietnam)
                .error(R.drawable.centralvietnam)
                .into(imgTour);

        txtStartDetail.setText(formatDate(tour.getStartDateString()));
        txtEndDetail.setText(formatDate(tour.getEndDateString()));

        if (tourWaypoints != null && !tourWaypoints.isEmpty()) {
            txtLocationDetail.setText(tourWaypoints.get(0).getLocationName());
            routePoints.clear();
            for (Tour.Waypoint wp : tourWaypoints) {
                if (wp.getCoordinate() != null) {
                    List<Double> coords = wp.getCoordinate().getCoordinates();
                    routePoints.add(new GeoPoint(coords.get(1), coords.get(0)));
                }
            }
            showMarkersOnMap();
        }
    }

    private void setupWaypointList() {
        recyclerWaypoints.setLayoutManager(new LinearLayoutManager(this));
        waypointAdapter = new WaypointViewAdapter(tourWaypoints, position -> {
            if (position < routePoints.size() - 1) {
                drawStepRoute(position);
            } else {
                Toast.makeText(this, "End point of the journey", Toast.LENGTH_SHORT).show();
            }
        });
        recyclerWaypoints.setAdapter(waypointAdapter);
        btnWholeRoute.setOnClickListener(v -> drawFullRoute());
    }

    private void setupMapConfig() {
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(11.0);
        mapView.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
    }

    private void showMarkersOnMap() {
        mapView.getOverlays().clear();
        for (int i = 0; i < routePoints.size(); i++) {
            Marker marker = new Marker(mapView);
            marker.setPosition(routePoints.get(i));
            marker.setTitle(tourWaypoints.get(i).getLocationName());
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            mapView.getOverlays().add(marker);
        }
        if (!routePoints.isEmpty()) {
            mapView.getController().setCenter(routePoints.get(0));
        }
        mapView.invalidate();
    }

    private void drawFullRoute() {
        if (routePoints.size() < 2) return;
        clearPolylines();
        new Thread(() -> {
            RoadManager roadManager = new OSRMRoadManager(this, getPackageName());
            Road road = roadManager.getRoad(new ArrayList<>(routePoints));
            Polyline roadOverlay = RoadManager.buildRoadOverlay(road);
            roadOverlay.setColor(Color.parseColor("#2E7D32"));
            roadOverlay.setWidth(10f);
            runOnUiThread(() -> {
                mapView.getOverlays().add(roadOverlay);
                mapView.invalidate();
                mapView.getController().animateTo(routePoints.get(0));
            });
        }).start();
    }

    private void drawStepRoute(int startIndex) {
        clearPolylines();
        new Thread(() -> {
            RoadManager roadManager = new OSRMRoadManager(this, getPackageName());
            ArrayList<GeoPoint> stepPoints = new ArrayList<>();
            stepPoints.add(routePoints.get(startIndex));
            stepPoints.add(routePoints.get(startIndex + 1));
            Road road = roadManager.getRoad(stepPoints);
            Polyline roadOverlay = RoadManager.buildRoadOverlay(road);
            roadOverlay.setColor(Color.RED);
            roadOverlay.setWidth(14f);
            runOnUiThread(() -> {
                mapView.getOverlays().add(roadOverlay);
                mapView.getController().animateTo(routePoints.get(startIndex));
                mapView.invalidate();
            });
        }).start();
    }

    private void clearPolylines() {
        mapView.getOverlays().removeIf(overlay -> overlay instanceof Polyline);
        mapView.invalidate();
    }

    private String formatDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return "N/A";
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            SimpleDateFormat out = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            return out.format(in.parse(dateStr));
        } catch (Exception e) {
            return dateStr;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sliderHandler.removeCallbacks(sliderRunnable);
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (viewPagerSlideshow.getVisibility() == View.VISIBLE) {
            sliderHandler.postDelayed(sliderRunnable, 3000);
            if (mediaPlayer != null) mediaPlayer.start();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sliderHandler.removeCallbacks(sliderRunnable);
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}