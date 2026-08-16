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

            txtFullName.setText(name != null ? name : "Traveler");
            txtTagline.setText("Travel Lover");
            txtEmail.setText(email != null ? email : "Not provided");
            txtPhone.setText("Not provided");
            txtRole.setText("Customer");

            Glide.with(this)
                    .load(photoUrl)
                    .placeholder(R.drawable.ic_user_placeholder)
                    .error(R.drawable.ic_user_placeholder)
                    .circleCrop()
                    .into(imgAvatar);
        } else {
            txtFullName.setText("Guest");
            txtTagline.setText("Travel Lover");
            txtEmail.setText("Not signed in");
            txtPhone.setText("Not provided");
            txtRole.setText("Guest");

            Glide.with(this)
                    .load(R.drawable.avt_guest)
                    .circleCrop()
                    .into(imgAvatar);
        }
    }

    private void setupMenuRows(View root) {
        root.findViewById(R.id.icSettingsHeader).setOnClickListener(v -> comingSoon("Settings"));
        root.findViewById(R.id.rowSavedPlaces).setOnClickListener(v -> comingSoon("Saved Places"));
        root.findViewById(R.id.rowPaymentMethods).setOnClickListener(v -> comingSoon("Payment Methods"));
        root.findViewById(R.id.rowNotifications).setOnClickListener(v -> comingSoon("Notifications"));
        root.findViewById(R.id.rowHelpSupport).setOnClickListener(v -> comingSoon("Help & Support"));
        root.findViewById(R.id.rowSettingsMenu).setOnClickListener(v -> comingSoon("Settings"));
        root.findViewById(R.id.rowLogout).setOnClickListener(v -> showLogoutDialog());
    }

    private void comingSoon(String feature) {
        if (getContext() == null) return;
        Toast.makeText(getContext(), feature + ": coming soon", Toast.LENGTH_SHORT).show();
    }

    private void showLogoutDialog() {
        if (getContext() == null) return;
        new AlertDialog.Builder(getContext())
                .setTitle("Log out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Yes", (d, w) -> {
                    SessionManager.clear(getContext());
                    startActivity(new Intent(getContext(), LoginActivity.class));
                    if (getActivity() != null) getActivity().finish();
                })
                .setNegativeButton("No", null).show();
    }
}
