package com.example.mycurrenttour;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Tracks which Discovery tours the current user has already tapped "+ Add this Tour" for, so
 * TourAdapter can show "Added" again after scrolling/re-opening Discovery instead of resetting
 * to "+ Add this Tour" on every RecyclerView rebind (view holders get reused, so without this the
 * button state would leak between unrelated tours or reset entirely). Persisted via
 * SharedPreferences (same style as WaypointLockManager), keyed by tourId so it survives closing
 * and reopening the app, and scoped per current userId (SessionManager.getUserId - a real Google
 * account id or a local guest id, see SessionManager) so different accounts/guests sharing a
 * device don't see each other's "added" state.
 *
 * This only tracks local UI state - the actual copy lives server-side as a new Tour document
 * (see TourAdapter.copyTour / POST /api/tours/copy/:tourId). It has no way to know if the same
 * tour was already copied from a different device; it's a convenience marker, not a source of
 * truth.
 */
public class AddedTourManager {

    private static final String PREFS_NAME = "added_tour_prefs";

    private AddedTourManager() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String key(String userId, String originalTourId) {
        return userId + "_" + originalTourId;
    }

    /** Has this user already added this Discovery tour to their My Travel? */
    public static boolean isAdded(Context context, String userId, String originalTourId) {
        if (userId == null || originalTourId == null) return false;
        return prefs(context).getBoolean(key(userId, originalTourId), false);
    }

    /** Call after a successful copyTour API response. */
    public static void markAdded(Context context, String userId, String originalTourId) {
        if (userId == null || originalTourId == null) return;
        prefs(context).edit().putBoolean(key(userId, originalTourId), true).apply();
    }
}
