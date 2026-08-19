package com.example.mycurrenttour;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;

/**
 * "My Profile" tab (nav_profile). Green curved-hill header, circular avatar, account-info card
 * and a "More" menu list. There is no backend account behind this (see SessionManager) - the
 * avatar/name/email shown here come from whichever door the user came in through on
 * LoginActivity: Guest (avt_guest placeholder) or Google (real photo/name/email read off the
 * on-device GoogleSignInAccount at login time, loaded here with Glide).
 * The "More" rows (Saved Places, Payment Methods, Notifications, Help & Support, Settings) have
 * no matching screens yet, so they're non-functional placeholders for now.
 */
public class ProfileFragment extends Fragment {

    private ImageView imgAvatar;
    private TextView txtFullName, txtTagline, txtEmail, txtPhone, txtRole;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        imgAvatar = view.findViewById(R.id.imgAvatarProfile);
        txtFullName = view.findViewById(R.id.txtFullNameProfile);
        txtTagline = view.findViewById(R.id.txtTaglineProfile);
        txtEmail = view.findViewById(R.id.txtEmailProfile);
        txtPhone = view.findViewById(R.id.txtPhoneProfile);
        txtRole = view.findViewById(R.id.txtRoleProfile);

        displayAccountInfo();
        setupMenuRows(view);

        BottomNavScrollHelper.attach((ScrollView) view.findViewById(R.id.scrollProfile), (HomeActivity) requireActivity());
    }

    private void displayAccountInfo() {
        if (getContext() == null) return;

        SessionManager.SignInType signInType = SessionManager.getSignInType(getContext());

        if (signInType == SessionManager.SignInType.GOOGLE) {
            String name = SessionManager.getDisplayName(getContext());
            String email = SessionManager.getEmail(getContext());
            String photoUrl = SessionManager.getPhotoUrl(getContext());

            txtFullName.setText(name != null ? name : getString(R.string.default_name_traveler));
            txtTagline.setText(R.string.tagline_travel_lover);
            txtEmail.setText(email != null ? email : getString(R.string.not_provided));
            txtPhone.setText(R.string.not_provided);
            txtRole.setText(R.string.role_customer);

            Glide.with(this)
                    .load(photoUrl)
                    .placeholder(R.drawable.ic_user_placeholder)
                    .error(R.drawable.ic_user_placeholder)
                    .circleCrop()
                    .into(imgAvatar);
        } else {
            txtFullName.setText(R.string.default_name_guest);
            txtTagline.setText(R.string.tagline_travel_lover);
            txtEmail.setText(R.string.not_signed_in);
            txtPhone.setText(R.string.not_provided);
            txtRole.setText(R.string.default_name_guest);

            Glide.with(this)
                    .load(R.drawable.avt_guest)
                    .circleCrop()
                    .into(imgAvatar);
        }
    }

    /** rowSettingsMenu used to be a non-functional "coming soon" placeholder
     *  (there's no real Settings screen). It's repurposed here as the app's Language row/entry
     *  point - the only setting that actually exists right now - instead of adding a new row,
     *  which would mean changing fragment_profile.xml's structure. */
    private void setupMenuRows(View root) {
        root.findViewById(R.id.rowSavedPlaces).setOnClickListener(v -> comingSoon(getString(R.string.menu_saved_places)));
        root.findViewById(R.id.rowPaymentMethods).setOnClickListener(v -> comingSoon(getString(R.string.menu_payment_methods)));
        root.findViewById(R.id.rowNotifications).setOnClickListener(v -> comingSoon(getString(R.string.menu_notifications)));
        root.findViewById(R.id.rowHelpSupport).setOnClickListener(v -> comingSoon(getString(R.string.menu_help_support)));
        root.findViewById(R.id.rowSettingsMenu).setOnClickListener(v -> showLanguageDialog());
        root.findViewById(R.id.rowLogout).setOnClickListener(v -> showLogoutDialog());
    }

    private void comingSoon(String feature) {
        if (getContext() == null) return;
        Toast.makeText(getContext(), getString(R.string.coming_soon_format, feature), Toast.LENGTH_SHORT).show();
    }

    /** Language picker - single-choice dialog between English and Vietnamese (see
     *  LanguageManager). AppCompat persists the choice and recreates every open Activity on its
     *  own once setApplicationLocales() is called, so there's nothing else to wire up here. */
    private void showLanguageDialog() {
        if (getContext() == null) return;

        CharSequence[] options = {
                getString(R.string.language_vietnamese),
                getString(R.string.language_english)
        };
        int checkedIndex = LanguageManager.LANGUAGE_EN.equals(LanguageManager.getCurrentLanguageTag()) ? 1 : 0;

        new AlertDialog.Builder(getContext())
                .setTitle(R.string.menu_language_settings)
                .setSingleChoiceItems(options, checkedIndex, (dialog, which) -> {
                    LanguageManager.setLanguage(which == 1 ? LanguageManager.LANGUAGE_EN : LanguageManager.LANGUAGE_VI);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void showLogoutDialog() {
        if (getContext() == null) return;
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.logout_title)
                .setMessage(R.string.logout_message)
                .setPositiveButton(R.string.dialog_yes, (d, w) -> {
                    SessionManager.clear(getContext());
                    startActivity(new Intent(getContext(), LoginActivity.class));
                    if (getActivity() != null) getActivity().finish();
                })
                .setNegativeButton(R.string.dialog_no, null).show();
    }
}
