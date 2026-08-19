package com.example.mycurrenttour;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

/**
 * In-app language switcher (English / Vietnamese) built on AppCompat's per-app language API.
 * AppCompatDelegate.setApplicationLocales() persists the chosen locale itself (AppCompat writes
 * it to a system-backed store on API 33+, or its own SharedPreferences below that) and triggers
 * an automatic Activity recreation everywhere the new locale needs to apply - callers here don't
 * need to save anything themselves or manually recreate() any Activity.
 *
 * The only thing this class owns is the "first run" bootstrap: a fresh install has no locale
 * override yet, so AppCompat would otherwise fall back to the device's language. Since this app
 * defaults to Vietnamese regardless of device language, applyDefaultLocaleIfFirstRun() forces
 * "vi" once, the very first time the process ever starts - after that, whatever the user picks
 * (or leaves as the vi default) is respected on every later launch via AppCompat's own
 * persistence, and this bootstrap never runs again.
 */
public final class LanguageManager {

    public static final String LANGUAGE_VI = "vi";
    public static final String LANGUAGE_EN = "en";

    private static final String PREFS_NAME = "language_prefs";
    private static final String KEY_BOOTSTRAPPED = "default_locale_applied";

    private LanguageManager() {}

    /** Call once from Application#onCreate(). Forces the vi default on the very first launch only. */
    public static void applyDefaultLocaleIfFirstRun(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_BOOTSTRAPPED, false)) {
            setLanguage(LANGUAGE_VI);
            prefs.edit().putBoolean(KEY_BOOTSTRAPPED, true).apply();
        }
    }

    /** Switches the app's language. AppCompat persists the choice and recreates open Activities. */
    public static void setLanguage(String languageTag) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag));
    }

    /** Current applied app language tag ("vi"/"en"), falling back to the device locale if the
     *  app hasn't set an override yet (shouldn't happen post-bootstrap, but kept defensive). */
    public static String getCurrentLanguageTag() {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        if (!locales.isEmpty()) {
            return locales.get(0).getLanguage();
        }
        return Locale.getDefault().getLanguage();
    }
}
