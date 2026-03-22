package com.example.mycurrenttour;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class PopularAdapter extends RecyclerView.Adapter<PopularAdapter.ViewHolder> {

    Context context;
    List<Tour> list;

    public PopularAdapter(Context context, List<Tour> list){
        this.context = context;
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_popular,parent,false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        Tour tour = list.get(position);

        holder.txtName.setText(tour.getTour_name());

        Glide.with(context)
                .load(tour.getImage_url())
                .into(holder.img);

    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder{

        ImageView img;
        TextView txtName;

        public ViewHolder(View itemView){
            super(itemView);

            img = itemView.findViewById(R.id.imgPopular);
            txtName = itemView.findViewById(R.id.txtPopularName);
        }
    }
}