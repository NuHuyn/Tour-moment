package com.example.mycurrenttour;

import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

import eightbitlab.com.blurview.BlurTarget;
import eightbitlab.com.blurview.BlurView;

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
 * Also owns:
 *  - the floating nav capsule's scroll-away/scroll-back slide animation
 *    (BottomNavScrollHelper.NavVisibilityController) - the bar itself (navBarContainer) lives
 *    here, not in any one fragment, so each tab's scroll container (a different widget type per
 *    tab: a NestedScrollView on Discovery, a plain ScrollView on My Travel/Profile, a
 *    RecyclerView on Reels) just reports scroll direction up to whichever HomeActivity instance
 *    is hosting it instead of needing its own copy of the slide logic.
 *  - the capsule's frosted-glass blur setup (setupBlur()) - a one-time wiring of navBlurView to
 *    blurTarget (activity_home.xml), since BlurView needs a concrete target view, not just XML.
 *  - the active-tab switch animation (animateNavItemState()) - now that the pill background
 *    behind the active icon is gone (see ActiveIndicator.MyCurrentTour in themes.xml), the only
 *    visual cue for switching tabs is this: the outgoing tab's icon+label scale back down and fade
 *    to gray, the incoming tab's scale up and fade to green.
 */
public class HomeActivity extends AppCompatActivity implements BottomNavScrollHelper.NavVisibilityController {

    private static final int SLIDE_DURATION_MS = 220;
    private static final int NAV_ITEM_ANIM_MS = 180;
    private static final float NAV_ITEM_ACTIVE_SCALE = 1.1f;

    private MaterialCardView navBarContainer;
    private boolean navHidden = false;
    private int selectedNavItemId = R.id.nav_explore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        navBarContainer = findViewById(R.id.navBarContainer);

        if (savedInstanceState == null) {
            showFragment(new DiscoveryFragment());
        }
        setupBlur();
        setupBottomNav();
    }

    /** Wires navBlurView (activity_home.xml) to blur whatever's drawn inside blurTarget
     *  (fragmentContainer + the vignette scrim) in real time, so scrolled content stays faintly
     *  visible through the floating capsule instead of it being a flat opaque/translucent bar. */
    private void setupBlur() {
        BlurView blurView = findViewById(R.id.navBlurView);
        BlurTarget blurTarget = findViewById(R.id.blurTarget);
        blurView.setupWith(blurTarget)
                .setFrameClearDrawable(new ColorDrawable(ContextCompat.getColor(this, R.color.white)))
                .setBlurRadius(20f);
    }

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottomNavigation);

        // Apply the active-tab look to whichever tab starts selected (nav_explore), without the
        // scroll-in animation - it should just already look active on first frame.
        nav.post(() -> animateNavItemState(nav, selectedNavItemId, true, false));

        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id != selectedNavItemId) {
                animateNavItemState(nav, selectedNavItemId, false, true);
                animateNavItemState(nav, id, true, true);
                selectedNavItemId = id;
            }
            Fragment target;
            if (id == R.id.nav_favorite) target = new ReelsFragment();
            else if (id == R.id.nav_trip) target = new MyTourFragment();
            else if (id == R.id.nav_profile) target = new ProfileFragment();
            else target = new DiscoveryFragment();
            showFragment(target);
            return true;
        });
    }

    /** Snappy scale + color-fade for one nav item's icon+label - toActive drives both the target
     *  scale (1x <-> NAV_ITEM_ACTIVE_SCALE) and target color (gray <-> brand green). Finds the
     *  item's icon/label views by walking its own view tree rather than depending on Material's
     *  internal resource ids, so it degrades gracefully (falls back to the instant color-selector
     *  switch already set on the BottomNavigationView) if that internal structure ever changes. */
    private void animateNavItemState(BottomNavigationView nav, int itemId, boolean toActive, boolean animate) {
        View itemView = nav.findViewById(itemId);
        if (itemView == null) return;

        itemView.animate().cancel();
        float targetScale = toActive ? NAV_ITEM_ACTIVE_SCALE : 1f;
        if (animate) {
            itemView.animate()
                    .scaleX(targetScale)
                    .scaleY(targetScale)
                    .setDuration(NAV_ITEM_ANIM_MS)
                    .setInterpolator(toActive ? new OvershootInterpolator(2.5f) : new DecelerateInterpolator())
                    .start();
        } else {
            itemView.setScaleX(targetScale);
            itemView.setScaleY(targetScale);
        }

        int fromColor = ContextCompat.getColor(this, toActive ? R.color.bottom_nav_inactive_gray : R.color.brand_green);
        int toColor = ContextCompat.getColor(this, toActive ? R.color.brand_green : R.color.bottom_nav_inactive_gray);

        ImageView icon = findFirstImageView(itemView);
        List<TextView> labels = new ArrayList<>();
        collectTextViews(itemView, labels);

        if (!animate) {
            if (icon != null) icon.setImageTintList(ColorStateList.valueOf(toColor));
            for (TextView label : labels) label.setTextColor(toColor);
            return;
        }

        ValueAnimator colorAnim = ValueAnimator.ofArgb(fromColor, toColor);
        colorAnim.setDuration(NAV_ITEM_ANIM_MS);
        colorAnim.addUpdateListener(anim -> {
            int color = (int) anim.getAnimatedValue();
            if (icon != null) icon.setImageTintList(ColorStateList.valueOf(color));
            for (TextView label : labels) label.setTextColor(color);
        });
        colorAnim.start();
    }

    @Nullable
    private static ImageView findFirstImageView(View view) {
        if (view instanceof ImageView) return (ImageView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                ImageView found = findFirstImageView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void collectTextViews(View view, List<TextView> out) {
        if (view instanceof TextView) {
            out.add((TextView) view);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectTextViews(group.getChildAt(i), out);
            }
        }
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
