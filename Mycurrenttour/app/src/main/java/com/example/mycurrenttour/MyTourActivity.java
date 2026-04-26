package com.example.mycurrenttour;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MyTourActivity extends AppCompatActivity {
    private Button btnTrip, btnUpcoming, btnOngoing, btnCompleted;
    private RecyclerView recyclerMyTours;
    private TourAdapter adapter;
    private List<Tour> allToursFromApi = new ArrayList<>();
    private String currentFilter = "Upcoming";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_tour);
        initViews();
        setupClickListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMyTours();
    }

    private void initViews() {
        btnTrip = findViewById(R.id.btnTrip);
        btnUpcoming = findViewById(R.id.btnUpcoming);
        btnOngoing = findViewById(R.id.btnOngoing);
        btnCompleted = findViewById(R.id.btnCompleted);
        recyclerMyTours = findViewById(R.id.recyclerMyTours);
        recyclerMyTours.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TourAdapter(new ArrayList<>(), false);
        recyclerMyTours.setAdapter(adapter);
    }

    private void setupClickListeners() {
        btnTrip.setOnClickListener(v -> startActivity(new Intent(this, CreateTourActivity.class)));
        btnUpcoming.setOnClickListener(v -> switchFilter("Upcoming"));
        btnOngoing.setOnClickListener(v -> switchFilter("Ongoing"));
        btnCompleted.setOnClickListener(v -> switchFilter("Completed"));
    }

    public void switchFilter(String status) {
        this.currentFilter = status;
        loadMyTours();
    }

    private void loadMyTours() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        
        // Truyền thêm currentFilter để fix lỗi "actual and formal argument lists differ in length"
        apiService.getMyTours(user.getUid(), currentFilter).enqueue(new Callback<List<Tour>>() {
            @Override
            public void onResponse(Call<List<Tour>> call, Response<List<Tour>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    allToursFromApi = response.body();
                    // Cập nhật danh sách lên giao diện
                    adapter.updateList(allToursFromApi);
                    
                    if (allToursFromApi.isEmpty()) {
                        Toast.makeText(MyTourActivity.this, "No " + currentFilter + " tours found.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.e("API_ERROR", "Response failed: " + response.code());
                }
            }
            @Override
            public void onFailure(Call<List<Tour>> call, Throwable t) {
                Log.e("API_ERROR", "Failure: " + t.getMessage());
                Toast.makeText(MyTourActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterByStatus(String status) {
        List<Tour> filteredList = new ArrayList<>();
        if (allToursFromApi == null) return;

        for (Tour tour : allToursFromApi) {
            String tourStatus = (tour.getStatus() != null) ? tour.getStatus().trim() : "";

            if (status.equalsIgnoreCase("Completed")) {
                // Chấp nhận cả "Completed" và "completed"
                if (tourStatus.equalsIgnoreCase("Completed") || tourStatus.equalsIgnoreCase("completed")) {
                    filteredList.add(tour);
                }
            } else {
                if (tourStatus.equalsIgnoreCase(status)) {
                    filteredList.add(tour);
                }
            }
        }

        // QUAN TRỌNG: Cập nhật adapter ngoài vòng lặp để app không bị treo
        adapter.updateList(filteredList);
        if (filteredList.isEmpty()) {
            Toast.makeText(this, "No " + status + " tours found.", Toast.LENGTH_SHORT).show();
        }
    }
}