package com.example.mycurrenttour;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
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

    private List<Tour> tourList;
    private boolean isHomePage;

    public TourAdapter(List<Tour> tourList, boolean isHomePage) {
        this.tourList = tourList;
        this.isHomePage = isHomePage;
    }

    public void updateList(List<Tour> newList) {
        this.tourList = newList;
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgTour, imgAuthor;
        TextView txtTitle, txtPrice, txtStartDate, txtAuthorName;
        TextView btnShareToPublic, btnMemorableVideo, btnAddTour, btnStartJourney;

        public ViewHolder(View itemView) {
            super(itemView);
            imgTour = itemView.findViewById(R.id.imgTourLogItem);
            txtTitle = itemView.findViewById(R.id.txtLogTourNameItem);
            txtPrice = itemView.findViewById(R.id.txtLogExpenseItem);
            txtStartDate = itemView.findViewById(R.id.txtLogDateItem);

            // Author views
            txtAuthorName = itemView.findViewById(R.id.txtAuthorNameItem);
            imgAuthor = itemView.findViewById(R.id.imgAuthorItem);

            // Buttons
            btnShareToPublic = itemView.findViewById(R.id.btnShareToPublicItem);
            btnMemorableVideo = itemView.findViewById(R.id.btnMemorableVideoItem);
            btnAddTour = itemView.findViewById(R.id.btnAddTourItem); 
            btnStartJourney = itemView.findViewById(R.id.btnStartJourneyItem);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tour, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Tour tour = tourList.get(position);

        // 1. Basic Info
        holder.txtTitle.setText(tour.getTitle() != null ? tour.getTitle() : "Unnamed trip");
        Picasso.get().load(tour.getImageUrl()).placeholder(R.drawable.centralvietnam).into(holder.imgTour);

        // Reset UI States
        holder.txtPrice.setTypeface(null, Typeface.NORMAL);
        holder.txtPrice.setTextColor(Color.parseColor("#757575"));
        holder.txtStartDate.setVisibility(View.GONE);
        holder.btnStartJourney.setVisibility(View.GONE);
        holder.btnAddTour.setVisibility(View.GONE);
        holder.btnShareToPublic.setVisibility(View.GONE);
        holder.btnMemorableVideo.setVisibility(View.GONE);
        holder.txtAuthorName.setVisibility(View.GONE);
        holder.imgAuthor.setVisibility(View.GONE);

        String status = tour.getStatus() != null ? tour.getStatus().trim() : "";

        // 2. Logic
        if (isHomePage) {
            holder.txtPrice.setText("Estimated cost: $" + tour.getTotalPrice());
            holder.txtPrice.setTextColor(Color.RED);
            holder.txtPrice.setTypeface(null, Typeface.BOLD);

            holder.txtAuthorName.setVisibility(View.VISIBLE);
            holder.imgAuthor.setVisibility(View.VISIBLE);
            String authorName = (tour.getAuthor() != null) ? tour.getAuthor().getDisplayName() : "Traveler";
            holder.txtAuthorName.setText(authorName);

            if (tour.getAuthor() != null && tour.getAuthor().getPhotoUrl() != null) {
                Picasso.get().load(tour.getAuthor().getPhotoUrl()).placeholder(R.drawable.ic_person).into(holder.imgAuthor);
            } else {
                holder.imgAuthor.setImageResource(R.drawable.ic_person);
            }

            holder.btnAddTour.setVisibility(View.VISIBLE);
            holder.btnAddTour.setText("+ Add this Tour");
            holder.btnAddTour.getBackground().setTint(Color.parseColor("#81C784"));
            holder.btnAddTour.setOnClickListener(v -> {
                copyTour(tour, holder);
                holder.btnAddTour.setText("Added");
                holder.btnAddTour.getBackground().setTint(Color.parseColor("#2E7D32"));
            });

        } else {
            if (status.equalsIgnoreCase("Upcoming")) {
                holder.txtPrice.setText("Estimated cost: $" + tour.getTotalPrice());
                holder.txtPrice.setTextColor(Color.RED);
                holder.txtPrice.setTypeface(null, Typeface.BOLD);

                holder.btnStartJourney.setVisibility(View.VISIBLE);
                holder.btnStartJourney.setText("Start the journey");
                holder.btnStartJourney.setOnClickListener(v -> startJourney(tour, v));

            } else if (status.equalsIgnoreCase("Completed")) {
                holder.txtStartDate.setVisibility(View.VISIBLE);
                String dr = "Date: " + formatDate(tour.getStartDate()) + " - " + formatDate(tour.getEndDate());
                holder.txtStartDate.setText(dr);
                holder.txtPrice.setText("Estimated cost: $" + tour.getTotalPrice());
                holder.txtPrice.setTextColor(Color.RED);
                holder.txtPrice.setTypeface(null, Typeface.BOLD);

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
                holder.txtPrice.setText("Cost: $" + tour.getTotalPrice());
            }
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), TourDetailActivity.class);
            intent.putExtra("tour_item", tour);
            v.getContext().startActivity(intent);
        });
    }

    private void startJourney(Tour tour, View v) {
        // Tối ưu: Chuyển màn hình ngay lập tức để người dùng không phải đợi API
        Intent intent = new Intent(v.getContext(), OngoingMapActivity.class);
        intent.putExtra("tour_item", tour);
        v.getContext().startActivity(intent);

        // Cập nhật trạng thái ngầm
        tour.setStatus("Ongoing");
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        apiService.updateTour(tour.getId(), tour).enqueue(new Callback<Tour>() {
            @Override
            public void onResponse(Call<Tour> call, Response<Tour> response) {
                if (v.getContext() instanceof MyTourActivity) {
                    ((MyTourActivity) v.getContext()).switchFilter("Ongoing");
                }
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

    private void copyTour(Tour tour, ViewHolder holder) {
        String myId = FirebaseAuth.getInstance().getUid();
        if (myId == null) return;
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        apiService.copyTour(tour.getId(), new ApiService.UserCopyRequest(myId)).enqueue(new Callback<Tour>() {
            @Override
            public void onResponse(Call<Tour> call, Response<Tour> response) {
                if (response.isSuccessful()) Toast.makeText(holder.itemView.getContext(), "Added to Upcoming!", Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onFailure(Call<Tour> call, Throwable t) {}
        });
    }

    private void updateShareButtonUI(ViewHolder holder, boolean isShared) {
        if (holder.btnShareToPublic == null) return;
        holder.btnShareToPublic.setText(isShared ? "Shared" : "Share to public");
        holder.btnShareToPublic.getBackground().setTint(isShared ? Color.parseColor("#2E7D32") : Color.parseColor("#D32F2F"));
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
