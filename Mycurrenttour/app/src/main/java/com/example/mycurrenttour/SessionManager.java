package com.example.mycurrenttour;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

/**
 * Local session store (SharedPreferences).
 * Records which door the user came in through on LoginActivity - Guest or Google. For Google,
 * once LoginActivity's POST /api/auth/google-login call succeeds, this holds the *backend's*
 * user id (User._id, which the backend sets equal to googleId - see authController.js) alongside
 * the display name/email/photo URL from the response body. That backend user id is what
 * MyTourFragment, CreateTourActivity and TourAdapter's copyTour() use as authorId/userId when
 * calling the API - it is NOT a Firebase UID (Firebase Auth is never engaged in this app; see
 * LoginActivity's TODO).
 *
 * Guest accounts get a locally-generated persistent id (KEY_GUEST_ID, format "guest_<uuid>")
 * instead - the backend doesn't require authorId to reference a real User row (see
 * getMyTours/copyTour in tourController.js, both just filter/set by the raw string), so a local
 * id works identically to a Google id for "my tours" ownership. This id is generated once and
 * reused across app restarts (MainActivity always routes through LoginActivity on cold start,
 * with no "already signed in" skip, so it must survive repeated "Continue as Guest" taps or a
 * guest's My Travel would appear empty every time the app is reopened) - it only resets on an
 * explicit sign-out (clear()).
 */
public class SessionManager {

    private static final String PREFS_NAME = "session_prefs";
    private static final String KEY_SIGN_IN_TYPE = "sign_in_type";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_GUEST_ID = "guest_id";
    private static final String KEY_NAME = "display_name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PHOTO_URL = "photo_url";

    // GOOGLE covers any real (non-Guest) Firebase-authenticated session as of the Firebase Auth
    // migration - Email/Password sign-in also lands here via saveGoogleSession, not just Google
    // Sign-In. Kept as one name/value (rather than renamed to e.g. AUTHENTICATED) to avoid
    // rippling the rename into every place that already reads it (DiscoveryFragment,
    // TourDetailActivity's review-gating, ProfileFragment, ...) - what every caller actually
    // means by it is "identified account with a backend User row", which is still accurate.
    public enum SignInType { GUEST, GOOGLE }

    private SessionManager() {}

    public static void saveGuestSession(Context context) {
        SharedPreferences p = prefs(context);
        String guestId = p.getString(KEY_GUEST_ID, null);
        if (guestId == null) {
            guestId = "guest_" + UUID.randomUUID();
        }
        p.edit()
                .putString(KEY_SIGN_IN_TYPE, SignInType.GUEST.name())
                .putString(KEY_GUEST_ID, guestId)
                .putString(KEY_USER_ID, guestId)
                .remove(KEY_NAME)
                .remove(KEY_EMAIL)
                .remove(KEY_PHOTO_URL)
                .apply();
    }

    /** userId is the backend's User._id from the google-login response, not a Firebase UID. */
    public static void saveGoogleSession(Context context, String userId, String displayName, String email, String photoUrl) {
        prefs(context).edit()
                .putString(KEY_SIGN_IN_TYPE, SignInType.GOOGLE.name())
                .putString(KEY_USER_ID, userId)
                .putString(KEY_NAME, displayName)
                .putString(KEY_EMAIL, email)
                .putString(KEY_PHOTO_URL, photoUrl)
                .apply();
    }

    public static void clear(Context context) {
        prefs(context).edit().clear().apply();
    }

    public static SignInType getSignInType(Context context) {
        String raw = prefs(context).getString(KEY_SIGN_IN_TYPE, SignInType.GUEST.name());
        try {
            return SignInType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return SignInType.GUEST;
        }
    }

    /** Backend User._id for a signed-in Google account, or the local persistent guest_<uuid>
     *  for a Guest session (see saveGuestSession) - null only if no session has been started
     *  at all yet (shouldn't happen once LoginActivity has been through once). */
    public static String getUserId(Context context) {
        return prefs(context).getString(KEY_USER_ID, null);
    }

    public static String getDisplayName(Context context) {
        return prefs(context).getString(KEY_NAME, null);
    }

    public static String getEmail(Context context) {
        return prefs(context).getString(KEY_EMAIL, null);
    }

    public static String getPhotoUrl(Context context) {
        return prefs(context).getString(KEY_PHOTO_URL, null);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
