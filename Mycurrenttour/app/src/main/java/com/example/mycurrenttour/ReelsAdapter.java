package com.example.mycurrenttour;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import java.util.ArrayList;
import java.util.List;

public class ReelsAdapter extends RecyclerView.Adapter<ReelsAdapter.ViewHolder> {

    private List<Tour> list;
    private MediaPlayer mediaPlayer; // Để phát nhạc nền cho Reels

    public ReelsAdapter(List<Tour> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Bạn nhớ cập nhật layout item_reel_video.xml (thay VideoView bằng ViewPager2)
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_reel_video, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Tour tour = list.get(position);

        holder.txtTitle.setText(tour.getTitle() != null ? tour.getTitle() : "Exciting trip");
        holder.txtTourNameLink.setText("View detail: " + tour.getTitle());

        // 1. Chuẩn bị danh sách ảnh cho Slideshow bên trong Reel
        List<String> photos = new ArrayList<>();
        if (tour.getImageUrl() != null) photos.add(tour.getImageUrl());
        if (tour.getWaypoints() != null) {
            for (Tour.Waypoint wp : tour.getWaypoints()) {
                if (wp.getPhotos() != null) photos.addAll(wp.getPhotos());
            }
        }

        // 2. Thiết lập ViewPager2 cho slideshow ngang
        SlideshowAdapter slideshowAdapter = new SlideshowAdapter(photos);
        holder.viewPagerReel.setAdapter(slideshowAdapter);

        // Hiệu ứng Fade chuyên nghiệp
        holder.viewPagerReel.setPageTransformer((page, position1) -> {
            page.setAlpha(1 - Math.abs(position1));
        });

        // 3. Tự động chạy slide cho Reel hiện tại
        startAutoSlider(holder.viewPagerReel, photos.size());

        // Click để xem chi tiết
        holder.layoutTourLink.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), TourDetailActivity.class);
            intent.putExtra("tour_item", tour);
            v.getContext().startActivity(intent);
        });
    }

    private void startAutoSlider(ViewPager2 vp, int size) {
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (vp.getAdapter() != null && size > 0) {
                    int nextItem = (vp.getCurrentItem() + 1) % size;
                    vp.setCurrentItem(nextItem, true);
                    handler.postDelayed(this, 3000);
                }
            }
        };
        handler.postDelayed(runnable, 3000);
    }

    @Override
    public int getItemCount() {
        return list != null ? list.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ViewPager2 viewPagerReel; // Thay cho VideoView
        TextView txtTitle, txtTourNameLink;
        View layoutTourLink;

        public ViewHolder(View itemView) {
            super(itemView);
            viewPagerReel = itemView.findViewById(R.id.viewPagerReel); // Đổi ID trong XML
            txtTitle = itemView.findViewById(R.id.txtTitle);
            txtTourNameLink = itemView.findViewById(R.id.txtTourNameLink);
            layoutTourLink = itemView.findViewById(R.id.layoutTourLink);
        }
    }
}