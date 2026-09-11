package com.example.mycurrenttour;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import com.squareup.picasso.Transformation;

/**
 * Cheap "frosted glass" blur for locked-waypoint stop cards (Feature 2's tasteful obfuscation -
 * see RouteStopAdapter). Scale-down-then-scale-up rather than a real Gaussian/stack blur: no extra
 * dependency, fast enough to run per RecyclerView bind, and the visual result is exactly what's
 * wanted here - a soft, indistinct thumbnail, not a precise blur radius.
 *
 * Only ever applied to a fixed local placeholder drawable (R.drawable.centralvietnam), never to a
 * real waypoint photo - the server never sends a locked waypoint's real photo in the first place
 * (see tour-backend's waypointVisibility.js), so there is nothing sensitive to blur here; this is
 * purely a stylistic "mystery location" treatment.
 */
public class BlurTransformation implements Transformation {

    private final float scale;

    public BlurTransformation() {
        this(0.07f);
    }

    public BlurTransformation(float scale) {
        this.scale = scale;
    }

    @Override
    public Bitmap transform(@NonNull Bitmap source) {
        int downWidth = Math.max(1, Math.round(source.getWidth() * scale));
        int downHeight = Math.max(1, Math.round(source.getHeight() * scale));

        Bitmap scaledDown = Bitmap.createScaledBitmap(source, downWidth, downHeight, true);
        Bitmap blurred = Bitmap.createScaledBitmap(scaledDown, source.getWidth(), source.getHeight(), true);

        if (scaledDown != blurred) scaledDown.recycle();
        if (blurred != source) source.recycle();
        return blurred;
    }

    @NonNull
    @Override
    public String key() {
        return "blur(" + scale + ")";
    }
}
