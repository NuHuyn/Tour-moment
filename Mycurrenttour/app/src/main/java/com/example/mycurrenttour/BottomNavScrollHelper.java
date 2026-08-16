package com.example.mycurrenttour;

import android.view.View;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Shared scroll-direction -> show/hide-the-floating-bottom-nav behavior (HomeActivity owns the
 * actual View + slide animation via NavVisibilityController; this class only turns raw scroll
 * deltas from whichever scroll container a tab uses into hide()/show() calls). One place for this
 * logic instead of each of the 4 tabs (Discovery/My Travel/Reels/Profile) re-implementing its own
 * threshold/debounce, since they don't all use the same scroll widget (NestedScrollView,
 * ScrollView, RecyclerView).
 *
 * Threshold/debounce: each Tracker accumulates raw per-event scroll deltas instead of reacting to
 * every single pixel - a fling/list settling naturally emits many tiny same-direction deltas plus
 * occasional opposite-sign jitter (e.g. RecyclerView's snap/overscroll), and reacting to the very
 * first pixel would flicker the nav in and out. Only once the accumulated movement in one
 * direction crosses THRESHOLD_PX does it actually fire hide()/show(), then resets to zero. A
 * direction reversal resets the accumulator immediately rather than letting the new delta merely
 * cancel the old one out - so a real flick the other way responds right away instead of first
 * having to "pay off" whatever was pending.
 */
public final class BottomNavScrollHelper {

    private static final int THRESHOLD_PX = 24;

    private BottomNavScrollHelper() {}

    public interface NavVisibilityController {
        void showBottomNav();
        void hideBottomNav();
    }

    /** One instance per scrollable container - keeps its own accumulated-delta state. Exposed
     *  directly (not just via the attach() overloads below) for screens like Discovery whose
     *  scroll container already has its own OnScrollChangeListener for something else (the header
     *  fade) - only one listener can be set at a time, so that screen feeds this manually instead
     *  of calling attach(). */
    public static final class Tracker {
        private int pending = 0;
        private final NavVisibilityController controller;

        public Tracker(NavVisibilityController controller) {
            this.controller = controller;
        }

        public void onScrolled(int dy) {
            if (dy == 0) return;
            if ((dy > 0 && pending < 0) || (dy < 0 && pending > 0)) {
                pending = 0;
            }
            pending += dy;
            if (pending > THRESHOLD_PX) {
                controller.hideBottomNav();
                pending = 0;
            } else if (pending < -THRESHOLD_PX) {
                controller.showBottomNav();
                pending = 0;
            }
        }
    }

    public static void attach(NestedScrollView scrollView, NavVisibilityController controller) {
        Tracker tracker = new Tracker(controller);
        scrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (scrollY <= 0) controller.showBottomNav();
            tracker.onScrolled(scrollY - oldScrollY);
        });
    }

    public static void attach(ScrollView scrollView, NavVisibilityController controller) {
        Tracker tracker = new Tracker(controller);
        scrollView.setOnScrollChangeListener((View.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (scrollY <= 0) controller.showBottomNav();
            tracker.onScrolled(scrollY - oldScrollY);
        });
    }

    public static void attach(RecyclerView recyclerView, NavVisibilityController controller) {
        Tracker tracker = new Tracker(controller);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!rv.canScrollVertically(-1)) controller.showBottomNav();
                tracker.onScrolled(dy);
            }
        });
    }
}
