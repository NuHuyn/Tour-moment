package com.example.mycurrenttour;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
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

/**
 * Login screen (demo build - no backend).
 * "Continue with Google" triggers a real on-device Google account picker purely to read the
 * chosen account's name/email/photo (no Firebase Auth, no server call) so ProfileFragment has a
 * real avatar to show. "Continue as Guest" just records that choice. Both then land on Home.
 */
public class LoginActivity extends AppCompatActivity {

    private static final int RC_SIGN_IN = 100;

    private GoogleSignInClient googleSignInClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        findViewById(R.id.btnGoogleSignIn).setOnClickListener(v ->
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
            SessionManager.saveGoogleSession(
                    this,
                    account.getDisplayName(),
                    account.getEmail(),
                    account.getPhotoUrl() != null ? account.getPhotoUrl().toString() : null);
            goToHome();
        } catch (ApiException e) {
            Toast.makeText(this, "Google sign-in cancelled", Toast.LENGTH_SHORT).show();
        }
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
