package com.example.mycurrenttour;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Tour implements Serializable {

    @SerializedName("_id")
    private String id;
    private String title;
    private String description;
    private String imageUrl;
    private String videoUrl;
    private String status;
    private String startDate;
    private String endDate;

    @SerializedName("isShared")
    private boolean isShared;

    @SerializedName("authorId")
    private String authorId;

    @SerializedName("author")
    private UserDetails author;

    // Computed live server-side from the Review collection (see tour-backend's reviewStats.js /
    // reviewController.js) - not stored fields on the Tour document itself. Default 0/0 (not
    // @SerializedName-required) so an older cached Tour without these still renders sanely (0
    // reviews -> card shows all-gray stars, no crash).
    private double avgRating;
    private int reviewCount;

    private List<Waypoint> waypoints = new ArrayList<>();

    public double getAvgRating() { return avgRating; }
    public void setAvgRating(double avgRating) { this.avgRating = avgRating; }
    public int getReviewCount() { return reviewCount; }
    public void setReviewCount(int reviewCount) { this.reviewCount = reviewCount; }

    public int getTotalPrice() {
        int total = 0;
        if (waypoints != null) {
            for (Waypoint wp : waypoints) {
                total += wp.getPrice();
            }
        }
        return total;
    }

    public static class UserDetails implements Serializable {
        private String displayName;
        private String photoUrl;

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getPhotoUrl() { return photoUrl; }
        public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    }

    public static class Waypoint implements Serializable {
        private String locationName;
        private String note;
        private int price;
        private List<String> photos = new ArrayList<>();
        private Coordinate coordinate;
        // Server-computed, per-device: true only while this waypoint is paywalled (price > 0)
        // AND this device hasn't unlocked it yet - see tour-backend's waypointVisibility.js.
        // When true, locationName/note/coordinate are already redacted/fuzzed by the server, not
        // just hidden client-side - real values were never sent. Defaults to false (not
        // @SerializedName-required) so older cached/offline Tour objects without this field don't
        // get misread as locked.
        private boolean locked = false;
        private transient boolean isExpanded = false;

        public boolean isExpanded() { return isExpanded; }
        public void setExpanded(boolean expanded) { this.isExpanded = expanded; }
        public String getLocationName() { return locationName; }
        public void setLocationName(String locationName) { this.locationName = locationName; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
        public int getPrice() { return price; }
        public void setPrice(int price) { this.price = price; }
        public List<String> getPhotos() { return photos; }
        public void setPhotos(List<String> photos) { this.photos = photos; }
        public Coordinate getCoordinate() { return coordinate; }
        public void setCoordinate(Coordinate coordinate) { this.coordinate = coordinate; }
        public boolean isLocked() { return locked; }
        public void setLocked(boolean locked) { this.locked = locked; }
    }

    public static class Coordinate implements Serializable {
        private String type = "Point";
        private List<Double> coordinates;
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public List<Double> getCoordinates() { return coordinates; }
        public void setCoordinates(List<Double> coordinates) { this.coordinates = coordinates; }
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public boolean isShared() { return isShared; }
    public void setShared(boolean shared) { isShared = shared; }
    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
    public UserDetails getAuthor() { return author; }
    public void setAuthor(UserDetails author) { this.author = author; }
    public List<Waypoint> getWaypoints() { return waypoints; }
    public void setWaypoints(List<Waypoint> waypoints) { this.waypoints = waypoints; }
}
