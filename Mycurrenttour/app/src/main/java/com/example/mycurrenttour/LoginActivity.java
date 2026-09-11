package com.example.mycurrenttour;

import android.content.Intent;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;
import com.google.firebase.auth.GoogleAuthProvider;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Login screen - Firebase Authentication (Email/Password + Google), Firebase project
 * "tour-moment". Both methods end the same way: get a Firebase ID token for the signed-in
 * FirebaseUser, POST it to this app's own backend (POST /api/auth/verify, which verifies it
 * server-side via firebase-admin before upserting a Mongo User row) and store that backend
 * User._id/displayName/email/photoUrl in SessionManager exactly as before - every other screen
 * that reads SessionManager (My Travel ownership, review authorship, avatars, ...) is unaffected
 * by this migration, it just now gets a server-verified identity instead of a client-asserted one.
 * "Continue as Guest" is unchanged - still local-only, no Firebase/backend call at all.
 */
public class LoginActivity extends AppCompatActivity {

    private static final int RC_SIGN_IN = 100;
    private static final String TAG = "LoginActivity";

    private FirebaseAuth firebaseAuth;
    private GoogleSignInClient googleSignInClient;

    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText edtEmail, edtPassword;
    private TextView btnForgotPassword, btnToggleAuthMode;
    private MaterialButton btnEmailAuthSubmit;
    private ProgressBar progressLoginForm;
    private View btnGoogleSignIn, btnGuest;

    /** false = signing in to an existing account, true = creating a new one. */
    private boolean isSignUpMode = false;

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        firebaseAuth = FirebaseAuth.getInstance();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        initViews();
        updateAuthModeUI();

        btnEmailAuthSubmit.setOnClickListener(v -> onEmailAuthSubmit());
        btnToggleAuthMode.setOnClickListener(v -> {
            isSignUpMode = !isSignUpMode;
            updateAuthModeUI();
        });
        btnForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
        btnGoogleSignIn.setOnClickListener(v ->
                startActivityForResult(googleSignInClient.getSignInIntent(), RC_SIGN_IN));
        btnGuest.setOnClickListener(v -> {
            SessionManager.saveGuestSession(this);
            goToHome();
        });

        setupFooter();
    }

    private void initViews() {
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        btnForgotPassword = findViewById(R.id.btnForgotPassword);
        btnToggleAuthMode = findViewById(R.id.btnToggleAuthMode);
        btnEmailAuthSubmit = findViewById(R.id.btnEmailAuthSubmit);
        progressLoginForm = findViewById(R.id.progressLoginForm);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        btnGuest = findViewById(R.id.btnGuest);
    }

    private void updateAuthModeUI() {
        btnEmailAuthSubmit.setText(isSignUpMode ? R.string.action_sign_up_email : R.string.action_sign_in_email);
        btnToggleAuthMode.setText(isSignUpMode ? R.string.prompt_have_account : R.string.prompt_no_account);
        // Resetting a password only makes sense for an account that already exists.
        btnForgotPassword.setVisibility(isSignUpMode ? View.INVISIBLE : View.VISIBLE);
    }

    // ================= Email / Password =================

    private void onEmailAuthSubmit() {
        String email = textOf(edtEmail);
        String password = textOf(edtPassword);

        tilEmail.setError(null);
        tilPassword.setError(null);

        boolean valid = true;
        if (TextUtils.isEmpty(email)) {
            tilEmail.setError(getString(R.string.error_email_required));
            valid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError(getString(R.string.error_invalid_email));
            valid = false;
        }
        if (TextUtils.isEmpty(password)) {
            tilPassword.setError(getString(R.string.error_password_required));
            valid = false;
        } else if (password.length() < 6) {
            // Matches Firebase Auth's own minimum - failing fast here avoids a round trip just to
            // get ERROR_WEAK_PASSWORD back.
            tilPassword.setError(getString(R.string.error_password_too_short));
            valid = false;
        }
        if (!valid) return;

        setLoading(true);
        Task<AuthResult> task = isSignUpMode
                ? firebaseAuth.createUserWithEmailAndPassword(email, password)
                : firebaseAuth.signInWithEmailAndPassword(email, password);

        task.addOnSuccessListener(result -> syncFirebaseUserToBackend(result.getUser()))
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Log.e(TAG, "Email/password auth failed (signUp=" + isSignUpMode + ")", e);
                    Toast.makeText(this, mapAuthError(e), Toast.LENGTH_LONG).show();
                });
    }

    private void showForgotPasswordDialog() {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        input.setHint(R.string.hint_email);
        String prefill = textOf(edtEmail);
        if (!TextUtils.isEmpty(prefill)) input.setText(prefill);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, 0);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_reset_password_title)
                .setMessage(R.string.dialog_reset_password_message)
                .setView(input)
                .setPositiveButton(R.string.action_send, (d, w) -> {
                    String email = input.getText().toString().trim();
                    if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        Toast.makeText(this, R.string.error_invalid_email, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    firebaseAuth.sendPasswordResetEmail(email)
                            .addOnSuccessListener(v -> Toast.makeText(this, R.string.toast_reset_email_sent, Toast.LENGTH_LONG).show())
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "sendPasswordResetEmail failed", e);
                                Toast.makeText(this, mapAuthError(e), Toast.LENGTH_LONG).show();
                            });
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    /** Firebase's own exception hierarchy for auth failures - mapped to the specific Vietnamese/
     *  English string resources instead of one generic message, so e.g. "wrong password" and
     *  "no such account" read differently to the user. */
    private String mapAuthError(Exception e) {
        if (e instanceof FirebaseAuthInvalidUserException) {
            return getString(R.string.error_user_not_found);
        }
        if (e instanceof FirebaseAuthInvalidCredentialsException) {
            String code = ((FirebaseAuthInvalidCredentialsException) e).getErrorCode();
            if ("ERROR_INVALID_EMAIL".equals(code)) return getString(R.string.error_invalid_email);
            return getString(R.string.error_wrong_password);
        }
        if (e instanceof FirebaseAuthUserCollisionException) {
            return getString(R.string.error_email_in_use);
        }
        if (e instanceof FirebaseAuthWeakPasswordException) {
            return getString(R.string.error_weak_password);
        }
        if (e instanceof FirebaseNetworkException) {
            return getString(R.string.error_network);
        }
        return getString(R.string.error_auth_generic);
    }

    private void setLoading(boolean loading) {
        progressLoginForm.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnEmailAuthSubmit.setEnabled(!loading);
        btnGoogleSignIn.setEnabled(!loading);
        btnGuest.setEnabled(!loading);
    }

    private String textOf(TextInputEditText edt) {
        return edt.getText() != null ? edt.getText().toString().trim() : "";
    }

    // ================= Google =================

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != RC_SIGN_IN) return;

        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            setLoading(true);
            AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
            firebaseAuth.signInWithCredential(credential)
                    .addOnSuccessListener(result -> syncFirebaseUserToBackend(result.getUser()))
                    .addOnFailureListener(e -> {
                        setLoading(false);
                        Log.e(TAG, "signInWithCredential (Google) failed", e);
                        Toast.makeText(this, mapAuthError(e), Toast.LENGTH_LONG).show();
                    });
        } catch (ApiException e) {
            // Status code 10 = DEVELOPER_ERROR: this device/build's signing certificate SHA-1
            // isn't registered on the OAuth client for Firebase project "tour-moment" (Firebase
            // console > Project settings > this Android app > Add fingerprint). Each debug
            // keystore (one per dev machine) and the eventual release keystore each need their own
            // SHA-1 added there - this is not a one-time global setting. Get the SHA-1 via:
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

    // ================= Shared: exchange the Firebase ID token with our backend =================

    /** Common tail for both auth methods: force-refresh the ID token (getIdToken(true), not the
     *  cached one - we just signed in, but this also protects against a stale cached token from a
     *  prior session on the same device) and hand it to POST /api/auth/verify, which verifies it
     *  server-side and returns/creates the matching backend User row. */
    private void syncFirebaseUserToBackend(FirebaseUser firebaseUser) {
        if (firebaseUser == null) {
            setLoading(false);
            Toast.makeText(this, R.string.error_auth_generic, Toast.LENGTH_LONG).show();
            return;
        }

        firebaseUser.getIdToken(true)
                .addOnSuccessListener(this::onIdTokenReady)
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Log.e(TAG, "getIdToken failed", e);
                    Toast.makeText(this, mapAuthError(e), Toast.LENGTH_LONG).show();
                });
    }

    private void onIdTokenReady(GetTokenResult tokenResult) {
        String idToken = tokenResult.getToken();
        if (idToken == null) {
            setLoading(false);
            Toast.makeText(this, R.string.error_auth_generic, Toast.LENGTH_LONG).show();
            return;
        }

        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        apiService.verifyFirebaseUser(new ApiService.IdTokenRequest(idToken)).enqueue(new Callback<User>() {
            @Override
            public void onResponse(Call<User> call, Response<User> response) {
                setLoading(false);
                if (!response.isSuccessful() || response.body() == null) {
                    Log.e(TAG, "POST /api/auth/verify failed: HTTP " + response.code());
                    // Backend didn't accept the (Firebase-verified) session - don't strand the
                    // user half-signed-in, let them retry cleanly from the login screen.
                    firebaseAuth.signOut();
                    Toast.makeText(LoginActivity.this, R.string.error_sync_backend_failed, Toast.LENGTH_LONG).show();
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
                setLoading(false);
                Log.e(TAG, "POST /api/auth/verify call failed", t);
                firebaseAuth.signOut();
                Toast.makeText(LoginActivity.this, R.string.error_network, Toast.LENGTH_LONG).show();
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
