package com.example.mycurrenttour;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

/**
 * Stable per-install device identifier, used as the identity for the server-side waypoint-unlock
 * paywall (see tour-backend's WaypointUnlock model / waypointVisibility.js) - this app's unlock
 * flow works for guests too (no login required), so entitlement can't be keyed on a googleId.
 *
 * Generated once on first use and persisted in SharedPreferences (same "survives restarts, not
 * reinstalls" tradeoff as WaypointLockManager's own local cache) - a random UUID rather than
 * Settings.Secure.ANDROID_ID so behavior doesn't depend on OEM-specific ANDROID_ID quirks/resets.
 */
public final class DeviceIdProvider {

    private static final String PREFS_NAME = "device_id_prefs";
    private static final String KEY_DEVICE_ID = "device_id";

    private DeviceIdProvider() {}

    public static String getOrCreate(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String existing = prefs.getString(KEY_DEVICE_ID, null);
        if (existing != null && !existing.isEmpty()) return existing;

        String generated = UUID.randomUUID().toString();
        prefs.edit().putString(KEY_DEVICE_ID, generated).apply();
        return generated;
    }
}
