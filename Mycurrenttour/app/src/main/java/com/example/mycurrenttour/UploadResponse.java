package com.example.mycurrenttour;

import com.google.gson.annotations.SerializedName;

public class UploadResponse {
    @SerializedName("imageUrl")
    private String imageUrl;

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}