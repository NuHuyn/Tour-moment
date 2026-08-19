package com.example.mycurrenttour;

import android.app.Application;

/**
 * Application entry point. Only job right now: bootstrap the app's default language (Vietnamese)
 * on first-ever launch - see LanguageManager for why this has to happen here rather than in
 * MainActivity (it must run before any Activity's onCreate/attachBaseContext picks up resources).
 */
public class TourMomentApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        LanguageManager.applyDefaultLocaleIfFirstRun(this);
    }
}
