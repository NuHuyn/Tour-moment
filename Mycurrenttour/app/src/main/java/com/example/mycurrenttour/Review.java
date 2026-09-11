package com.example.mycurrenttour;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * One review of a trip - mirrors tour-backend's Review model. `user` (displayName/photoUrl) is
 * looked up server-side from the reviewer's User row at read time (see reviewController's
 * getTourReviews), not duplicated into the Review document itself.
 */
public class Review implements Serializable {

    @SerializedName("_id")
    private String id;
    private String userId;
    private int rating;
    private String comment;
    private String createdAt;
    private ReviewerInfo user;

    public static class ReviewerInfo implements Serializable {
        private String displayName;
        private String photoUrl;
        public String getDisplayName() { return displayName; }
        public String getPhotoUrl() { return photoUrl; }
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public int getRating() { return rating; }
    public String getComment() { return comment; }
    public String getCreatedAt() { return createdAt; }
    public ReviewerInfo getUser() { return user; }
}
