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
import android.text.format.DateUtils;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
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
import java.util.TimeZone;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

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
    private TextView txtIdDetail, txtDurationDetail, txtStartDetail, txtEndDetail;
    private TextView txtDescription, txtToggleDescription;
    private LinearLayout layoutDestinationChips;

    // Reviews section
    private TextView txtReviewsSectionRating, txtReviewsSectionCount, txtReviewsEmpty;
    private LinearLayout btnWriteReview, layoutReviewsList;
    private List<Review> currentReviews = new ArrayList<>();

    private Tour tour;
    private final List<String> photos = new ArrayList<>();
    private final Handler sliderHandler = new Handler(Looper.getMainLooper());
    private MediaPlayer mediaPlayer;
    private static final String TAG = "TourDetailActivity";

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

        prefetchImages(tour);
        preparePhotos(tour);
        setupHeroCarousel();
        setupHeroScrollBlur();
        displayTourData(tour);
        setupDestinationChips(tour);
        setupDescription(tour.getDescription());
        loadReviews();

        btnWriteReview.setOnClickListener(v -> onWriteReviewClick());

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
        txtStartDetail = findViewById(R.id.txtStartDetail);
        txtEndDetail = findViewById(R.id.txtEndDetail);
        txtDescription = findViewById(R.id.txtDescription);
        txtToggleDescription = findViewById(R.id.txtToggleDescription);
        layoutDestinationChips = findViewById(R.id.layoutDestinationChips);

        txtReviewsSectionRating = findViewById(R.id.txtReviewsSectionRating);
        txtReviewsSectionCount = findViewById(R.id.txtReviewsSectionCount);
        txtReviewsEmpty = findViewById(R.id.txtReviewsEmpty);
        btnWriteReview = findViewById(R.id.btnWriteReview);
        layoutReviewsList = findViewById(R.id.layoutReviewsList);
    }

    private void toggleFavorite() {
        isFavorited = !isFavorited;
        btnFavoriteDetail.setImageResource(isFavorited ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
    }

    private void shareTour() {
        // "JourneyLog" is the product's brand name, not app_name (the Android app label) -
        // deliberately left unlocalized, same as it already was.
        String title = tour.getTitle() != null ? tour.getTitle() : "JourneyLog";
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, title);
        shareIntent.putExtra(Intent.EXTRA_TEXT, title + " - " + (tour.getDescription() != null ? tour.getDescription() : getString(R.string.share_text_fallback)));
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_via)));
    }

    /** Kicks off Picasso's cache-warming .fetch() (no target ImageView, doesn't touch layout) for
     *  the cover + first few waypoint photos the instant the Tour data is available - before
     *  setupHeroCarousel()/the waypoint RecyclerView even inflate their views. By the time those
     *  actually call .load(...).into(imageView), the bytes are already downloading (or done), so
     *  the real .into() call resolves from cache instead of starting a fresh fetch at bind time.
     *  Doesn't change what eventually renders, just moves the network request earlier. */
    private void prefetchImages(Tour tour) {
        if (tour.getImageUrl() != null && !tour.getImageUrl().isEmpty()) {
            Picasso.get().load(tour.getImageUrl()).fetch();
        }
        if (tour.getWaypoints() != null) {
            int count = 0;
            for (Tour.Waypoint wp : tour.getWaypoints()) {
                if (count >= 4) break; // first screenful is enough - no point prefetching the whole trip up front
                if (wp.getPhotos() != null && !wp.getPhotos().isEmpty() && !wp.getPhotos().get(0).isEmpty()) {
                    Picasso.get().load(wp.getPhotos().get(0)).fetch();
                    count++;
                }
            }
        }
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

        // Picasso.load("") throws IllegalArgumentException ("Path must not be empty") - guard
        // against an empty/missing imageUrl the same way TourAdapter does.
        if (tour.getImageUrl() != null && !tour.getImageUrl().isEmpty()) {
            Picasso.get().load(tour.getImageUrl())
                    .placeholder(R.drawable.centralvietnam)
                    .error(R.drawable.centralvietnam)
                    .into(imgTour);
        } else {
            imgTour.setImageResource(R.drawable.centralvietnam);
        }

        txtIdDetail.setText(tour.getId() != null ? tour.getId() : getString(R.string.not_available));
        txtDurationDetail.setText(buildDuration(tour));
        txtStartDetail.setText(formatDate(tour.getStartDate()));
        txtEndDetail.setText(formatDate(tour.getEndDate()));

        // Real rating/review-count now come from GET /api/tours/{id}/reviews (see loadReviews()),
        // which is aggregated live from the Review collection - no more hash-seeded placeholder
        // numbers. Both this row and the reviews section header show "…" until that call resolves.
        txtRatingDetail.setText(R.string.not_available);
        txtReviewCountDetail.setText(R.string.review_count_placeholder);
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

    // ================= Reviews =================

    /** Fetches GET /api/tours/{id}/reviews and refreshes both the top rating row (txtRatingDetail/
     *  txtReviewCountDetail) and the reviews section (header + list) from the same response, so
     *  the two numbers on screen never disagree. */
    private void loadReviews() {
        if (tour.getId() == null) return;
        ApiService api = ApiClient.getClient().create(ApiService.class);
        api.getTourReviews(tour.getId()).enqueue(new Callback<ApiService.ReviewsResponse>() {
            @Override
            public void onResponse(Call<ApiService.ReviewsResponse> call, Response<ApiService.ReviewsResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.e(TAG, "getTourReviews failed: HTTP " + response.code());
                    Toast.makeText(TourDetailActivity.this, R.string.reviews_load_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                ApiService.ReviewsResponse body = response.body();
                currentReviews = body.getReviews() != null ? body.getReviews() : new ArrayList<>();
                applyRatingSummary(body.getAvgRating(), body.getReviewCount());
                renderReviews();
            }

            @Override
            public void onFailure(Call<ApiService.ReviewsResponse> call, Throwable t) {
                Log.e(TAG, "getTourReviews call failed", t);
                Toast.makeText(TourDetailActivity.this, R.string.reviews_load_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyRatingSummary(double avgRating, int reviewCount) {
        String ratingText;
        if (reviewCount > 0) {
            NumberFormat ratingFormat = NumberFormat.getNumberInstance(Locale.getDefault());
            ratingFormat.setMinimumFractionDigits(1);
            ratingFormat.setMaximumFractionDigits(1);
            ratingText = ratingFormat.format(avgRating);
        } else {
            ratingText = getString(R.string.not_available);
        }
        String countText = getResources().getQuantityString(R.plurals.review_count, reviewCount, reviewCount);

        txtRatingDetail.setText(ratingText);
        txtReviewCountDetail.setText(countText);
        txtReviewsSectionRating.setText(ratingText);
        txtReviewsSectionCount.setText(countText);
    }

    private void renderReviews() {
        layoutReviewsList.removeAllViews();
        txtReviewsEmpty.setVisibility(currentReviews.isEmpty() ? View.VISIBLE : View.GONE);

        String myUserId = SessionManager.getUserId(this);
        for (Review review : currentReviews) {
            layoutReviewsList.addView(buildReviewItemView(review, myUserId));
        }
    }

    private View buildReviewItemView(Review review, String myUserId) {
        View itemView = LayoutInflater.from(this).inflate(R.layout.item_review, layoutReviewsList, false);

        ImageView imgAvatar = itemView.findViewById(R.id.imgReviewerAvatar);
        TextView txtName = itemView.findViewById(R.id.txtReviewerName);
        LinearLayout layoutStars = itemView.findViewById(R.id.layoutReviewStars);
        TextView txtDate = itemView.findViewById(R.id.txtReviewDate);
        TextView txtComment = itemView.findViewById(R.id.txtReviewComment);
        TextView txtDelete = itemView.findViewById(R.id.txtDeleteReview);

        String name = review.getUser() != null && review.getUser().getDisplayName() != null
                ? review.getUser().getDisplayName() : getString(R.string.default_reviewer_name);
        txtName.setText(name);

        String photoUrl = review.getUser() != null ? review.getUser().getPhotoUrl() : null;
        if (photoUrl != null && !photoUrl.isEmpty()) {
            Picasso.get().load(photoUrl)
                    .placeholder(R.drawable.ic_user_placeholder)
                    .error(R.drawable.ic_user_placeholder)
                    .into(imgAvatar);
        } else {
            imgAvatar.setImageResource(R.drawable.ic_user_placeholder);
        }

        layoutStars.removeAllViews();
        int starSizePx = dp(12);
        for (int i = 1; i <= 5; i++) {
            ImageView star = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(starSizePx, starSizePx);
            if (i > 1) lp.setMarginStart(dp(1));
            star.setLayoutParams(lp);
            star.setImageResource(i <= review.getRating() ? R.drawable.ic_star_filled : R.drawable.ic_star_gray);
            layoutStars.addView(star);
        }

        txtDate.setText(formatRelativeDate(review.getCreatedAt()));

        String comment = review.getComment();
        if (comment != null && !comment.trim().isEmpty()) {
            txtComment.setText(comment);
            txtComment.setVisibility(View.VISIBLE);
        } else {
            txtComment.setVisibility(View.GONE);
        }

        boolean isOwnReview = myUserId != null && myUserId.equals(review.getUserId());
        txtDelete.setVisibility(isOwnReview ? View.VISIBLE : View.GONE);
        if (isOwnReview) {
            txtDelete.setOnClickListener(v -> confirmDeleteReview());
        }

        return itemView;
    }

    /** ISO-8601 (Mongoose timestamps, e.g. "2026-09-10T12:34:56.789Z") -> localized relative
     *  string ("2 ngày trước" / "2 days ago") via the same Locale.getDefault()-driven convention
     *  formatDate() already uses for absolute dates elsewhere on this screen. */
    private String formatRelativeDate(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) return "";
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            in.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date parsed = in.parse(isoDate);
            return DateUtils.getRelativeTimeSpanString(parsed.getTime(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** Guests (SessionManager.SignInType.GUEST) get a sign-in-required prompt instead - there is
     *  no existing "sign in mid-flow and return to where you were" mechanism anywhere else in this
     *  app (LoginActivity always finishes into a fresh HomeActivity, discarding the back stack),
     *  so this is a minimal stopgap: tapping "Đăng nhập" takes the user to LoginActivity/Home, not
     *  back to this exact screen. Flagged as a real UX gap worth a dedicated fix later. */
    private void onWriteReviewClick() {
        if (SessionManager.getSignInType(this) != SessionManager.SignInType.GOOGLE) {
            showSignInRequiredDialog();
            return;
        }

        String myUserId = SessionManager.getUserId(this);
        Review existing = null;
        for (Review r : currentReviews) {
            if (myUserId != null && myUserId.equals(r.getUserId())) {
                existing = r;
                break;
            }
        }

        boolean isEdit = existing != null;
        ReviewFormBottomSheet sheet = ReviewFormBottomSheet.newInstance(
                isEdit ? existing.getRating() : 0,
                isEdit ? existing.getComment() : "");
        sheet.setListener((rating, comment) -> submitOrUpdateReview(isEdit, rating, comment));
        sheet.show(getSupportFragmentManager(), "review_form");
    }

    private void showSignInRequiredDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_signin_required_title)
                .setMessage(R.string.dialog_signin_required_message)
                .setPositiveButton(R.string.action_sign_in, (d, w) ->
                        startActivity(new Intent(this, LoginActivity.class)))
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void submitOrUpdateReview(boolean isEdit, int rating, String comment) {
        String myUserId = SessionManager.getUserId(this);
        if (myUserId == null || tour.getId() == null) return;

        ApiService api = ApiClient.getClient().create(ApiService.class);
        ApiService.ReviewRequest body = new ApiService.ReviewRequest(myUserId, rating, comment);
        Call<Review> call = isEdit ? api.updateReview(tour.getId(), body) : api.submitReview(tour.getId(), body);

        call.enqueue(new Callback<Review>() {
            @Override
            public void onResponse(Call<Review> call, Response<Review> response) {
                if (!response.isSuccessful()) {
                    Log.e(TAG, (isEdit ? "updateReview" : "submitReview") + " failed: HTTP " + response.code());
                    Toast.makeText(TourDetailActivity.this, R.string.review_submit_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(TourDetailActivity.this,
                        isEdit ? R.string.review_update_success : R.string.review_submit_success,
                        Toast.LENGTH_SHORT).show();
                loadReviews();
            }

            @Override
            public void onFailure(Call<Review> call, Throwable t) {
                Log.e(TAG, (isEdit ? "updateReview" : "submitReview") + " call failed", t);
                Toast.makeText(TourDetailActivity.this, R.string.review_submit_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmDeleteReview() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_review_title)
                .setMessage(R.string.confirm_delete_review_message)
                .setPositiveButton(R.string.dialog_yes, (d, w) -> deleteOwnReview())
                .setNegativeButton(R.string.dialog_no, null)
                .show();
    }

    private void deleteOwnReview() {
        String myUserId = SessionManager.getUserId(this);
        if (myUserId == null || tour.getId() == null) return;

        ApiService api = ApiClient.getClient().create(ApiService.class);
        api.deleteReview(tour.getId(), new ApiService.UserIdRequest(myUserId)).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "deleteReview failed: HTTP " + response.code());
                    Toast.makeText(TourDetailActivity.this, R.string.review_submit_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(TourDetailActivity.this, R.string.review_delete_success, Toast.LENGTH_SHORT).show();
                loadReviews();
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(TAG, "deleteReview call failed", t);
                Toast.makeText(TourDetailActivity.this, R.string.review_submit_failed, Toast.LENGTH_SHORT).show();
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
