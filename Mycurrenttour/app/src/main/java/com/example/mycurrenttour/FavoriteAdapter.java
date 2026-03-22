package com.example.mycurrenttour;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class FavoriteAdapter extends RecyclerView.Adapter<FavoriteAdapter.ViewHolder> {

    List<Tour> list;

    public FavoriteAdapter(List<Tour> list) {
        this.list = list;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        ImageView imgTour;
        TextView txtTitle, txtPrice;

        public ViewHolder(View itemView) {
            super(itemView);

            imgTour = itemView.findViewById(R.id.imgTour);
            txtTitle = itemView.findViewById(R.id.txtTitle);
            txtPrice = itemView.findViewById(R.id.txtPrice);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_favorite_tour, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {

        Tour tour = list.get(position);

        holder.txtTitle.setText(tour.getTour_name());
        holder.txtPrice.setText(String.valueOf(tour.getPrice()));

    }

    @Override
    public int getItemCount() {
        return list.size();
    }
}