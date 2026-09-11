package com.example.mycurrenttour;

import android.content.Context;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Waypoint unlock actions - now backed by the real server-side paywall (tour-backend's
 * WaypointUnlock model + waypointVisibility.js redaction), not a local simulation.
 *
 * Previously this class WAS the entire paywall: "locked" was computed locally (a hardcoded "first
 * 2 waypoints free, rest locked" rule) and persisted only in SharedPreferences, so a locked
 * waypoint's real name/coordinates were already sitting in the Tour object the server had sent -
 * hiding them was UI-only and trivially bypassed by inspecting the network response. That data is
 * now redacted server-side and only revealed per-device once this class's unlock calls succeed -
 * see Tour.Waypoint.isLocked(), which is now the single source of truth for lock state (set by the
 * server on every fetch), not this class.
 *
 * TODO: still no real payment gateway - OngoingMapActivity's "Pay" dialog calls unlockWaypoint/
 * unlockAllWaypoints immediately on tap, with no actual charge. That part of the demo is unchanged.
 */
public final class WaypointLockManager {

    public static final double UNLOCK_FULL_DISCOUNT = 0.25;

    private WaypointLockManager() {}

    public interface UnlockCallback {
        void onSuccess();
        void onFailure(String message);
    }

    public static void unlockWaypoint(Context context, String tourId, int index, UnlockCallback callback) {
        String deviceId = DeviceIdProvider.getOrCreate(context);
        ApiService api = ApiClient.getClient().create(ApiService.class);
        api.unlockWaypoint(tourId, index, new ApiService.DeviceIdRequest(deviceId))
                .enqueue(new Callback<ApiService.UnlockResponse>() {
                    @Override
                    public void onResponse(Call<ApiService.UnlockResponse> call, Response<ApiService.UnlockResponse> response) {
                        if (response.isSuccessful()) callback.onSuccess();
                        else callback.onFailure("Mở khóa thất bại (mã lỗi " + response.code() + ")");
                    }

                    @Override
                    public void onFailure(Call<ApiService.UnlockResponse> call, Throwable t) {
                        callback.onFailure("Không thể kết nối máy chủ: " + t.getMessage());
                    }
                });
    }

    public static void unlockAllWaypoints(Context context, String tourId, UnlockCallback callback) {
        String deviceId = DeviceIdProvider.getOrCreate(context);
        ApiService api = ApiClient.getClient().create(ApiService.class);
        api.unlockAllWaypoints(tourId, new ApiService.DeviceIdRequest(deviceId))
                .enqueue(new Callback<ApiService.UnlockResponse>() {
                    @Override
                    public void onResponse(Call<ApiService.UnlockResponse> call, Response<ApiService.UnlockResponse> response) {
                        if (response.isSuccessful()) callback.onSuccess();
                        else callback.onFailure("Mở khóa thất bại (mã lỗi " + response.code() + ")");
                    }

                    @Override
                    public void onFailure(Call<ApiService.UnlockResponse> call, Throwable t) {
                        callback.onFailure("Không thể kết nối máy chủ: " + t.getMessage());
                    }
                });
    }

    /** Sum of the given (currently-locked) waypoints' real per-step prices, minus 25% - the
     *  "Unlock Now" full-trip price shown on badgeUnlockDiscount/btnUnlockFull. */
    public static int fullUnlockPriceVnd(List<Tour.Waypoint> lockedWaypoints) {
        int total = 0;
        for (Tour.Waypoint wp : lockedWaypoints) total += wp.getPrice();
        return (int) Math.round(total * (1 - UNLOCK_FULL_DISCOUNT));
    }
}
