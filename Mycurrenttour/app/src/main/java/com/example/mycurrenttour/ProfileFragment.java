package com.example.mycurrenttour;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.squareup.picasso.Picasso;

/**
 * "My Profile" tab (nav_profile) - previously unhandled: tapping this tab in the bottom nav did
 * nothing (dead ProfileActivity.java referenced in the manifest was never implemented, so this
 * layout sat unused). Now a real fragment: shows the signed-in account and lets the user log out.
 * TODO: "Chỉnh sửa hồ sơ" is a stub (no EditProfile screen exists yet) - wire it up when built.
 */
public class ProfileFragment extends Fragment {

    private ImageView imgAvatar;
    private TextView txtFullName, txtEmail;

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
        txtEmail = view.findViewById(R.id.txtEmailProfile);
        Button btnEditProfile = view.findViewById(R.id.btnEditProfile);
        Button btnLogout = view.findViewById(R.id.btnLogout);

        displayAccountInfo();

        btnEditProfile.setOnClickListener(v ->
                Toast.makeText(getContext(), "Edit profile: coming soon", Toast.LENGTH_SHORT).show());
        btnLogout.setOnClickListener(v -> showLogoutDialog());
    }

    private void displayAccountInfo() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        txtFullName.setText(user.getDisplayName() != null ? user.getDisplayName() : "Traveler");
        txtEmail.setText("Email: " + (user.getEmail() != null ? user.getEmail() : "N/A"));
        if (user.getPhotoUrl() != null) {
            Picasso.get().load(user.getPhotoUrl()).placeholder(R.drawable.ic_user_placeholder).into(imgAvatar);
        }
    }

    private void showLogoutDialog() {
        if (getContext() == null) return;
        new AlertDialog.Builder(getContext())
                .setTitle("Log out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Yes", (d, w) -> {
                    FirebaseAuth.getInstance().signOut();
                    startActivity(new Intent(getContext(), LoginActivity.class));
                    requireActivity().finish();
                })
                .setNegativeButton("No", null).show();
    }
}
