package com.example.mycurrenttour;

import java.util.List;
import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.HTTP;
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
    Call<List<Tour>> getSharedTours(@Query("deviceId") String deviceId);

    // Single-tour refetch, redacted per this deviceId's unlock state - used to pick up a
    // waypoint's real data right after it's unlocked (see WaypointLockManager), since the Tour
    // object already in memory still holds whatever was redacted when the list was first fetched.
    @GET("api/tours/{id}")
    Call<Tour> getTourById(@Path("id") String tourId, @Query("deviceId") String deviceId);

    @POST("api/tours")
    Call<Tour> createTour(@Body Tour tour);

    @PUT("api/tours/{id}")
    Call<Tour> updateTour(@Path("id") String tourId, @Body Tour tour);

    @POST("api/tours/copy/{tourId}")
    Call<Tour> copyTour(
            @Path("tourId") String tourId,
            @Body UserCopyRequest body
    );

    @POST("api/tours/{id}/waypoints/{index}/unlock")
    Call<UnlockResponse> unlockWaypoint(
            @Path("id") String tourId,
            @Path("index") int waypointIndex,
            @Body DeviceIdRequest body
    );

    @POST("api/tours/{id}/unlock-all")
    Call<UnlockResponse> unlockAllWaypoints(
            @Path("id") String tourId,
            @Body DeviceIdRequest body
    );

    @PATCH("api/tours/{id}/share")
    Call<Tour> shareTour(@Path("id") String tourId);

    @GET("api/tours/{id}/reviews")
    Call<ReviewsResponse> getTourReviews(@Path("id") String tourId);

    @POST("api/tours/{id}/reviews")
    Call<Review> submitReview(@Path("id") String tourId, @Body ReviewRequest body);

    @PATCH("api/tours/{id}/reviews")
    Call<Review> updateReview(@Path("id") String tourId, @Body ReviewRequest body);

    @HTTP(method = "DELETE", path = "api/tours/{id}/reviews", hasBody = true)
    Call<Void> deleteReview(@Path("id") String tourId, @Body UserIdRequest body);

    @PATCH("api/tours/{id}/waypoint")
    Call<Tour> addWaypoint(
            @Path("id") String tourId,
            @Body Tour.Waypoint waypoint
    );

    @Multipart
    @POST("api/tours/upload")
    Call<UploadResponse> uploadImage(@Part MultipartBody.Part image);

    // Exchanges a Firebase ID token (from FirebaseAuth, Email/Password or Google - see
    // LoginActivity) for this backend's own User row - the backend verifies the token
    // server-side (firebase-admin) before upserting, replacing the old client-asserted
    // google-login endpoint.
    @POST("api/auth/verify")
    Call<User> verifyFirebaseUser(@Body IdTokenRequest body);

    @POST("api/chat")
    Call<ChatResponse> sendChatMessage(@Body ChatRequest request);

    class UploadResponse {
        @SerializedName("imageUrl")
        private String imageUrl;
        public String getImageUrl() { return imageUrl; }
    }

    class UserCopyRequest {
        @SerializedName("userId")
        private String userId;
        @SerializedName("deviceId")
        private String deviceId;
        public UserCopyRequest(String userId, String deviceId) {
            this.userId = userId;
            this.deviceId = deviceId;
        }
    }

    class DeviceIdRequest {
        @SerializedName("deviceId")
        private String deviceId;
        public DeviceIdRequest(String deviceId) { this.deviceId = deviceId; }
    }

    class ReviewRequest {
        @SerializedName("userId")
        private String userId;
        @SerializedName("rating")
        private int rating;
        @SerializedName("comment")
        private String comment;
        public ReviewRequest(String userId, int rating, String comment) {
            this.userId = userId;
            this.rating = rating;
            this.comment = comment;
        }
    }

    class UserIdRequest {
        @SerializedName("userId")
        private String userId;
        public UserIdRequest(String userId) { this.userId = userId; }
    }

    class IdTokenRequest {
        @SerializedName("idToken")
        private String idToken;
        public IdTokenRequest(String idToken) { this.idToken = idToken; }
    }

    /** GET /api/tours/{id}/reviews response - avgRating/reviewCount are computed live server-side
     *  from the Review collection (not stored on Tour), see reviewController.getTourReviews. */
    class ReviewsResponse {
        private double avgRating;
        private int reviewCount;
        private List<Review> reviews;
        public double getAvgRating() { return avgRating; }
        public int getReviewCount() { return reviewCount; }
        public List<Review> getReviews() { return reviews; }
    }

    class UnlockResponse {
        private boolean unlocked;
        private boolean alreadyUnlocked;
        private int unlockedCount;
        public boolean isUnlocked() { return unlocked || alreadyUnlocked; }
        public int getUnlockedCount() { return unlockedCount; }
    }

    /** One turn of chat history sent back to the backend each request - this app has no
     *  server-side chat session, so the client is the source of truth for prior turns (see
     *  ChatbotActivity's chatHistory field). */
    class ChatMessageDto {
        String role; // "user" or "assistant"
        String content;
        public ChatMessageDto(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    class ChatRequest {
        String message;
        List<ChatMessageDto> history;
        // Non-null/non-empty only for a real Google session (see SessionManager.SignInType) -
        // its presence, not its verification, is what the backend uses to grant the
        // authenticated tier (no message cap) for this phase; see feasibility_report.md §1.5.
        @SerializedName("googleId")
        private String googleId;
        @SerializedName("email")
        private String email;

        public ChatRequest(String message, List<ChatMessageDto> history, String googleId, String email) {
            this.message = message;
            this.history = history;
            this.googleId = googleId;
            this.email = email;
        }
    }

    class ChatResponse {
        private String reply;
        private List<Tour> suggestedTours;
        private boolean capped;

        public String getReply() { return reply; }
        public List<Tour> getSuggestedTours() { return suggestedTours; }
        public boolean isCapped() { return capped; }
    }
}
