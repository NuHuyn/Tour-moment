package com.example.mycurrenttour;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Single-Activity shell for the 4 bottom-nav tabs (Discovery / My Travel / My Tour / My Profile).
 *
 * Trước đây mỗi tab là 1 Activity riêng (startActivity), nên bottomNavigation chỉ tồn tại trong
 * layout của HomeActivity - hễ bấm sang tab khác là cả thanh nav biến mất luôn vì Activity mới
 * không có nó. Giờ chuyển sang kiến trúc chuẩn: bottomNavigation nằm cố định trong
 * activity_home.xml, nội dung từng tab là 1 Fragment được swap vào fragmentContainer, giống
 * hành vi Facebook - thanh nav không bao giờ biến mất khi đổi tab.
 *
 * Các màn chi tiết (TourDetailActivity, OngoingMapActivity, CreateTourActivity, ...) vẫn là
 * Activity riêng như cũ - chỉ 4 tab cấp cao nhất mới cần là Fragment.
 */
public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        if (savedInstanceState == null) {
            showFragment(new DiscoveryFragment());
        }
        setupBottomNav();
    }

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottomNavigation);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment target;
            if (id == R.id.nav_favorite) target = new ReelsFragment();
            else if (id == R.id.nav_trip) target = new MyTourFragment();
            else if (id == R.id.nav_profile) target = new ProfileFragment();
            else target = new DiscoveryFragment();
            showFragment(target);
            return true;
        });
    }

    private void showFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }
}
