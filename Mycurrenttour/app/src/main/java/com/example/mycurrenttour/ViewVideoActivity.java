package com.example.mycurrenttour;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.squareup.picasso.Picasso;

import java.util.List;

public class ViewVideoActivity extends AppCompatActivity {

    private ImageView imgSlideShow;
    private List<String> photoList;
    private int currentIndex = 0;
    private final Handler handler = new Handler();
    private Runnable slideRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_video);

        imgSlideShow = findViewById(R.id.imgSlideShow);

        photoList = getIntent().getStringArrayListExtra("PHOTO_LIST");

        if (photoList == null || photoList.isEmpty()) {
            Toast.makeText(this, "No image data to display.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        startSlideShow();
    }

    private void startSlideShow() {
        slideRunnable = new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isDestroyed()) return;

                String currentPhotoUrl = photoList.get(currentIndex);

                Picasso.get()
                        .load(currentPhotoUrl)
                        .placeholder(R.drawable.centralvietnam)
                        .error(R.drawable.centralvietnam)
                        .into(imgSlideShow);

                currentIndex = (currentIndex + 1) % photoList.size();

                handler.postDelayed(this, 3000);
            }
        };
        handler.post(slideRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (handler != null && slideRunnable != null) {
            handler.removeCallbacks(slideRunnable);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (photoList != null && !photoList.isEmpty()) {
            handler.post(slideRunnable);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && slideRunnable != null) {
            handler.removeCallbacks(slideRunnable);
        }
    }
}