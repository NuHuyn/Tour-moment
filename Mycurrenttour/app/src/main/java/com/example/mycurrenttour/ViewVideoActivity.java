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

        // Nhận danh sách ảnh từ Intent
        photoList = getIntent().getStringArrayListExtra("PHOTO_LIST");

        if (photoList == null || photoList.isEmpty()) {
            Toast.makeText(this, "Không có dữ liệu ảnh để hiển thị!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Thêm nút đóng hoặc xử lý Back nếu cần
        startSlideShow();
    }

    private void startSlideShow() {
        slideRunnable = new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isDestroyed()) return;

                String currentPhotoUrl = photoList.get(currentIndex);

                // Đồng bộ Placeholder với các màn hình khác (R.drawable.centralvietnam)
                Picasso.get()
                        .load(currentPhotoUrl) // Picasso có thể nhận String trực tiếp, không cần parse Uri thủ công
                        .placeholder(R.drawable.centralvietnam)
                        .error(R.drawable.centralvietnam)
                        .into(imgSlideShow);

                // Loop danh sách
                currentIndex = (currentIndex + 1) % photoList.size();

                // 3 giây chuyển 1 lần
                handler.postDelayed(this, 3000);
            }
        };
        handler.post(slideRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Tạm dừng slide khi người dùng thoát ra ngoài (nhận cuộc gọi, v.v.)
        if (handler != null && slideRunnable != null) {
            handler.removeCallbacks(slideRunnable);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Tiếp tục chạy slide khi quay lại
        if (photoList != null && !photoList.isEmpty()) {
            handler.post(slideRunnable);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Dừng triệt để để tránh Memory Leak
        if (handler != null && slideRunnable != null) {
            handler.removeCallbacks(slideRunnable);
        }
    }
}