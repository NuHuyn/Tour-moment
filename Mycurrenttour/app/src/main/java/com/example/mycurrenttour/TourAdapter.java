package com.example.mycurrenttour;

import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;

import java.text.SimpleDateFormat;
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
        ImageView imgTour;
        TextView txtTitle, txtPrice, txtStartDate;
        TextView btnShareToPublic, btnMemorableVideo;

        public ViewHolder(View itemView) {
            super(itemView);
            imgTour = itemView.findViewById(R.id.imgTourLogItem);
            txtTitle = itemView.findViewById(R.id.txtLogTourNameItem);
            txtPrice = itemView.findViewById(R.id.txtLogExpenseItem);
            txtStartDate = itemView.findViewById(R.id.txtLogDateItem);
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

        // Hiển thị thông tin cơ bản
        holder.txtTitle.setText(tour.getTitle() != null ? tour.getTitle() : "Self-guided trip");
        holder.txtPrice.setText("Total expense: $" + tour.getTotalPrice());
        holder.txtStartDate.setText("Start date: " + formatDate(tour.getStartDateString()));

        // Load ảnh bìa tour
        Picasso.get()
                .load(tour.getImageUrl())
                .fit()
                .centerCrop()
                .placeholder(R.drawable.centralvietnam)
                .error(R.drawable.centralvietnam)
                .into(holder.imgTour);

        // Xử lý hiển thị các nút chức năng
        if (isHomePage) {
            holder.btnMemorableVideo.setVisibility(View.GONE);
            holder.btnShareToPublic.setVisibility(View.GONE);
        } else {
            // Hiển thị nút Video Slideshow nếu có ảnh
            if ((tour.getImageUrl() != null && !tour.getImageUrl().isEmpty()) ||
                    (tour.getWaypoints() != null && !tour.getWaypoints().isEmpty())) {
                holder.btnMemorableVideo.setVisibility(View.VISIBLE);
                holder.btnMemorableVideo.setOnClickListener(v -> {
                    Intent intent = new Intent(v.getContext(), VideoPlayerActivity.class);
                    intent.putExtra("tour_item", tour);
                    v.getContext().startActivity(intent);
                });
            } else {
                holder.btnMemorableVideo.setVisibility(View.GONE);
            }

            // Hiển thị nút Share
            holder.btnShareToPublic.setVisibility(View.VISIBLE);
            updateShareButtonUI(holder, tour.isShared());
            holder.btnShareToPublic.setOnClickListener(v -> {
                if (!tour.isShared()) {
                    shareTourToPublic(tour, holder);
                } else {
                    Toast.makeText(v.getContext(), "This tour has already been shared!", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Click vào item để xem chi tiết
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), TourDetailActivity.class);
            intent.putExtra("tour_item", tour);
            v.getContext().startActivity(intent);
        });
    }

    // Hàm cập nhật màu sắc nút Share (Đã sửa lỗi setColorFilter)
    private void updateShareButtonUI(ViewHolder holder, boolean isShared) {
        holder.btnShareToPublic.setBackgroundResource(R.drawable.bg_button_share);
        if (isShared) {
            holder.btnShareToPublic.setText("Shared");
            holder.btnShareToPublic.setTextColor(Color.WHITE);
            holder.btnShareToPublic.getBackground().setTint(Color.parseColor("#2E7D32"));
        } else {
            holder.btnShareToPublic.setText("Share journey to public");
            holder.btnShareToPublic.setTextColor(Color.WHITE);
            holder.btnShareToPublic.getBackground().setTint(Color.parseColor("#D32F2F"));
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
                    Toast.makeText(holder.itemView.getContext(), "Now visible on the Home page!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(holder.itemView.getContext(), "Server error: Unable to share", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<Tour> call, Throwable t) {
                Toast.makeText(holder.itemView.getContext(), "Network connection error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return tourList != null ? tourList.size() : 0;
    }

    private String formatDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return "--/--/----";
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
            in.setTimeZone(TimeZone.getTimeZone("UTC"));
            SimpleDateFormat out = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date date = in.parse(dateStr);
            return out.format(date);
        } catch (Exception e) {
            return dateStr.contains("T") ? dateStr.split("T")[0] : dateStr;
        }
    }
}