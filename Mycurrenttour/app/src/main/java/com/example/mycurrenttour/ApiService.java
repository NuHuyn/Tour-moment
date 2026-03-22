package com.example.mycurrenttour;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;
import com.example.mycurrenttour.Tour;
public interface ApiService {

    @GET("api/tours")
    Call<List<Tour>> getTours();

}