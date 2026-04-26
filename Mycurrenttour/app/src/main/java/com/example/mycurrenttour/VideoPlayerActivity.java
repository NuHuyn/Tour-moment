package com.example.mycurrenttour;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import java.util.ArrayList;
import java.util.List;

public class VideoPlayerActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private Handler sliderHandler = new Handler(Looper.getMainLooper());
    private List<String> photos = new ArrayList<>();
    private MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        viewPager = findViewById(R.id.viewPagerSlideshow);
        findViewById(R.id.btnBackVideo).setOnClickListener(v -> finish());

        initMusic();

        Tour tour = (Tour) getIntent().getSerializableExtra("tour_item");
        if (tour != null) {
            if (tour.getImageUrl() != null) photos.add(tour.getImageUrl());
            if (tour.getWaypoints() != null) {
                for (Tour.Waypoint wp : tour.getWaypoints()) {
                    if (wp.getPhotos() != null) photos.addAll(wp.getPhotos());
                }
            }

            SlideshowAdapter adapter = new SlideshowAdapter(photos);
            viewPager.setAdapter(adapter);

            setupTransformer();

            startSlideshow();
        }
    }

    private void setupTransformer() {
        viewPager.setPageTransformer((page, position) -> {
            page.setAlpha(0f);
            page.setVisibility(View.VISIBLE);

            if (position >= -1 && position <= 1) {
                page.setAlpha(1 - Math.abs(position));

                float scaleFactor = 1.1f - Math.abs(position) * 0.1f;
                page.setScaleX(scaleFactor);
                page.setScaleY(scaleFactor);
            }
        });
    }

    private void initMusic() {
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.background_music);
            if (mediaPlayer != null) {
                mediaPlayer.setLooping(true);
                mediaPlayer.setVolume(1.0f, 1.0f);
                mediaPlayer.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Runnable sliderRunnable = new Runnable() {
        @Override
        public void run() {
            if (photos.isEmpty()) return;

            int currentItem = viewPager.getCurrentItem();
            int nextItem = (currentItem < photos.size() - 1) ? currentItem + 1 : 0;

            viewPager.setCurrentItem(nextItem, true);

            sliderHandler.postDelayed(this, 3000);
        }
    };

    private void startSlideshow() {
        sliderHandler.removeCallbacks(sliderRunnable);
        sliderHandler.postDelayed(sliderRunnable, 3000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startSlideshow();
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sliderHandler.removeCallbacks(sliderRunnable);
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sliderHandler.removeCallbacks(sliderRunnable);
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}