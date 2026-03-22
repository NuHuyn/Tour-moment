package com.example.mycurrenttour;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TourAdapter extends RecyclerView.Adapter<TourAdapter.ViewHolder> {

    List<Tour> tourList;

    public TourAdapter(List<Tour> tourList) {
        this.tourList = tourList;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        ImageView imgTour;
        TextView txtTitle;
        TextView txtPrice;

        TextView txtStartDate;
        TextView txtEndDate;

        public ViewHolder(View itemView) {
            super(itemView);

            imgTour = itemView.findViewById(R.id.imgTour);
            txtTitle = itemView.findViewById(R.id.txtTitle);
            txtPrice = itemView.findViewById(R.id.txtPrice);
            txtStartDate = itemView.findViewById(R.id.txtStartDate);
            txtEndDate = itemView.findViewById(R.id.txtEndDate);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tour, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {

        Tour tour = tourList.get(position);

        // set dữ liệu
        holder.txtTitle.setText(tour.getTour_name());
        holder.txtPrice.setText("Trip expense : $" + tour.getPrice());

        try {

            if (tour.getSchedule() != null) {

                SimpleDateFormat inputFormat =
                        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault());

                SimpleDateFormat outputFormat =
                        new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

                Date start = inputFormat.parse(tour.getSchedule().getStart_date());
                Date end = inputFormat.parse(tour.getSchedule().getEnd_date());

                String startFormatted = outputFormat.format(start);
                String endFormatted = outputFormat.format(end);

                holder.txtStartDate.setText("Start date: " + startFormatted);
                holder.txtEndDate.setText("End date: " + endFormatted);

            } else {
                holder.txtStartDate.setText("Start date: N/A");
                holder.txtEndDate.setText("End date: N/A");
            }

        } catch (Exception e) {
            holder.txtStartDate.setText("Start date: N/A");
            holder.txtEndDate.setText("End date: N/A");
        }

        // load ảnh từ URL
        Glide.with(holder.itemView.getContext())
                .load(tour.getImage_url())
                .into(holder.imgTour);

        // click mở chi tiết tour
        holder.itemView.setOnClickListener(v -> {

            Intent intent = new Intent(v.getContext(), TourDetailActivity.class);

            intent.putExtra("title", tour.getTour_name());
            intent.putExtra("price", tour.getPrice());
            intent.putExtra("image", tour.getImage_url());

            intent.putExtra("location", tour.getLocation());

            if (tour.getSchedule() != null) {
                intent.putExtra("start_date", tour.getSchedule().getStart_date());
            }


            v.getContext().startActivity(intent);
        });

    }

    @Override
    public int getItemCount() {
        return tourList.size();
    }
}