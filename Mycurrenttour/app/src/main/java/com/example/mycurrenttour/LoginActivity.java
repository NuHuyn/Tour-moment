package com.example.mycurrenttour;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Login screen.
 * "Continue with Google" triggers a real on-device Google account picker, then POSTs the
 * picked account's stable id/email/name/photo to the backend (POST /api/auth/google-login),
 * which upserts a User row and hands back its record - SessionManager then stores that
 * backend-issued user id (not a Firebase uid; Firebase Auth is not engaged here) so
 * MyTourFragment/CreateTourActivity/TourAdapter can use it as authorId/userId in later API calls.
 * "Continue as Guest" just records that choice locally, no network call. Both land on Home.
 *
 * TODO: this trusts client-supplied Google account data without server-side ID token
 * verification (no requestIdToken() here, backend does not check anything against Google).
 * Known simplification - upgrade to requestIdToken(...) + Firebase/google-auth-library
 * verification on the backend before any production/public release.
 */
public class LoginActivity extends AppCompatActivity {

    private static final int RC_SIGN_IN = 100;
    private static final String TAG = "LoginActivity";

    private GoogleSignInClient googleSignInClient;
    private View btnGoogleSignIn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        btnGoogleSignIn.setOnClickListener(v ->
                startActivityForResult(googleSignInClient.getSignInIntent(), RC_SIGN_IN));
        findViewById(R.id.btnGuest).setOnClickListener(v -> {
            SessionManager.saveGuestSession(this);
            goToHome();
        });

        setupFooter();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != RC_SIGN_IN) return;

        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            syncAccountToBackend(account);
        } catch (ApiException e) {
            // Status code 10 = DEVELOPER_ERROR: this device/build's signing certificate SHA-1
            // isn't registered on the OAuth client in Firebase console (Project Settings > your
            // Android app > Add fingerprint). Each debug keystore (one per dev machine) and the
            // eventual release keystore each need their own SHA-1 added there - this is not a
            // one-time global setting. Get the SHA-1 via:
            //   keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -storepass android -alias androiddebugkey
            if (e.getStatusCode() == com.google.android.gms.common.api.CommonStatusCodes.DEVELOPER_ERROR) {
                Log.e(TAG, "Google sign-in DEVELOPER_ERROR (10) - this build's SHA-1 fingerprint " +
                        "is not registered on the OAuth client in Firebase console.", e);
                Toast.makeText(this,
                        "Sign-in setup error: this device's SHA-1 isn't registered in Firebase console yet.",
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Google sign-in cancelled", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /** Posts the picked account to POST /api/auth/google-login and stores the backend's
     *  response (its User._id, not a Firebase uid) into SessionManager before continuing to
     *  Home. See the class-level TODO re: no ID token verification yet. */
    private void syncAccountToBackend(GoogleSignInAccount account) {
        User request = new User();
        // account.getId() is the Google account's stable unique id - unlike email, it cannot
        // change if the user later changes their email address, so it's the right value to key
        // the backend User row on (matches authController.js's googleId field/upsert key).
        request.setGoogleId(account.getId());
        request.setEmail(account.getEmail());
        request.setDisplayName(account.getDisplayName());
        request.setPhotoUrl(account.getPhotoUrl() != null ? account.getPhotoUrl().toString() : null);

        btnGoogleSignIn.setEnabled(false);
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        apiService.googleLogin(request).enqueue(new Callback<User>() {
            @Override
            public void onResponse(Call<User> call, Response<User> response) {
                btnGoogleSignIn.setEnabled(true);
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(LoginActivity.this, "Sign-in failed, please try again", Toast.LENGTH_SHORT).show();
                    return;
                }
                User user = response.body();
                SessionManager.saveGoogleSession(
                        LoginActivity.this,
                        user.get_id(),
                        user.getDisplayName(),
                        user.getEmail(),
                        user.getPhotoUrl());
                goToHome();
            }

            @Override
            public void onFailure(Call<User> call, Throwable t) {
                btnGoogleSignIn.setEnabled(true);
                Log.e(TAG, "google-login call failed", t);
                Toast.makeText(LoginActivity.this, "Could not reach server, please try again", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void goToHome() {
        startActivity(new Intent(LoginActivity.this, HomeActivity.class));
        finish();
    }

    private void setupFooter() {
        TextView tvFooter = findViewById(R.id.tvFooter);

        String prefix = "By continuing, you agree to our ";
        String terms = "Terms of Service";
        String and = " and ";
        String privacy = "Privacy Policy";
        String full = prefix + terms + and + privacy;

        SpannableString spannable = new SpannableString(full);
        int linkColor = ContextCompat.getColor(this, R.color.link_light_green);

        int termsStart = prefix.length();
        int termsEnd = termsStart + terms.length();
        int privacyStart = termsEnd + and.length();
        int privacyEnd = privacyStart + privacy.length();

        spannable.setSpan(new ForegroundColorSpan(linkColor), termsStart, termsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                Toast.makeText(LoginActivity.this, "Terms of Service", Toast.LENGTH_SHORT).show();
            }
        }, termsStart, termsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        spannable.setSpan(new ForegroundColorSpan(linkColor), privacyStart, privacyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                Toast.makeText(LoginActivity.this, "Privacy Policy", Toast.LENGTH_SHORT).show();
            }
        }, privacyStart, privacyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        tvFooter.setText(spannable);
        tvFooter.setMovementMethod(LinkMovementMethod.getInstance());
        tvFooter.setHighlightColor(android.graphics.Color.TRANSPARENT);
        tvFooter.setTextColor(ContextCompat.getColor(this, R.color.subtext_light_gray));
    }
}
