package com.example.mycurrenttour;

import android.app.Application;

import com.squareup.picasso.OkHttp3Downloader;
import com.squareup.picasso.Picasso;

import java.io.File;

import okhttp3.Cache;
import okhttp3.OkHttpClient;

/**
 * Application entry point. Two jobs on startup: bootstrap the app's default language (Vietnamese)
 * on first-ever launch - see LanguageManager for why this has to happen here rather than in
 * MainActivity (it must run before any Activity's onCreate/attachBaseContext picks up resources)
 * - and install a Picasso singleton with a real User-Agent (see configurePicasso()).
 */
public class JourneyLogApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        LanguageManager.applyDefaultLocaleIfFirstRun(this);
        configurePicasso();
    }

    /**
     * A real, identifying User-Agent (Wikimedia's edge 403s a bare library-default one like
     * "okhttp/4.12.0" from a desktop/CLI client - see the investigation notes in the chat/PR
     * description for the full picture). NOTE: on-device this alone was NOT sufficient to make
     * Wikimedia Commons (Special:FilePath) photo loads succeed from this app - same URL, same UA
     * string, same IP still gets HTTP 403 from Android/OkHttp specifically while an identical
     * request from curl gets a normal redirect, pointing at a TLS/HTTP2-fingerprint-level block
     * rather than a UA-content one. The real fix is server-side re-hosting (done - see
     * tour-backend's rehostTourImages.js) so the app never hotlinks Wikimedia directly at all;
     * this stays in place as a harmless, still-correct improvement (and helps for any other host
     * that *is* purely UA-based).
     *
     * BUG FIXED (found while investigating "opening a trip a second time isn't instant"): the
     * OkHttpClient built here originally had no Cache attached. OkHttp3Downloader's
     * OkHttpClient-constructor overload reads its disk cache straight off that client
     * (`this.cache = client.cache()`, per Picasso's source) rather than installing a default one
     * itself the way its Context-constructor overload does - so `client.cache()` was null and
     * Picasso's on-disk image cache was silently disabled app-wide from the moment this custom
     * singleton was introduced. The in-memory LRU cache (a separate mechanism Picasso always
     * keeps) still worked, which is why it wasn't obvious right away - only cache misses across
     * process restarts (not within the same session/Activity) were actually affected.
     */
    private void configurePicasso() {
        Cache diskCache = new Cache(new File(getCacheDir(), "picasso-cache"), 100L * 1024 * 1024); // 100MB

        OkHttpClient client = new OkHttpClient.Builder()
                .cache(diskCache)
                .addInterceptor(chain -> chain.proceed(
                        chain.request().newBuilder()
                                // ASCII only - OkHttp's header validator rejects non-Latin1 bytes
                                // in a header value outright (throws inside the interceptor
                                // before the request ever goes out), which is exactly what a
                                // first version of this string with "Đắk Lắk/Tây Nguyên" did.
                                .header("User-Agent", "JourneyLogApp/1.0 (Android travel app; Dak Lak/Tay Nguyen tour demo; contact: journeylog.app@example.com)")
                                .build()))
                .build();

        Picasso picasso = new Picasso.Builder(this)
                .downloader(new OkHttp3Downloader(client))
                .build();
        Picasso.setSingletonInstance(picasso);
    }
}
