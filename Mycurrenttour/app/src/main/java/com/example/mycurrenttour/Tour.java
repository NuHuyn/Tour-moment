package com.example.mycurrenttour;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;

public class Tour implements Serializable {
    @SerializedName("_id")
    private String id;

    private String title;
    private String description;
    private String imageUrl;

    // --- TRƯỜNG DỮ LIỆU MỚI ĐỂ LƯU VIDEO ---
    private String videoUrl;

    @SerializedName("isShared")
    private boolean isShared;

    private String startDate;
    private String endDate;
    private List<Waypoint> waypoints;
    private String status;

    // --- GETTER & SETTER CHO VIDEO ---
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

    public boolean isShared() { return isShared; }
    public void setShared(boolean shared) { this.isShared = shared; }

    public void setStatus(String status) { this.status = status; }
    public String getStatus() { return status; }

    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getImageUrl() { return imageUrl; }

    public void setCoverPhoto(String coverPhoto) { this.imageUrl = coverPhoto; }

    public int getTotalPrice() {
        int total = 0;
        if (waypoints != null) {
            for (Waypoint wp : waypoints) {
                total += wp.getPrice();
            }
        }
        return total;
    }

    public void setWaypoints(List<Waypoint> waypoints) { this.waypoints = waypoints; }

    public void setId(String id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public List<Waypoint> getWaypoints() { return waypoints; }
    public String getStartDateString() { return startDate; }
    public String getEndDateString() { return endDate; }

    // --- LỚP WAYPOINT ---
    public static class Waypoint implements Serializable {
        private String locationName;
        private String note;
        private int price;
        private List<String> photos = new ArrayList<>();
        private Coordinate coordinate;

        // Dùng transient để GSON không cố gắng serialize biến trạng thái UI này
        private transient boolean isExpanded = false;

        public boolean isExpanded() { return isExpanded; }
        public void setExpanded(boolean expanded) { isExpanded = expanded; }

        public String getLocationName() { return locationName; }
        public String getNote() { return note; }
        public int getPrice() { return price; }
        public List<String> getPhotos() { return photos; }
        public Coordinate getCoordinate() { return coordinate; }

        public void setLocationName(String locationName) { this.locationName = locationName; }
        public void setNote(String note) { this.note = note; }
        public void setPrice(int price) { this.price = price; }
        public void setPhotos(List<String> photos) { this.photos = photos; }
        public void setCoordinate(Coordinate coordinate) { this.coordinate = coordinate; }

        public void addPhoto(String url) {
            if (this.photos == null) this.photos = new ArrayList<>();
            this.photos.add(url);
        }
    }

    // --- LỚP TỌA ĐỘ ---
    public static class Coordinate implements Serializable {
        private String type;
        private List<Double> coordinates;

        public String getType() { return type; }
        public List<Double> getCoordinates() { return coordinates; }

        public void setType(String type) { this.type = type; }
        public void setCoordinates(List<Double> coordinates) { this.coordinates = coordinates; }
    }
}