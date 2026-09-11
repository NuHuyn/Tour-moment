package com.example.mycurrenttour;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TourAdapter extends RecyclerView.Adapter<TourAdapter.ViewHolder> {

    /** Callback for actions that need to notify the hosting screen (e.g. MyTourFragment) instead of an Activity cast. */
    public interface OnTourActionListener {
        default void onJourneyStarted(Tour tour) {}
    }

    private List<Tour> tourList;
    private boolean isHomePage;
    private OnTourActionListener listener;

    public TourAdapter(List<Tour> tourList, boolean isHomePage) {
        this(tourList, isHomePage, null);
    }

    public TourAdapter(List<Tour> tourList, boolean isHomePage, OnTourActionListener listener) {
        this.tourList = tourList;
        this.isHomePage = isHomePage;
        this.listener = listener;
    }

    public void updateList(List<Tour> newList) {
        this.tourList = newList;
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgTour, imgAuthor;
        TextView txtTitle, txtStartDate, txtAuthorName, txtMeta;
        TextView btnShareToPublic, btnMemorableVideo, btnAddTour, btnStartJourney;

        // Discovery-card only (item_tour_discovery.xml) - null on item_tour.xml (My Tour cards),
        // which has no rating row; every use below is null-guarded.
        LinearLayout layoutCardRatingStars;
        TextView txtCardRatingScore;

        public ViewHolder(View itemView) {
            super(itemView);
            imgTour = itemView.findViewById(R.id.imgTourLogItem);
            txtTitle = itemView.findViewById(R.id.txtLogTourNameItem);
            txtStartDate = itemView.findViewById(R.id.txtLogDateItem);
            txtMeta = itemView.findViewById(R.id.txtTourMetaItem);

            // Author views
            txtAuthorName = itemView.findViewById(R.id.txtAuthorNameItem);
            imgAuthor = itemView.findViewById(R.id.imgAuthorItem);

            // Buttons
            btnShareToPublic = itemView.findViewById(R.id.btnShareToPublicItem);
            btnMemorableVideo = itemView.findViewById(R.id.btnMemorableVideoItem);
            btnAddTour = itemView.findViewById(R.id.btnAddTourItem);
            btnStartJourney = itemView.findViewById(R.id.btnStartJourneyItem);

            layoutCardRatingStars = itemView.findViewById(R.id.layoutCardRatingStars);
            txtCardRatingScore = itemView.findViewById(R.id.txtCardRatingScore);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Discovery's "All Tours" list (isHomePage) and MyTourFragment's "My Travel" list each
        // have their own image-left/info-right card (item_tour_discovery.xml / item_tour.xml)
        // with the same view IDs, so the rest of this class is unaware of which one got inflated.
        int layoutRes = isHomePage ? R.layout.item_tour_discovery : R.layout.item_tour;
        View view = LayoutInflater.from(parent.getContext())
                .inflate(layoutRes, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Tour tour = tourList.get(position);

        // 1. Basic Info
        holder.txtTitle.setText(tour.getTitle() != null ? tour.getTitle() : "Unnamed trip");
        // Picasso.load("") throws IllegalArgumentException ("Path must not be empty") - it only
        // tolerates null, not empty string. Backend data can legitimately have an empty/missing
        // imageUrl (e.g. a tour created without a cover photo), so this must be guarded the same
        // way the author-photo branch below is.
        if (tour.getImageUrl() != null && !tour.getImageUrl().isEmpty()) {
            Picasso.get().load(tour.getImageUrl()).placeholder(R.drawable.centralvietnam).into(holder.imgTour);
        } else {
            holder.imgTour.setImageResource(R.drawable.centralvietnam);
        }

        // Reset UI States
        holder.txtStartDate.setVisibility(View.GONE);
        holder.btnStartJourney.setVisibility(View.GONE);
        holder.btnAddTour.setVisibility(View.GONE);
        holder.btnShareToPublic.setVisibility(View.GONE);
        holder.btnMemorableVideo.setVisibility(View.GONE);
        holder.txtAuthorName.setVisibility(View.GONE);
        holder.imgAuthor.setVisibility(View.GONE);
        if (holder.txtMeta != null) holder.txtMeta.setVisibility(View.GONE);

        // "3N2Đ - Quy Nhơn" - same data-derived meta line on both Discovery and My Tour cards.
        if (holder.txtMeta != null) {
            String meta = buildDurationDestination(tour);
            if (meta != null) {
                holder.txtMeta.setText(meta);
                holder.txtMeta.setVisibility(View.VISIBLE);
            }
        }

        bindCardRating(holder, tour);

        String status = tour.getStatus() != null ? tour.getStatus().trim() : "";

        // 2. Logic
        if (isHomePage) {
            holder.txtAuthorName.setVisibility(View.VISIBLE);
            holder.imgAuthor.setVisibility(View.VISIBLE);
            String authorName = (tour.getAuthor() != null) ? tour.getAuthor().getDisplayName() : "Traveler";
            holder.txtAuthorName.setText(authorName);

            // Same Picasso.load("") crash as above ("Path must not be empty") - the backend's
            // getPublicTours fallback author object can have an empty/null photoUrl when no
            // matching User row exists for a tour's authorId (e.g. seed/demo data).
            if (tour.getAuthor() != null && tour.getAuthor().getPhotoUrl() != null && !tour.getAuthor().getPhotoUrl().isEmpty()) {
                Picasso.get().load(tour.getAuthor().getPhotoUrl()).placeholder(R.drawable.ic_person).into(holder.imgAuthor);
            } else {
                holder.imgAuthor.setImageResource(R.drawable.ic_person);
            }

            holder.btnAddTour.setVisibility(View.VISIBLE);
            // Reflects AddedTourManager's persisted state so the button shows "Added" correctly
            // after scrolling away and back, or reopening the app - not just right after tapping.
            String viewerId = SessionManager.getUserId(holder.itemView.getContext());
            boolean alreadyAdded = AddedTourManager.isAdded(holder.itemView.getContext(), viewerId, tour.getId());
            setAddButtonState(holder, alreadyAdded);
            holder.btnAddTour.setOnClickListener(v -> {
                if (holder.btnAddTour.getText().toString().equals("Added")) return; // already added, no-op
                copyTour(tour, holder);
            });

        } else {
            // My Tour cards are full-bleed photo cards (item_tour.xml) with a bottom gradient
            // scrim, so all overlaid text needs to stay white/light instead of the dark
            // gray/#212121 used back when this info sat on a plain white card.
            if (status.equalsIgnoreCase("Upcoming")) {
                holder.btnStartJourney.setVisibility(View.VISIBLE);
                holder.btnStartJourney.setText("Start the journey");
                holder.btnStartJourney.setOnClickListener(v -> startJourney(tour, v));

            } else if (status.equalsIgnoreCase("Completed")) {
                holder.txtStartDate.setVisibility(View.VISIBLE);
                String dr = "Date: " + formatDate(tour.getStartDate()) + " - " + formatDate(tour.getEndDate());
                holder.txtStartDate.setText(dr);

                holder.btnShareToPublic.setVisibility(View.VISIBLE);
                holder.btnMemorableVideo.setVisibility(View.VISIBLE);
                holder.btnMemorableVideo.getBackground().setTint(Color.parseColor("#B22222"));
                holder.btnMemorableVideo.setTextColor(Color.WHITE);
                updateShareButtonUI(holder, tour.isShared());
                holder.btnShareToPublic.setOnClickListener(v -> shareTour(tour, holder, v));
                holder.btnMemorableVideo.setOnClickListener(v -> showVideo(tour, v));
            } else {
                holder.txtStartDate.setVisibility(View.VISIBLE);
                holder.txtStartDate.setText("Start date: " + formatDate(tour.getStartDate()));
            }
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), TourDetailActivity.class);
            intent.putExtra("tour_item", tour);
            v.getContext().startActivity(intent);
        });
    }

    private void startJourney(Tour tour, View v) {
        Intent intent = new Intent(v.getContext(), OngoingMapActivity.class);
        intent.putExtra("tour_item", tour);
        v.getContext().startActivity(intent);

        tour.setStatus("Ongoing");
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        apiService.updateTour(tour.getId(), tour).enqueue(new Callback<Tour>() {
            @Override
            public void onResponse(Call<Tour> call, Response<Tour> response) {
                if (listener != null) listener.onJourneyStarted(tour);
            }
            @Override
            public void onFailure(Call<Tour> call, Throwable t) {}
        });
    }

    private void showVideo(Tour tour, View v) {
        ArrayList<String> p = new ArrayList<>();
        if (tour.getImageUrl() != null) p.add(tour.getImageUrl());
        if (tour.getWaypoints() != null) {
            for (Tour.Waypoint wp : tour.getWaypoints()) {
                if (wp.getPhotos() != null) p.addAll(wp.getPhotos());
            }
        }
        if (p.isEmpty()) {
            Toast.makeText(v.getContext(), "No photos!", Toast.LENGTH_SHORT).show();
        } else {
            Intent intent = new Intent(v.getContext(), ViewVideoActivity.class);
            intent.putStringArrayListExtra("PHOTO_LIST", p);
            v.getContext().startActivity(intent);
        }
    }

    private void shareTour(Tour tour, ViewHolder holder, View v) {
        if (tour.isShared()) return;
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        apiService.shareTour(tour.getId()).enqueue(new Callback<Tour>() {
            @Override
            public void onResponse(Call<Tour> call, Response<Tour> response) {
                if (response.isSuccessful()) {
                    tour.setShared(true);
                    updateShareButtonUI(holder, true);
                    Toast.makeText(v.getContext(), "Shared successfully!", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<Tour> call, Throwable t) {}
        });
    }

    /** "+ Add this Tour" -> POST /api/tours/copy/:tourId, which clones the tour server-side with
     *  authorId = the current viewer (Google account or local guest id - see SessionManager), so
     *  it shows up in that user's My Travel (MyTourFragment's getMyTours query filters by the
     *  same authorId). Only marks the button/local "added" state once the server confirms the
     *  copy actually happened, not optimistically on tap. */
    private void copyTour(Tour tour, ViewHolder holder) {
        Context context = holder.itemView.getContext();
        // Real Google-account id or local guest id (SessionManager.saveGuestSession gives every
        // guest session a persistent local id) - both work identically here since the backend
        // just stores whatever string it's given as authorId, no real User row required.
        String myId = SessionManager.getUserId(context);
        if (myId == null) {
            Toast.makeText(context, "Please sign in or continue as guest first", Toast.LENGTH_SHORT).show();
            return;
        }
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        String deviceId = DeviceIdProvider.getOrCreate(context);
        apiService.copyTour(tour.getId(), new ApiService.UserCopyRequest(myId, deviceId)).enqueue(new Callback<Tour>() {
            @Override
            public void onResponse(Call<Tour> call, Response<Tour> response) {
                if (response.isSuccessful()) {
                    AddedTourManager.markAdded(context, myId, tour.getId());
                    setAddButtonState(holder, true);
                    Toast.makeText(context, "Added to My Travel!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Could not add tour, please try again", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<Tour> call, Throwable t) {
                Toast.makeText(context, "Could not reach server, please try again", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Sets the "+ Add this Tour" button's text/color/enabled state for either the not-yet-added
     *  or already-added look. Centralized so onBindViewHolder's initial state and copyTour's
     *  post-success update never drift apart. */
    private void setAddButtonState(ViewHolder holder, boolean added) {
        if (added) {
            holder.btnAddTour.setText("Added");
            holder.btnAddTour.getBackground().setTint(Color.parseColor("#2E7D32"));
            holder.btnAddTour.setEnabled(false);
        } else {
            holder.btnAddTour.setText("+ Add this Tour");
            holder.btnAddTour.getBackground().setTint(Color.parseColor("#81C784"));
            holder.btnAddTour.setEnabled(true);
        }
    }

    private void updateShareButtonUI(ViewHolder holder, boolean isShared) {
        if (holder.btnShareToPublic == null) return;
        holder.btnShareToPublic.setText(isShared ? "Shared" : "Share to public");
        holder.btnShareToPublic.getBackground().setTint(isShared ? Color.parseColor("#2E7D32") : Color.parseColor("#D32F2F"));
    }

    /** Discovery-card-only 5-star row (item_tour_discovery.xml's layoutCardRatingStars/
     *  txtCardRatingScore - null on item_tour.xml, so this is a no-op there). Stars up to
     *  round(avgRating) render gold (ic_star_filled), the rest gray (ic_star_gray); with no
     *  reviews yet (reviewCount == 0) all 5 stay gray and the numeric score is hidden instead of
     *  showing a misleading "0.0". */
    private void bindCardRating(ViewHolder holder, Tour tour) {
        if (holder.layoutCardRatingStars == null) return;

        int filledStars = tour.getReviewCount() > 0
                ? Math.max(0, Math.min(5, (int) Math.round(tour.getAvgRating())))
                : 0;

        holder.layoutCardRatingStars.removeAllViews();
        Context context = holder.itemView.getContext();
        int starSizePx = Math.round(13 * context.getResources().getDisplayMetrics().density);
        int starGapPx = Math.round(1 * context.getResources().getDisplayMetrics().density);
        for (int i = 1; i <= 5; i++) {
            ImageView star = new ImageView(context);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(starSizePx, starSizePx);
            if (i > 1) lp.setMarginStart(starGapPx);
            star.setLayoutParams(lp);
            star.setImageResource(i <= filledStars ? R.drawable.ic_star_filled : R.drawable.ic_star_gray);
            holder.layoutCardRatingStars.addView(star);
        }

        if (holder.txtCardRatingScore != null) {
            if (tour.getReviewCount() > 0) {
                holder.txtCardRatingScore.setText(String.format(Locale.getDefault(), "%.1f", tour.getAvgRating()));
                holder.txtCardRatingScore.setVisibility(View.VISIBLE);
            } else {
                holder.txtCardRatingScore.setVisibility(View.GONE);
            }
        }
    }

    /** Builds the "3N2Đ - Quy Nhơn" meta line shown on both Discovery and My Tour cards, from data
     *  the Tour already carries - no new fields: nights from startDate/endDate (same ISO parsing
     *  as formatDate()), destination from the first waypoint's locationName (its part after the
     *  last " - ", e.g. "Chợ nổi Cái Răng - Cần Thơ" -> "Cần Thơ"). Returns null if neither part
     *  is available. */
    private String buildDurationDestination(Tour tour) {
        String duration = null;
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
            in.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date start = in.parse(tour.getStartDate());
            Date end = in.parse(tour.getEndDate());
            long nights = (end.getTime() - start.getTime()) / (24L * 60 * 60 * 1000);
            if (nights > 0) duration = (nights + 1) + "N" + nights + "Đ";
        } catch (Exception ignored) {}

        String destination = null;
        if (tour.getWaypoints() != null && !tour.getWaypoints().isEmpty()) {
            String locationName = tour.getWaypoints().get(0).getLocationName();
            if (locationName != null && !locationName.trim().isEmpty()) {
                int dashIndex = locationName.lastIndexOf(" - ");
                destination = dashIndex >= 0 ? locationName.substring(dashIndex + 3) : locationName;
            }
        }

        if (duration != null && destination != null) return duration + " - " + destination;
        if (destination != null) return destination;
        return duration;
    }

    private String formatDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return "--/--/----";
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
            in.setTimeZone(TimeZone.getTimeZone("UTC"));
            SimpleDateFormat out = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            return out.format(in.parse(dateStr));
        } catch (Exception e) { return dateStr; }
    }

    @Override
    public int getItemCount() { return tourList != null ? tourList.size() : 0; }
}
