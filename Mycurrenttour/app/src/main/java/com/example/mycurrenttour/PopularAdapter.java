package com.example.mycurrenttour;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;

import java.util.List;

public class PopularAdapter extends RecyclerView.Adapter<PopularAdapter.ViewHolder> {

    private Context context;
    private List<Tour> list;

    public PopularAdapter(Context context, List<Tour> list){
        this.context = context;
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_popular, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Tour tour = list.get(position);


        holder.txtName.setText(tour.getTitle() != null ? tour.getTitle() : "Popular Tour");


        String imageUrl = tour.getImageUrl();

        Picasso.get()
                .load(imageUrl)
                .fit()
                .centerCrop()
                .placeholder(R.drawable.centralvietnam)
                .error(R.drawable.centralvietnam)
                .into(holder.img);

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, TourDetailActivity.class);
            intent.putExtra("tour_item", tour);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return list != null ? list.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView img;
        TextView txtName;

        public ViewHolder(View itemView){
            super(itemView);
            img = itemView.findViewById(R.id.imgPopular);
            txtName = itemView.findViewById(R.id.txtPopularName);
        }
    }
}