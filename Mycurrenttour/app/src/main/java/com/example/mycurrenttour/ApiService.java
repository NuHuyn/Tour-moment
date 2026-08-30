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
        public UserCopyRequest(String userId) { this.userId = userId; }
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
