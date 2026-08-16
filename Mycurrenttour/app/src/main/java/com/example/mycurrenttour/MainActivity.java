package com.example.mycurrenttour;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

/**
 * App launcher / splash screen.
 * Shows the splash background + a green indeterminate progress bar for ~2 seconds,
 * then automatically moves on to LoginActivity and finishes itself so the back button
 * can never return here.
 */
public class MainActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 2000;

    private final Handler splashHandler = new Handler(Looper.getMainLooper());
    private final Runnable goToLogin = () -> {
        startActivity(new Intent(MainActivity.this, LoginActivity.class));
        finish();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        splashHandler.postDelayed(goToLogin, SPLASH_DELAY_MS);
    }

    @Override
    protected void onDestroy() {
        splashHandler.removeCallbacks(goToLogin);
        super.onDestroy();
    }
}
