package com.example.mycurrenttour;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import android.util.Log;
import java.util.HashMap;
import java.util.Map;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

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
        ImageView imgTour, btnAddTour;
        TextView txtTitle, txtPrice, txtStartDate, txtEndDate, txtTravelerName;
        TextView btnShareToPublic, btnMemorableVideo;
        View layoutUserButtons;

        public ViewHolder(View itemView) {
            super(itemView);
            imgTour = itemView.findViewById(R.id.imgTourLogItem);
            btnAddTour = itemView.findViewById(R.id.btnAddTourHome);
            txtTitle = itemView.findViewById(R.id.txtLogTourNameItem);
            txtPrice = itemView.findViewById(R.id.txtLogExpenseItem);
            txtStartDate = itemView.findViewById(R.id.txtLogDateItem);
            txtEndDate = itemView.findViewById(R.id.txtLogEndDateItem);
            txtTravelerName = itemView.findViewById(R.id.txtTravelerName);
            layoutUserButtons = itemView.findViewById(R.id.layoutUserButtons);
            btnShareToPublic = itemView.findViewById(R.id.btnShareToPublicItem);
            btnMemorableVideo = itemView.findViewById(R.id.btnMemorableVideoItem);
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

        holder.txtTitle.setText(tour.getTitle() != null ? tour.getTitle() : "Self-guided trip");

        // --- FIX LỖI 1: Gọi hàm getTotalPrice() đã thêm vào Model ---
        holder.txtPrice.setText("$" + calculateTotal(tour));

        if (holder.txtTravelerName != null) {    // Sử dụng hàm getAuthorDetails() mà chúng ta đã định nghĩa trong class Tour mới
            Tour.UserDetails author = tour.getAuthorDetails();
            if (author != null && author.getDisplayName() != null) {
                holder.txtTravelerName.setText("By: " + author.getDisplayName());
            } else {
                holder.txtTravelerName.setText("By: Anonymous");
            }
        }

        Picasso.get()
                .load(tour.getImageUrl())
                .fit()
                .centerCrop()
                .placeholder(R.drawable.centralvietnam)
                .error(R.drawable.centralvietnam)
                .into(holder.imgTour);

        if (isHomePage) {
            holder.txtStartDate.setVisibility(View.GONE);
            if (holder.txtEndDate != null) holder.txtEndDate.setVisibility(View.GONE);
            if (holder.layoutUserButtons != null) holder.layoutUserButtons.setVisibility(View.GONE);
            holder.btnAddTour.setVisibility(View.VISIBLE);
            holder.btnAddTour.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(v.getContext(), holder.btnAddTour);
                popup.getMenu().add("Add to upcoming trip");
                popup.setOnMenuItemClickListener(item -> {
                    if (item.getTitle().equals("Add to upcoming trip")) {
                        addToUpcoming(tour, v.getContext());
                        return true;
                    }
                    return false;
                });
                popup.show();
            });

        } else {
            // --- FIX LỖI 2 & 3: Đổi getStartDateString() -> getStartDate() ---
            holder.txtStartDate.setVisibility(View.VISIBLE);
            holder.txtStartDate.setText("Start date: " + formatDate(tour.getStartDate()));

            if (holder.txtEndDate != null) {
                holder.txtEndDate.setVisibility(View.VISIBLE);
                holder.txtEndDate.setText("End date: " + formatDate(tour.getEndDate()));
            }

            holder.btnAddTour.setVisibility(View.GONE);
            if (holder.layoutUserButtons != null) holder.layoutUserButtons.setVisibility(View.VISIBLE);

            updateShareButtonUI(holder, tour.isShared());
            holder.btnShareToPublic.setOnClickListener(v -> {
                if (!tour.isShared()) shareTourToPublic(tour, holder);
            });

            if (tour.getImageUrl() != null && !tour.getImageUrl().isEmpty()) {
                holder.btnMemorableVideo.setVisibility(View.VISIBLE);
            } else {
                holder.btnMemorableVideo.setVisibility(View.GONE);
            }
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), TourDetailActivity.class);
            intent.putExtra("tour_item", tour);
            v.getContext().startActivity(intent);
        });
    }

    // Hàm phụ để tính tiền nếu trong Model chưa có logic sẵn
    private int calculateTotal(Tour tour) {
        int total = 0;
        if (tour.getWaypoints() != null) {
            for (Tour.Waypoint wp : tour.getWaypoints()) {
                total += wp.getPrice();
            }
        }
        return total;
    }

    private void addToUpcoming(Tour tour, Context context) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(context, "Please login first!", Toast.LENGTH_SHORT).show();
            return;
        }
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        Map<String, String> body = new HashMap<>();
        body.put("userId", user.getUid());
        apiService.addTourToMyCollection(tour.getId(), body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(context, "Added to upcoming trips!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Failed to add tour.", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(context, "Network error.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateShareButtonUI(ViewHolder holder, boolean isShared) {
        if (holder.btnShareToPublic.getBackground() != null) {
            holder.btnShareToPublic.setBackgroundResource(R.drawable.bg_button_share);
            if (isShared) {
                holder.btnShareToPublic.setText("Shared");
                holder.btnShareToPublic.getBackground().setTint(Color.parseColor("#2E7D32"));
            } else {
                holder.btnShareToPublic.setText("Share journey to public");
                holder.btnShareToPublic.getBackground().setTint(Color.parseColor("#D32F2F"));
            }
        }
    }

    private void shareTourToPublic(Tour tour, ViewHolder holder) {
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        apiService.shareTour(tour.getId()).enqueue(new Callback<Tour>() {
            @Override
            public void onResponse(Call<Tour> call, Response<Tour> response) {
                if (response.isSuccessful()) {
                    tour.setShared(true);
                    updateShareButtonUI(holder, true);
                    Toast.makeText(holder.itemView.getContext(), "Shared successfully!", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<Tour> call, Throwable t) {
                Toast.makeText(holder.itemView.getContext(), "Error sharing", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String formatDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return "--/--/----";
        try {
            // Định dạng ISO từ Backend (có thể có hoặc không có SSS)
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            in.setTimeZone(TimeZone.getTimeZone("UTC"));
            SimpleDateFormat out = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            return out.format(in.parse(dateStr.substring(0, 19)));
        } catch (Exception e) {
            return dateStr;
        }
    }

    @Override
    public int getItemCount() {
        return tourList != null ? tourList.size() : 0;
    }
}