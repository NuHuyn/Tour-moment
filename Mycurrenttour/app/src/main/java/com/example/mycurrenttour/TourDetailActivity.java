package com.example.mycurrenttour;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.Layout;
import android.media.MediaPlayer;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.squareup.picasso.Picasso;

import eightbitlab.com.blurview.BlurTarget;
import eightbitlab.com.blurview.BlurView;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Tour Detail - editorial-style overview (Style B): full-bleed photo carousel, large bold title,
 * info-card grid, destination chips, video preview, expandable description. No fixed package
 * price or purchase button - this app monetizes per-waypoint on Route Details
 * (OngoingMapActivity), which is where the single "Xem lộ trình" CTA at the bottom sends the user.
 */
public class TourDetailActivity extends AppCompatActivity {

    private ViewPager2 viewPagerHero;
    private TextView txtPhotoCounter;
    private ImageView btnFavoriteDetail;
    private boolean isFavorited = false;

    private ScrollView scrollTourDetail;
    private BlurView blurViewHero;
    private View frameHero;
    private static final float HERO_MAX_BLUR_RADIUS = 24f;
    private static final int HERO_MAX_DIM_ALPHA = 76; // ~30% black (76/255)

    private ImageView imgTour;
    private ViewPager2 viewPagerSlideshow;
    private ImageButton btnPlaySlideshow;

    private TextView txtTitleDetail, txtProviderDetail, txtRatingDetail, txtReviewCountDetail;
    private TextView txtIdDetail, txtDurationDetail, txtSeatsDetail, txtStartDetail, txtEndDetail;
    private TextView txtDescription, txtToggleDescription;
    private LinearLayout layoutDestinationChips;

    private Tour tour;
    private final List<String> photos = new ArrayList<>();
    private final Handler sliderHandler = new Handler(Looper.getMainLooper());
    private MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tour_detail);

        initViews();

        tour = (Tour) getIntent().getSerializableExtra("tour_item");
        if (tour == null) {
            Toast.makeText(this, R.string.invalid_data, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        preparePhotos(tour);
        setupHeroCarousel();
        setupHeroScrollBlur();
        displayTourData(tour);
        setupDestinationChips(tour);
        setupDescription(tour.getDescription());

        btnPlaySlideshow.setOnClickListener(v -> startInternalSlideshow());
        findViewById(R.id.btnBackDetail).setOnClickListener(v -> finish());
        btnFavoriteDetail.setOnClickListener(v -> toggleFavorite());
        findViewById(R.id.btnShareDetail).setOnClickListener(v -> shareTour());

        // Single CTA - takes the user to Route Details (the waypoint list/map screen) where
        // per-waypoint unlock actually happens. No price, no "Đặt ngay" here.
        findViewById(R.id.btnViewRoute).setOnClickListener(v -> {
            Intent intent = new Intent(this, OngoingMapActivity.class);
            intent.putExtra("tour_item", tour);
            startActivity(intent);
        });
    }

    private void initViews() {
        viewPagerHero = findViewById(R.id.viewPagerHero);
        txtPhotoCounter = findViewById(R.id.txtPhotoCounter);
        btnFavoriteDetail = findViewById(R.id.btnFavoriteDetail);
        scrollTourDetail = findViewById(R.id.scrollTourDetail);
        blurViewHero = findViewById(R.id.blurViewHero);
        frameHero = findViewById(R.id.frameHero);

        imgTour = findViewById(R.id.imgTourDetail);
        viewPagerSlideshow = findViewById(R.id.viewPagerDetailSlideshow);
        btnPlaySlideshow = findViewById(R.id.btnPlaySlideshow);

        txtTitleDetail = findViewById(R.id.txtTitleDetail);
        txtProviderDetail = findViewById(R.id.txtProviderDetail);
        txtRatingDetail = findViewById(R.id.txtRatingDetail);
        txtReviewCountDetail = findViewById(R.id.txtReviewCountDetail);
        txtIdDetail = findViewById(R.id.txtIdDetail);
        txtDurationDetail = findViewById(R.id.txtDurationDetail);
        txtSeatsDetail = findViewById(R.id.txtSeatsDetail);
        txtStartDetail = findViewById(R.id.txtStartDetail);
        txtEndDetail = findViewById(R.id.txtEndDetail);
        txtDescription = findViewById(R.id.txtDescription);
        txtToggleDescription = findViewById(R.id.txtToggleDescription);
        layoutDestinationChips = findViewById(R.id.layoutDestinationChips);
    }

    private void toggleFavorite() {
        isFavorited = !isFavorited;
        btnFavoriteDetail.setImageResource(isFavorited ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
    }

    private void shareTour() {
        // "TourMoment" is the product's brand name, not app_name (the Android app label) -
        // deliberately left unlocalized, same as it already was.
        String title = tour.getTitle() != null ? tour.getTitle() : "TourMoment";
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, title);
        shareIntent.putExtra(Intent.EXTRA_TEXT, title + " - " + (tour.getDescription() != null ? tour.getDescription() : getString(R.string.share_text_fallback)));
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_via)));
    }

    private void preparePhotos(Tour tour) {
        photos.clear();
        if (tour.getImageUrl() != null) photos.add(tour.getImageUrl());
        if (tour.getWaypoints() != null) {
            for (Tour.Waypoint wp : tour.getWaypoints()) {
                if (wp.getPhotos() != null) photos.addAll(wp.getPhotos());
            }
        }
        if (photos.isEmpty()) photos.add(null); // placeholder slide so the carousel/adapter still has 1 item
    }

    private void setupHeroCarousel() {
        viewPagerHero.setAdapter(new SlideshowAdapter(photos));
        txtPhotoCounter.setText("1/" + photos.size());
        viewPagerHero.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                txtPhotoCounter.setText((position + 1) + "/" + photos.size());
            }
        });
    }

    /**
     * Scroll-driven blur on the hero photo: blurViewHero (a live blurred copy of blurTargetHero,
     * see activity_tour_detail.xml) sits on top of the sharp photo, and its blur radius + dim
     * overlay are recomputed straight off the ScrollView's offset on every scroll callback - no
     * separate triggered/duration-based animation, so it tracks the finger 1:1 with no stepping.
     * At scrollY=0 the radius is 0, which renders identical to the sharp photo underneath.
     */
    private void setupHeroScrollBlur() {
        BlurTarget blurTarget = findViewById(R.id.blurTargetHero);
        blurViewHero.setupWith(blurTarget)
                .setFrameClearDrawable(new ColorDrawable(Color.WHITE))
                .setBlurRadius(0f);
        blurViewHero.setOverlayColor(Color.TRANSPARENT);

        scrollTourDetail.setOnScrollChangeListener((View.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) ->
                updateHeroBlur(scrollY));
    }

    private void updateHeroBlur(int scrollY) {
        int scrollRange = frameHero.getHeight();
        if (scrollRange <= 0) return;

        float fraction = Math.max(0f, Math.min(1f, scrollY / (float) scrollRange));
        blurViewHero.setBlurRadius(fraction * HERO_MAX_BLUR_RADIUS);
        int dimAlpha = Math.round(fraction * HERO_MAX_DIM_ALPHA);
        blurViewHero.setOverlayColor(Color.argb(dimAlpha, 0, 0, 0));
    }

    private void startInternalSlideshow() {
        if (photos.isEmpty()) {
            Toast.makeText(this, R.string.no_photos_error, Toast.LENGTH_SHORT).show();
            return;
        }

        imgTour.setAlpha(0.4f);

        btnPlaySlideshow.setVisibility(View.GONE);
        viewPagerSlideshow.setVisibility(View.VISIBLE);

        SlideshowAdapter adapter = new SlideshowAdapter(photos);
        viewPagerSlideshow.setAdapter(adapter);

        viewPagerSlideshow.setOffscreenPageLimit(1);

        viewPagerSlideshow.setPageTransformer((page, position) -> {
            page.setTranslationX(-position * page.getWidth());

            if (position < -1 || position > 1) {
                page.setAlpha(0f);
            } else {
                float alpha = Math.max(0.01f, 1 - Math.abs(position));
                page.setAlpha(alpha);

                float scale = 1.0f + (alpha * 0.05f);
                page.setScaleX(scale);
                page.setScaleY(scale);
            }
        });

        initMusic();

        sliderHandler.removeCallbacks(sliderRunnable);
        sliderHandler.postDelayed(sliderRunnable, 4000);
    }

    private final Runnable sliderRunnable = new Runnable() {
        @Override
        public void run() {
            if (viewPagerSlideshow.getAdapter() != null && photos.size() > 1) {
                int nextItem = (viewPagerSlideshow.getCurrentItem() + 1) % photos.size();
                viewPagerSlideshow.setCurrentItem(nextItem, true);
                sliderHandler.postDelayed(this, 4000);
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
        txtTitleDetail.setText(tour.getTitle() != null ? tour.getTitle() : getString(R.string.unnamed_trip));

        String providerName = (tour.getAuthor() != null && tour.getAuthor().getDisplayName() != null)
                ? tour.getAuthor().getDisplayName() : getString(R.string.default_name_traveler);
        txtProviderDetail.setText(getString(R.string.provider_by_format, providerName));

        Picasso.get().load(tour.getImageUrl())
                .placeholder(R.drawable.centralvietnam)
                .error(R.drawable.centralvietnam)
                .into(imgTour);

        txtIdDetail.setText(tour.getId() != null ? tour.getId() : getString(R.string.not_available));
        txtDurationDetail.setText(buildDuration(tour));
        txtStartDetail.setText(formatDate(tour.getStartDate()));
        txtEndDetail.setText(formatDate(tour.getEndDate()));

        // Rating/review-count/seats-left aren't backend fields yet (Tour has no such properties) -
        // deterministic per-tour placeholder values (seeded off the tour id) so a given tour
        // always shows the same numbers instead of them looking randomly generated on every open.
        // TODO: replace with real fields once the backend exposes them.
        int seed = tour.getId() != null ? Math.abs(tour.getId().hashCode()) : 0;
        double rating = 4.5 + (seed % 5) / 10.0;
        int reviewCount = 80 + seed % 150;
        int seatsLeft = 5 + seed % 20;

        NumberFormat ratingFormat = NumberFormat.getNumberInstance(Locale.getDefault());
        ratingFormat.setMinimumFractionDigits(1);
        ratingFormat.setMaximumFractionDigits(1);
        txtRatingDetail.setText(ratingFormat.format(rating));

        txtReviewCountDetail.setText(getResources().getQuantityString(R.plurals.review_count, reviewCount, reviewCount));
        txtSeatsDetail.setText(NumberFormat.getIntegerInstance(Locale.getDefault()).format(seatsLeft));
    }

    /** "3N2Đ" (vi) / "3D2N" (en) from startDate/endDate, same nights+1/nights convention as
     *  TourAdapter's card meta line. */
    private String buildDuration(Tour tour) {
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            Date start = in.parse(tour.getStartDate());
            Date end = in.parse(tour.getEndDate());
            long nights = (end.getTime() - start.getTime()) / (24L * 60 * 60 * 1000);
            if (nights > 0) {
                return String.format(Locale.getDefault(), getString(R.string.tour_duration_format), nights + 1, nights);
            }
        } catch (Exception ignored) {}
        return getString(R.string.not_available);
    }

    /** One chip per unique destination city, parsed the same way as TourAdapter's meta line: the
     *  part of each waypoint's locationName after its last " - " (e.g. "Chợ nổi Cái Răng - Cần Thơ"
     *  -> "Cần Thơ"), de-duplicated but keeping first-seen order. */
    private void setupDestinationChips(Tour tour) {
        layoutDestinationChips.removeAllViews();
        if (tour.getWaypoints() == null) return;

        LinkedHashSet<String> destinations = new LinkedHashSet<>();
        for (Tour.Waypoint wp : tour.getWaypoints()) {
            String locationName = wp.getLocationName();
            if (locationName == null || locationName.trim().isEmpty()) continue;
            int dashIndex = locationName.lastIndexOf(" - ");
            String dest = dashIndex >= 0 ? locationName.substring(dashIndex + 3) : locationName;
            destinations.add(dest.trim());
        }

        for (String dest : destinations) {
            TextView chip = new TextView(this);
            chip.setText(dest);
            chip.setTextColor(ContextCompat.getColor(this, R.color.green_dark_primary));
            chip.setTextSize(13);
            chip.setTypeface(null, Typeface.BOLD);
            chip.setBackgroundResource(R.drawable.bg_light_green_round);
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            chip.setLayoutParams(lp);
            layoutDestinationChips.addView(chip);
        }
    }

    /** Collapsed to 3 lines by default; "Xem thêm" only shows up if the text actually got
     *  truncated (checked via the laid-out Layout's ellipsis count, once it's actually measured -
     *  a short description just never gets the toggle link at all). */
    private void setupDescription(String description) {
        txtDescription.setText(description != null ? description : "");
        txtDescription.setMaxLines(3);
        txtDescription.setEllipsize(TextUtils.TruncateAt.END);
        txtToggleDescription.setVisibility(View.GONE);

        txtDescription.post(() -> {
            Layout layout = txtDescription.getLayout();
            if (layout == null || layout.getLineCount() == 0) return;
            int lastLine = Math.min(txtDescription.getMaxLines(), layout.getLineCount()) - 1;
            if (lastLine >= 0 && layout.getEllipsisCount(lastLine) > 0) {
                txtToggleDescription.setVisibility(View.VISIBLE);
            }
        });

        txtToggleDescription.setOnClickListener(v -> {
            boolean expanded = txtDescription.getMaxLines() != 3;
            if (expanded) {
                txtDescription.setMaxLines(3);
                txtDescription.setEllipsize(TextUtils.TruncateAt.END);
                txtToggleDescription.setText(R.string.show_more_toggle);
            } else {
                txtDescription.setMaxLines(Integer.MAX_VALUE);
                txtDescription.setEllipsize(null);
                txtToggleDescription.setText(R.string.show_less_toggle);
            }
        });
    }

    private String formatDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return getString(R.string.not_available);
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            Date parsed = in.parse(dateStr);
            // Locale-aware output (was a hardcoded "dd/MM/yyyy" pattern) - format adapts with the
            // active app language, e.g. "20 thg 8, 2026" (vi) vs "Aug 20, 2026" (en).
            java.text.DateFormat out = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, Locale.getDefault());
            return out.format(parsed);
        } catch (Exception e) {
            return dateStr;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
