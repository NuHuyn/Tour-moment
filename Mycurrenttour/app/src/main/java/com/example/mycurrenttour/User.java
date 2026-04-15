package com.example.mycurrenttour;

import java.io.Serializable;

public class User implements Serializable {
    private String _id;
    private String googleId;
    private String displayName;
    private String email;
    private String photoUrl;
    private String role;

    public User() {}

    public String get_id() { return _id; }
    public String getGoogleId() { return googleId; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public String getPhotoUrl() { return photoUrl; }
    public String getRole() { return role; }

    public void setGoogleId(String googleId) { this.googleId = googleId; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setEmail(String email) { this.email = email; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public void setRole(String role) { this.role = role; }
}