package com.example.mycurrenttour;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * "My Tour" tab (nav_trip) - was MyTourActivity. Moved into a Fragment hosted by
 * HomeActivity's fragmentContainer so the bottom nav bar stays fixed across tabs.
 */
public class MyTourFragment extends Fragment implements TourAdapter.OnTourActionListener {
    private Chip btnTrip, btnUpcoming, btnOngoing, btnCompleted;
    private RecyclerView recyclerMyTours;
    private TourAdapter adapter;
    private List<Tour> allToursFromApi = new ArrayList<>();
    private String currentFilter = "Upcoming";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_my_tour, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupClickListeners();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadMyTours();
    }

    private void initViews(View root) {
        btnTrip = root.findViewById(R.id.btnTrip);
        btnUpcoming = root.findViewById(R.id.btnUpcoming);
        btnOngoing = root.findViewById(R.id.btnOngoing);
        btnCompleted = root.findViewById(R.id.btnCompleted);
        recyclerMyTours = root.findViewById(R.id.recyclerMyTours);
        recyclerMyTours.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new TourAdapter(new ArrayList<>(), false, this);
        recyclerMyTours.setAdapter(adapter);

        BottomNavScrollHelper.attach((ScrollView) root.findViewById(R.id.scrollMyTour), (HomeActivity) requireActivity());
    }

    private void setupClickListeners() {
        btnTrip.setOnClickListener(v -> startActivity(new Intent(getContext(), CreateTourActivity.class)));
        btnUpcoming.setOnClickListener(v -> switchFilter("Upcoming"));
        btnOngoing.setOnClickListener(v -> switchFilter("Ongoing"));
        btnCompleted.setOnClickListener(v -> switchFilter("Completed"));
    }

    /** Called by TourAdapter after "Start the journey" succeeds, to jump this tab to the Ongoing filter. */
    @Override
    public void onJourneyStarted(Tour tour) {
        switchFilter("Ongoing");
    }

    public void switchFilter(String status) {
        this.currentFilter = status;
        loadMyTours();
    }

    private void loadMyTours() {
        if (!isAdded()) return;
        // TODO: set USE_MOCK_DATA = false khi backend sẵn sàng
        // Đặt TRƯỚC check FirebaseUser: màn này từng trắng vì login thật (syncUserToBackend)
        // cũng gọi API thật nên không đăng nhập được khi backend tắt -> currentUser() == null
        // -> return sớm trước khi kịp chạy nhánh mock. Discover (DiscoveryFragment) không bị vì
        // không gate theo currentUser.
        if (MockDataProvider.USE_MOCK_DATA) {
            allToursFromApi = MockDataProvider.getMockTours();
            filterByStatus(currentFilter);
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        ApiService apiService = ApiClient.getClient().create(ApiService.class);

        apiService.getMyTours(user.getUid(), currentFilter).enqueue(new Callback<List<Tour>>() {
            @Override
            public void onResponse(Call<List<Tour>> call, Response<List<Tour>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null) {
                    allToursFromApi = response.body();
                    adapter.updateList(allToursFromApi);

                    if (allToursFromApi.isEmpty()) {
                        Toast.makeText(getContext(), "No " + currentFilter + " tours found.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.e("API_ERROR", "Response failed: " + response.code());
                }
            }
            @Override
            public void onFailure(Call<List<Tour>> call, Throwable t) {
                if (!isAdded()) return;
                Log.e("API_ERROR", "Failure: " + t.getMessage());
                Toast.makeText(getContext(), "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterByStatus(String status) {
        List<Tour> filteredList = new ArrayList<>();
        if (allToursFromApi == null) return;

        for (Tour tour : allToursFromApi) {
            String tourStatus = (tour.getStatus() != null) ? tour.getStatus().trim() : "";

            if (status.equalsIgnoreCase("Completed")) {
                if (tourStatus.equalsIgnoreCase("Completed") || tourStatus.equalsIgnoreCase("completed")) {
                    filteredList.add(tour);
                }
            } else {
                if (tourStatus.equalsIgnoreCase(status)) {
                    filteredList.add(tour);
                }
            }
        }
        adapter.updateList(filteredList);
        if (filteredList.isEmpty()) {
            Toast.makeText(getContext(), "No " + status + " tours found.", Toast.LENGTH_SHORT).show();
        }
    }
}
