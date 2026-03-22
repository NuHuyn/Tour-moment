package com.example.mycurrenttour;
public class Tour {

    private String _id;
    private String tour_name;
    private String description;
    private String location;
    private String region;
    private String category;
    private int price;
    private String image_url;
    private String status;
    private String created_at;

    private Schedule schedule;

    public String getId() {
        return _id;
    }

    public String getTour_name() {
        return tour_name;
    }

    public String getDescription() {
        return description;
    }

    public String getLocation() {
        return location;
    }

    public String getRegion() {
        return region;
    }

    public String getCategory() {
        return category;
    }

    public int getPrice() {
        return price;
    }

    public String getImage_url() {
        return image_url;
    }

    public String getStatus() {
        return status;
    }

    public String getCreated_at() {
        return created_at;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public void setTour_name(String tour_name) {
        this.tour_name = tour_name;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public void setImage_url(String image_url) {
        this.image_url = image_url;
    }
}