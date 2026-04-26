package com.example.mycurrenttour;

import android.content.Intent;
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

    public ReelsAdapter(List<Tour> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_reel_video, parent, false);
        ViewHolder holder = new ViewHolder(view);

        // TỐI ƯU: Thiết lập Transformer tại đây để không phải chạy lại trong onBind
        holder.viewPagerReel.setPageTransformer((page, pos) -> {
            page.setTranslationX(-pos * page.getWidth());
            if (pos < -1 || pos > 1) {
                page.setAlpha(0f);
            } else {
                float alpha = 1 - Math.abs(pos);
                page.setAlpha(alpha);
                float scale = 1.0f + (alpha * 0.05f);
                page.setScaleX(scale);
                page.setScaleY(scale);
            }
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Tour tour = list.get(position);

        holder.txtTitle.setText(tour.getTitle() != null ? tour.getTitle() : "Exciting trip");
        holder.txtTourNameLink.setText("View detail: " + tour.getTitle());

        // 1. Chuẩn bị ảnh
        List<String> photos = new ArrayList<>();
        if (tour.getImageUrl() != null && !tour.getImageUrl().isEmpty()) {
            photos.add(tour.getImageUrl());
        }

        if (tour.getWaypoints() != null) {
            for (Tour.Waypoint wp : tour.getWaypoints()) {
                // Kiểm tra an toàn trước khi addAll
                List<String> wpPhotos = wp.getPhotos();
                if (wpPhotos != null && !wpPhotos.isEmpty()) {
                    photos.addAll(wpPhotos);
                }
            }
        }

        // 2. Thiết lập Slideshow
        SlideshowAdapter slideshowAdapter = new SlideshowAdapter(photos);
        holder.viewPagerReel.setAdapter(slideshowAdapter);
        holder.viewPagerReel.setOffscreenPageLimit(3);

        // 3. Chạy slider tự động
        holder.startAutoSlider(photos.size());

        holder.layoutTourLink.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), TourDetailActivity.class);
            intent.putExtra("tour_item", tour);
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return (list != null) ? list.size() : 0;
    }

    // QUAN TRỌNG: Dừng slider khi item bị cuộn đi để giải phóng bộ nhớ
    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        holder.stopAutoSlider();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ViewPager2 viewPagerReel;
        TextView txtTitle, txtTourNameLink;
        View layoutTourLink;

        private final Handler handler = new Handler(Looper.getMainLooper());
        private Runnable runnable;

        public ViewHolder(View itemView) {
            super(itemView);
            viewPagerReel = itemView.findViewById(R.id.viewPagerReel);
            txtTitle = itemView.findViewById(R.id.txtTitle);
            txtTourNameLink = itemView.findViewById(R.id.txtTourNameLink);
            layoutTourLink = itemView.findViewById(R.id.layoutTourLink);
        }

        public void startAutoSlider(int size) {
            stopAutoSlider();
            if (size <= 1) return;

            runnable = new Runnable() {
                @Override
                public void run() {
                    if (viewPagerReel != null && viewPagerReel.getAdapter() != null) {
                        int currentItem = viewPagerReel.getCurrentItem();
                        int nextItem = (currentItem + 1) % size; // Chạy lặp lại (Loop) cho giống Reel
                        viewPagerReel.setCurrentItem(nextItem, true);
                        handler.postDelayed(this, 3500);
                    }
                }
            };
            handler.postDelayed(runnable, 3500);
        }

        public void stopAutoSlider() {
            handler.removeCallbacksAndMessages(null);
        }
    }
}