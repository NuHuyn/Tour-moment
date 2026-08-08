package com.example.mycurrenttour;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Demo waypoint-lock / paywall (chưa gắn cổng thanh toán thật, chỉ mô phỏng UI cho báo cáo đồ án).
 * TODO: thay bằng logic khóa dựa trên thanh toán thật khi có backend.
 *
 * Đây là NGUỒN TRẠNG THÁI KHÓA DUY NHẤT, dùng chung cho cả màn Discovery
 * (TourDetailActivity) và My Travel (OngoingMapActivity) để 2 màn luôn khớp nhau,
 * và lưu bằng SharedPreferences (theo tourId) nên thoát app rồi vào lại vẫn còn.
 *
 * Luật:
 * - 2 waypoint đầu tiên của mỗi tour luôn miễn phí / mở sẵn.
 * - Từ waypoint thứ 3 trở đi: khóa mặc định, icon ổ khóa xanh lá cạnh icon chỉ đường;
 *   icon chỉ đường bị disable cho tới khi waypoint đó được mở.
 *   + Bấm icon ổ khóa của 1 step: trả 2.000đ để mở đúng step đó (unlockWaypoint).
 *   + Nút "Unlock" (full trip): mở hết toàn bộ waypoint còn khóa 1 lần, giảm 25% so với
 *     mua lẻ (vd 3 waypoint khóa: mua lẻ 3 x 2.000đ = 6.000đ, full chỉ 4.500đ).
 */
public class WaypointLockManager {

    private static final String PREFS_NAME = "waypoint_lock_prefs";
    private static final int FREE_WAYPOINTS = 2;

    public static final int PRICE_PER_WAYPOINT_VND = 2000;
    public static final double UNLOCK_FULL_DISCOUNT = 0.25;

    private WaypointLockManager() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String key(String tourId, int index) {
        return tourId + "_wp_" + index;
    }

    /** Waypoint tại index có đang mở hay không (2 waypoint đầu luôn true). */
    public static boolean isUnlocked(Context context, String tourId, int index) {
        if (index < FREE_WAYPOINTS) return true;
        if (tourId == null) return true; // an toàn: không có id thì không áp khóa
        return prefs(context).getBoolean(key(tourId, index), false);
    }

    /** Danh sách vị trí đang khóa của 1 tour — đưa thẳng vào WaypointViewAdapter.setLockedPositions(). */
    public static Set<Integer> getLockedPositions(Context context, String tourId, int totalWaypoints) {
        Set<Integer> locked = new LinkedHashSet<>();
        for (int i = 0; i < totalWaypoints; i++) {
            if (!isUnlocked(context, tourId, i)) locked.add(i);
        }
        return locked;
    }

    /** Mở đúng 1 waypoint được chỉ định (bấm icon ổ khóa của step nào thì mở step đó, trả 2.000đ). */
    public static void unlockWaypoint(Context context, String tourId, int index) {
        if (tourId == null || index < FREE_WAYPOINTS) return;
        prefs(context).edit().putBoolean(key(tourId, index), true).apply();
    }

    /** Unlock full: mở hết toàn bộ waypoint còn khóa của tour cùng lúc. */
    public static void unlockAll(Context context, String tourId, int totalWaypoints) {
        if (tourId == null) return;
        SharedPreferences.Editor editor = prefs(context).edit();
        for (int i = FREE_WAYPOINTS; i < totalWaypoints; i++) {
            editor.putBoolean(key(tourId, i), true);
        }
        editor.apply();
    }

    /** Giá mở lẻ đúng 1 waypoint (bấm icon ổ khóa của step đó). */
    public static int stepPriceVnd() {
        return PRICE_PER_WAYPOINT_VND;
    }

    /** Giá "Unlock full" cho toàn bộ waypoint đang khóa còn lại (đã giảm 25%). */
    public static int fullUnlockPriceVnd(int remainingLockedCount) {
        int individualTotal = remainingLockedCount * PRICE_PER_WAYPOINT_VND;
        return (int) Math.round(individualTotal * (1 - UNLOCK_FULL_DISCOUNT));
    }
}
