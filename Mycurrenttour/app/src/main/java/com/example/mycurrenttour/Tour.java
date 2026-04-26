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

    /**
     * SỬA LỖI TẠI ĐÂY:
     * Chỉ dùng 1 biến duy nhất mapping với "authorId".
     * Kiểu Object giúp nhận được cả chuỗi ID hoặc Object chi tiết từ Server.
     */
    @SerializedName("authorId")
    private Object authorData;

    private List<Waypoint> waypoints;
    private List<String> photos;

    // --- XỬ LÝ AUTHOR ---
    public String getAuthorIdString() {    if (authorData == null) return "";
        if (authorData instanceof String) return (String) authorData;

        // Trường hợp là Map (GSON mặc định)
        if (authorData instanceof java.util.Map) {
            Object id = ((java.util.Map<?, ?>) authorData).get("_id");
            return id != null ? id.toString() : "";
        }

        // Trường hợp là Object UserDetails
        if (authorData instanceof UserDetails) {
            return ((UserDetails) authorData).getId();
        }
        return "";
    }

    public void setAuthorIdString(String userId) {
        this.authorData = userId;
    }

    public UserDetails getAuthorDetails() {
        if (authorData instanceof UserDetails) {
            return (UserDetails) authorData;
        }
        return null;
    }

    // --- HÀM TIỆN ÍCH ---
    public int getTotalPrice() {
        int total = 0;
        if (waypoints != null) {
            for (Waypoint wp : waypoints) {
                total += wp.getPrice();
            }
        }
        return total;
    }

    // --- INNER CLASSES ---
    public static class UserDetails implements Serializable {
        @SerializedName("_id")
        private String id;
        private String displayName;
        private String photoUrl;

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public String getPhotoUrl() { return photoUrl; }
    }

    public static class Waypoint implements Serializable {
        private String note;
        private String locationName;
        private int price;
        private Coordinate coordinate;
        private List<String> photos;
        private transient boolean isExpanded = false;

        public boolean isExpanded() { return isExpanded; }
        public void setExpanded(boolean expanded) { this.isExpanded = expanded; }
        public List<String> getPhotos() {
            return (photos == null) ? new ArrayList<>() : photos;
        }
        public void setPhotos(List<String> photos) { this.photos = photos; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
        public String getLocationName() { return locationName; }
        public void setLocationName(String locationName) { this.locationName = locationName; }
        public int getPrice() { return price; }
        public void setPrice(int price) { this.price = price; }
        public Coordinate getCoordinate() { return coordinate; }
        public void setCoordinate(Coordinate coordinate) { this.coordinate = coordinate; }
    }

    public static class Coordinate implements Serializable {
        private String type = "Point";
        private List<Double> coordinates;
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public List<Double> getCoordinates() { return coordinates; }
        public void setCoordinates(List<Double> coordinates) { this.coordinates = coordinates; }
    }

    // --- GETTERS & SETTERS ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public boolean isShared() { return isShared; }
    public void setShared(boolean shared) { isShared = shared; }
    public List<Waypoint> getWaypoints() { return waypoints; }
    public void setWaypoints(List<Waypoint> waypoints) { this.waypoints = waypoints; }
    public List<String> getPhotos() { return photos; }
    public void setPhotos(List<String> photos) { this.photos = photos; }
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
}