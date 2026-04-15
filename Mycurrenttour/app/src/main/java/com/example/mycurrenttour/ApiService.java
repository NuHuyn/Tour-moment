package com.example.mycurrenttour;

import java.util.List;
import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import com.google.gson.annotations.SerializedName;

public interface ApiService {

    @GET("api/tours/my-tours")
    Call<List<Tour>> getMyTours();

    // Fetch public tour list for the Home screen
    @GET("api/tours")
    Call<List<Tour>> getSharedTours();

    // Log in
    @POST("api/auth/google-login")
    Call<User> googleLogin(@Body User user);

    // Update profile
    @PUT("api/auth/update-profile")
    Call<User> updateProfile(@Body User user);

    // Get tour details
    @GET("api/tours/{id}")
    Call<Tour> getTourById(@Path("id") String tourId);

    // Create a new tour (matches router.post("/") in the backend)
    @POST("api/tours")
    Call<Tour> createTour(@Body Tour tour);

    // Add a waypoint (matches router.patch("/:tourId/waypoint") in the backend)
    @PATCH("api/tours/{id}/waypoint")
    Call<Tour> addWaypoint(
            @Path("id") String tourId,
            @Body Tour.Waypoint waypoint
    );

    // Upload an image (matches router.post("/upload") in the backend)
    @Multipart
    @POST("api/tours/upload")
    Call<UploadResponse> uploadImage(@Part MultipartBody.Part image);


    @PATCH("api/tours/{id}/share") // Exactly matches the PATCH route in your backend
    Call<Tour> shareTour(@Path("id") String tourId);

    @PUT("api/tours/{id}") // Or your API update endpoint
    Call<Tour> updateTour(@Path("id") String tourId, @Body Tour tour);



    // Class to handle data returned from the Upload API
    class UploadResponse {
        @SerializedName("imageUrl")
        private String imageUrl;

        public String getImageUrl() {
            return imageUrl;
        }

        public void setImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
        }
    }
}