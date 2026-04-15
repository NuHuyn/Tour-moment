package com.example.mycurrenttour;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.VideoView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ReelsAdapter extends RecyclerView.Adapter<ReelsAdapter.ViewHolder> {

    private List<Tour> list;

    public ReelsAdapter(List<Tour> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_reel_video, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Tour tour = list.get(position);


        String title = tour.getTitle() != null ? tour.getTitle() : "Exciting trip";
        holder.txtTitle.setText(title);
        holder.txtTourNameLink.setText("Book now: " + title);


        String videoUrl = null;

        if (videoUrl != null && !videoUrl.isEmpty()) {
            holder.videoReel.setVideoPath(videoUrl);
        } else {

            int rawId = getRawResourceId(position);
            String path = "android.resource://" + holder.itemView.getContext().getPackageName() + "/" + rawId;
            holder.videoReel.setVideoURI(Uri.parse(path));
        }


        holder.videoReel.setOnPreparedListener(mp -> {
            float videoRatio = mp.getVideoWidth() / (float) mp.getVideoHeight();
            float screenRatio = holder.videoReel.getWidth() / (float) holder.videoReel.getHeight();
            float scale = videoRatio / screenRatio;

            if (scale >= 1f) holder.videoReel.setScaleX(scale);
            else holder.videoReel.setScaleY(1f / scale);

            mp.setLooping(true);
            holder.videoReel.start();
        });


        holder.layoutTourLink.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), TourDetailActivity.class);
            intent.putExtra("tour_item", tour);
            v.getContext().startActivity(intent);
        });
    }

    private int getRawResourceId(int position) {
        switch (position % 4) {
            case 0: return R.raw.v1;
            case 1: return R.raw.v2;
            case 2: return R.raw.v3;
            default: return R.raw.v4;
        }
    }

    @Override
    public int getItemCount() {
        return list != null ? list.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        VideoView videoReel;
        TextView txtTitle, txtTourNameLink;
        View layoutTourLink;

        public ViewHolder(View itemView) {
            super(itemView);
            videoReel = itemView.findViewById(R.id.videoReel);
            txtTitle = itemView.findViewById(R.id.txtTitle);
            txtTourNameLink = itemView.findViewById(R.id.txtTourNameLink);
            layoutTourLink = itemView.findViewById(R.id.layoutTourLink);
        }
    }
}