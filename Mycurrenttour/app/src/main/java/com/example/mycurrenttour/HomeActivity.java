package com.example.mycurrenttour;

import android.os.Bundle;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;

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
 *
 * Also owns the floating nav bar's scroll-away/scroll-back slide animation
 * (BottomNavScrollHelper.NavVisibilityController) - the bar itself (navBarContainer) lives here,
 * not in any one fragment, so each tab's scroll container (a different widget type per tab: a
 * NestedScrollView on Discovery, a plain ScrollView on My Travel/Profile, a RecyclerView on Reels)
 * just reports scroll direction up to whichever HomeActivity instance is hosting it instead of
 * needing its own copy of the slide logic.
 */
public class HomeActivity extends AppCompatActivity implements BottomNavScrollHelper.NavVisibilityController {

    private static final int SLIDE_DURATION_MS = 220;

    private MaterialCardView navBarContainer;
    private boolean navHidden = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        navBarContainer = findViewById(R.id.navBarContainer);

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
        // Switching tabs always lands with the nav bar visible, regardless of how the previous
        // tab's scroll position left it hidden.
        showBottomNav();
        getSupportFragmentManager()
                .beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }

    @Override
    public void showBottomNav() {
        if (!navHidden) return;
        navHidden = false;
        navBarContainer.animate().cancel();
        navBarContainer.animate()
                .translationY(0f)
                .setDuration(SLIDE_DURATION_MS)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    @Override
    public void hideBottomNav() {
        if (navHidden) return;
        navHidden = true;
        navBarContainer.animate().cancel();
        int bottomMargin = ((ViewGroup.MarginLayoutParams) navBarContainer.getLayoutParams()).bottomMargin;
        // + a little extra so the card's own drop shadow clears the screen edge too, not just the
        // card itself.
        float hideDistance = navBarContainer.getHeight() + bottomMargin + 24f;
        navBarContainer.animate()
                .translationY(hideDistance)
                .setDuration(SLIDE_DURATION_MS)
                .setInterpolator(new AccelerateInterpolator())
                .start();
    }
}
