package com.example.mycurrenttour;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
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


        holder.txtTitle.setText(tour.getTitle() != null ? tour.getTitle() : "Self-guided trip");
        holder.txtPrice.setText("Total expense: $" + tour.getTotalPrice());
        holder.txtStartDate.setText("Start date: " + formatDate(tour.getStartDateString()));

        Picasso.get()
                .load(tour.getImageUrl())
                .fit()
                .centerCrop()
                .placeholder(R.drawable.centralvietnam)
                .error(R.drawable.centralvietnam)
                .into(holder.imgTour);


        if (isHomePage) {

            holder.btnShareToPublic.setVisibility(View.GONE);
            holder.btnMemorableVideo.setVisibility(View.GONE);
        } else {

            holder.btnShareToPublic.setVisibility(View.VISIBLE);
            holder.btnMemorableVideo.setVisibility(View.VISIBLE);


            updateShareButtonUI(holder, tour.isShared());


            holder.btnShareToPublic.setOnClickListener(v -> {
                if (!tour.isShared()) {
                    shareTourToPublic(tour, holder);
                } else {
                    Toast.makeText(v.getContext(), "This tour has already been shared!", Toast.LENGTH_SHORT).show();
                }
            });


            holder.btnMemorableVideo.setOnClickListener(v -> {
                Intent intent = new Intent(v.getContext(), CreateVideoActivity.class);
                intent.putExtra("tour_item", tour);
                v.getContext().startActivity(intent);
            });
        }


        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), TourDetailActivity.class);
            intent.putExtra("tour_item", tour);
            v.getContext().startActivity(intent);
        });
    }

    private void updateShareButtonUI(ViewHolder holder, boolean isShared) {
        if (isShared) {
            holder.btnShareToPublic.setText("Shared");
            holder.btnShareToPublic.setTextColor(Color.WHITE);
            holder.btnShareToPublic.setBackgroundResource(R.drawable.bg_button_share);
            holder.btnShareToPublic.getBackground().setColorFilter(
                    Color.parseColor("#2E7D32"), PorterDuff.Mode.SRC_IN);
        } else {
            holder.btnShareToPublic.setText("Share journey to public");
            holder.btnShareToPublic.setTextColor(Color.WHITE);
            holder.btnShareToPublic.setBackgroundResource(R.drawable.bg_button_share);
            holder.btnShareToPublic.getBackground().setColorFilter(
                    Color.parseColor("#D32F2F"), PorterDuff.Mode.SRC_IN);
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