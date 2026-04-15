package com.example.mycurrenttour;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ReelsActivity extends AppCompatActivity {

    private RecyclerView recyclerReels;
    private ReelsAdapter adapter;
    private List<Tour> reelsList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reels);


        recyclerReels = findViewById(R.id.recyclerFavorite);


        recyclerReels.setLayoutManager(new LinearLayoutManager(this));


        PagerSnapHelper snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(recyclerReels);

        loadReelsData();
    }

    private void loadReelsData() {
        ApiService apiService = ApiClient.getClient().create(ApiService.class);

        apiService.getSharedTours().enqueue(new Callback<List<Tour>>() {
            @Override
            public void onResponse(Call<List<Tour>> call, Response<List<Tour>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    reelsList = response.body();
                    adapter = new ReelsAdapter(reelsList);
                    recyclerReels.setAdapter(adapter);
                }
            }

            @Override
            public void onFailure(Call<List<Tour>> call, Throwable t) {
                Toast.makeText(ReelsActivity.this, "Unable to load video from the server", Toast.LENGTH_SHORT).show();
            }
        });
    }
}