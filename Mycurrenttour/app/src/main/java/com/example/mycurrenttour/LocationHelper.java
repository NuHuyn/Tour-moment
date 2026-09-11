package com.example.mycurrenttour;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wrapper around FusedLocationProviderClient for "where is the user right now" requests - used by
 * OngoingMapActivity's on-demand per-waypoint navigation (Feature 1).
 *
 * Streams updates via requestLocationUpdates(PRIORITY_HIGH_ACCURACY) rather than a single
 * getCurrentLocation() shot: a lone "current location" call can return as soon as Play Services
 * has ANY fix, which right after GPS cold-start can still be a coarse network-based estimate
 * (accuracy in the tens/hundreds of meters) - a real device outdoors typically needs a couple of
 * seconds of satellite lock before accuracy tightens up. This keeps listening (logging every
 * intermediate fix's accuracy - see TAG "LocationHelper" in logcat) and only hands back a fix once
 * it's under GOOD_ENOUGH_ACCURACY_METERS, or the best one seen once TIMEOUT_MS runs out.
 *
 * Bounded by TIMEOUT_MS so a cold GPS start (or no GPS at all, e.g. an emulator) can't hang the UI
 * indefinitely with no feedback. If nothing at all comes back in time, falls back to
 * getLastLocation() (whatever's cached, however old) and tells the caller via the `approximate`
 * flag so it can warn the user rather than silently routing from a stale/wrong position - this is
 * the failure mode that previously produced a 13,000km "route" from a stale Mountain View, CA fix
 * to a Đắk Lắk waypoint on an emulator with no real GPS.
 */
public final class LocationHelper {

    private static final String TAG = "LocationHelper";
    private static final long TIMEOUT_MS = 12_000;
    private static final long UPDATE_INTERVAL_MS = 1_000;
    /** A fix at or under this radius is accepted immediately instead of waiting out the full
     *  timeout for something even tighter - "real fix good enough to route from", not perfection. */
    private static final float GOOD_ENOUGH_ACCURACY_METERS = 20f;

    private LocationHelper() {}

    public static boolean hasLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public interface LocationCallback {
        /** approximate=true means this either came from a cached last-known fix, or is the best
         *  fix seen before timing out without ever reaching GOOD_ENOUGH_ACCURACY_METERS - callers
         *  should warn the user it may be off. */
        void onLocation(double lat, double lng, boolean approximate);
        /** Permission missing, no fix available anywhere (fresh or cached), etc. - always a
         *  user-facing message, never an exception; callers should show it and stop, not crash. */
        void onUnavailable(String message);
    }

    @SuppressLint("MissingPermission") // guarded by hasLocationPermission() before every call site
    public static void getCurrentLocation(Context context, LocationCallback callback) {
        if (!hasLocationPermission(context)) {
            callback.onUnavailable("Chưa có quyền truy cập vị trí.");
            return;
        }
        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(context);
        Handler handler = new Handler(Looper.getMainLooper());
        AtomicBoolean resolved = new AtomicBoolean(false);
        Location[] best = new Location[1]; // best (lowest-accuracy-number) fix seen so far, if any
        long startElapsed = SystemClock.elapsedRealtime();

        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS).build();

        com.google.android.gms.location.LocationCallback gmsCallback = new com.google.android.gms.location.LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                Location location = result.getLastLocation();
                if (location == null || resolved.get()) return;

                long elapsedMs = SystemClock.elapsedRealtime() - startElapsed;
                Log.d(TAG, String.format(Locale.US,
                        "Fix at +%dms: lat=%.6f lng=%.6f accuracy=%.1fm",
                        elapsedMs, location.getLatitude(), location.getLongitude(), location.getAccuracy()));

                if (best[0] == null || location.getAccuracy() < best[0].getAccuracy()) {
                    best[0] = location;
                }

                if (location.getAccuracy() <= GOOD_ENOUGH_ACCURACY_METERS && resolved.compareAndSet(false, true)) {
                    handler.removeCallbacksAndMessages(null);
                    client.removeLocationUpdates(this);
                    callback.onLocation(location.getLatitude(), location.getLongitude(), false);
                }
            }
        };

        Runnable onTimeout = () -> {
            if (!resolved.compareAndSet(false, true)) return;
            client.removeLocationUpdates(gmsCallback);
            if (best[0] != null) {
                Log.d(TAG, String.format(Locale.US,
                        "Timed out after %dms without reaching %.0fm accuracy - using best fix seen: accuracy=%.1fm",
                        TIMEOUT_MS, GOOD_ENOUGH_ACCURACY_METERS, best[0].getAccuracy()));
                callback.onLocation(best[0].getLatitude(), best[0].getLongitude(), true);
            } else {
                Log.d(TAG, "Timed out with no fix at all - falling back to getLastLocation()");
                fallbackToLastLocation(context, client, callback);
            }
        };
        handler.postDelayed(onTimeout, TIMEOUT_MS);

        client.requestLocationUpdates(request, gmsCallback, Looper.getMainLooper())
                .addOnFailureListener(e -> {
                    if (!resolved.compareAndSet(false, true)) return;
                    handler.removeCallbacksAndMessages(null);
                    Log.e(TAG, "requestLocationUpdates failed", e);
                    fallbackToLastLocation(context, client, callback);
                });
    }

    @SuppressLint("MissingPermission") // same guard as getCurrentLocation() - only reached from there
    private static void fallbackToLastLocation(Context context, FusedLocationProviderClient client, LocationCallback callback) {
        if (!hasLocationPermission(context)) {
            callback.onUnavailable("Chưa có quyền truy cập vị trí.");
            return;
        }
        client.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        Log.d(TAG, String.format(Locale.US,
                                "getLastLocation(): lat=%.6f lng=%.6f accuracy=%.1fm age=%dms",
                                location.getLatitude(), location.getLongitude(), location.getAccuracy(),
                                SystemClock.elapsedRealtime() - location.getElapsedRealtimeNanos() / 1_000_000));
                        callback.onLocation(location.getLatitude(), location.getLongitude(), true);
                    } else {
                        Log.d(TAG, "getLastLocation() returned null - no fix available anywhere");
                        callback.onUnavailable("Không lấy được vị trí hiện tại. Hãy bật GPS/Vị trí rồi thử lại.");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "getLastLocation() failed", e);
                    callback.onUnavailable("Lỗi khi lấy vị trí: " + e.getMessage());
                });
    }
}
