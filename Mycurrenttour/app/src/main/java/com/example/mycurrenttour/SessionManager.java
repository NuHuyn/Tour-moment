package com.example.mycurrenttour;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Tiny local-only session store (SharedPreferences).
 * Records which door the user came in through on LoginActivity - Guest or Google - and, for
 * Google, the account's display name/email/photo URL read straight off the on-device
 * GoogleSignInAccount result. There is no backend/Firebase account behind this; it only exists
 * so ProfileFragment knows what to show.
 */
public class SessionManager {

    private static final String PREFS_NAME = "session_prefs";
    private static final String KEY_SIGN_IN_TYPE = "sign_in_type";
    private static final String KEY_NAME = "display_name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PHOTO_URL = "photo_url";

    public enum SignInType { GUEST, GOOGLE }

    private SessionManager() {}

    public static void saveGuestSession(Context context) {
        prefs(context).edit()
                .putString(KEY_SIGN_IN_TYPE, SignInType.GUEST.name())
                .remove(KEY_NAME)
                .remove(KEY_EMAIL)
                .remove(KEY_PHOTO_URL)
                .apply();
    }

    public static void saveGoogleSession(Context context, String displayName, String email, String photoUrl) {
        prefs(context).edit()
                .putString(KEY_SIGN_IN_TYPE, SignInType.GOOGLE.name())
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
