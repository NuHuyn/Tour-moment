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
import retrofit2.http.Query;
import com.google.gson.annotations.SerializedName;

public interface ApiService {

    @GET("api/tours/my-tours/{userId}")
    Call<List<Tour>> getMyTours(
            @Path("userId") String userId,
            @Query("status") String status
    );

    @GET("api/tours")
    Call<List<Tour>> getSharedTours();

    @POST("api/tours")
    Call<Tour> createTour(@Body Tour tour);

    @PUT("api/tours/{id}")
    Call<Tour> updateTour(@Path("id") String tourId, @Body Tour tour);

    @POST("api/tours/copy/{tourId}")
    Call<Tour> copyTour(
            @Path("tourId") String tourId,
            @Body UserCopyRequest body
    );

    @PATCH("api/tours/{id}/share")
    Call<Tour> shareTour(@Path("id") String tourId);

    @PATCH("api/tours/{id}/waypoint")
    Call<Tour> addWaypoint(
            @Path("id") String tourId,
            @Body Tour.Waypoint waypoint
    );

    @Multipart
    @POST("api/tours/upload")
    Call<UploadResponse> uploadImage(@Part MultipartBody.Part image);

    @POST("api/auth/google-login")
    Call<User> googleLogin(@Body User user);

    class UploadResponse {
        @SerializedName("imageUrl")
        private String imageUrl;
        public String getImageUrl() { return imageUrl; }
    }

    class UserCopyRequest {
        @SerializedName("userId")
        private String userId;
        public UserCopyRequest(String userId) { this.userId = userId; }
    }
}
