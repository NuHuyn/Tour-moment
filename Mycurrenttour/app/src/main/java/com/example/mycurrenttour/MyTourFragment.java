package com.example.mycurrenttour;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

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
    private ProgressBar progressMyTour;
    private TourAdapter adapter;
    private List<Tour> allToursFromApi = new ArrayList<>();
    private String currentFilter = "Upcoming";
    // Bumped on every loadMyTours() call and captured per-request; a response is only applied if
    // it's still the most recent request when it lands. Without this, an older/slower response
    // (e.g. a filter switch triggered by TourAdapter.onJourneyStarted firing while this fragment
    // is paused/backgrounded on OngoingMapActivity) can arrive after a newer one and silently
    // overwrite the list the user is actually looking at with stale/wrong-filter data.
    private int loadRequestId = 0;

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
        progressMyTour = root.findViewById(R.id.progressMyTour);
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
        syncFilterChipSelection(status);
        loadMyTours();
    }

    /** Keeps the filter chips' checked/highlighted state in sync with currentFilter even when
     *  switchFilter() is called programmatically (TourAdapter.onJourneyStarted) instead of from a
     *  chip tap. The chips only auto-check themselves when the user physically taps one - a code
     *  path like onJourneyStarted changing currentFilter without going through a real tap left the
     *  UI showing "Upcoming" highlighted while the list underneath had actually been reloaded under
     *  "Ongoing", which is what made tours look like they'd silently vanished after starting a
     *  journey and navigating back. */
    private void syncFilterChipSelection(String status) {
        if (btnUpcoming == null || btnOngoing == null || btnCompleted == null) return;
        btnUpcoming.setChecked(status.equalsIgnoreCase("Upcoming"));
        btnOngoing.setChecked(status.equalsIgnoreCase("Ongoing"));
        btnCompleted.setChecked(status.equalsIgnoreCase("Completed"));
    }

    private void loadMyTours() {
        if (!isAdded()) return;
        if (MockDataProvider.USE_MOCK_DATA) {
            allToursFromApi = MockDataProvider.getMockTours();
            filterByStatus(currentFilter);
            return;
        }

        // userId is the backend User._id from LoginActivity's google-login call (SessionManager),
        // not a Firebase uid - Firebase Auth is never engaged in this app. null here means either
        // Guest sign-in or not signed in at all, so there is no "my tours" to show.
        String userId = SessionManager.getUserId(getContext());
        if (userId == null) return;

        ApiService apiService = ApiClient.getClient().create(ApiService.class);

        // Capture both the filter this request was made for and a generation id, so a
        // slower/older response landing after a newer request was already issued (e.g. the
        // onJourneyStarted-triggered reload racing with this screen's own onResume reload) gets
        // dropped instead of overwriting more current data.
        final String requestedFilter = currentFilter;
        final int requestId = ++loadRequestId;

        // Only the RecyclerView + chips are already on screen from a previous load, so a bare
        // spinner (not a full-screen blocker) is enough - it just needs to make the wait visible
        // instead of the screen sitting static. Most requests resolve fast enough that this is
        // barely noticeable, but it matters for the Cloud Run cold-start case (first request after
        // idle can take a couple seconds) where otherwise nothing on screen indicates anything is
        // happening.
        if (progressMyTour != null) progressMyTour.setVisibility(View.VISIBLE);

        apiService.getMyTours(userId, requestedFilter).enqueue(new Callback<List<Tour>>() {
            @Override
            public void onResponse(Call<List<Tour>> call, Response<List<Tour>> response) {
                if (!isAdded()) return;
                if (requestId != loadRequestId) return; // superseded by a newer loadMyTours() call
                if (progressMyTour != null) progressMyTour.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    allToursFromApi = response.body();
                    adapter.updateList(allToursFromApi);

                    if (allToursFromApi.isEmpty()) {
                        Toast.makeText(getContext(), getString(R.string.no_tours_found_format, localizedStatusLabel(requestedFilter)), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.e("API_ERROR", "Response failed: " + response.code());
                }
            }
            @Override
            public void onFailure(Call<List<Tour>> call, Throwable t) {
                if (!isAdded()) return;
                if (requestId != loadRequestId) return;
                if (progressMyTour != null) progressMyTour.setVisibility(View.GONE);
                Log.e("API_ERROR", "Failure: " + t.getMessage());
                Toast.makeText(getContext(), R.string.network_error, Toast.LENGTH_SHORT).show();
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
            Toast.makeText(getContext(), getString(R.string.no_tours_found_format, localizedStatusLabel(status)), Toast.LENGTH_SHORT).show();
        }
    }

    /** Maps the internal, API-facing status key ("Upcoming"/"Ongoing"/"Completed" - unchanged,
     *  since the backend/status comparisons above depend on these exact English values) to a
     *  localized word for display in Toast messages only. */
    private String localizedStatusLabel(String status) {
        if (getContext() == null) return status;
        if (status.equalsIgnoreCase("Upcoming")) return getString(R.string.status_upcoming);
        if (status.equalsIgnoreCase("Ongoing")) return getString(R.string.status_ongoing);
        if (status.equalsIgnoreCase("Completed")) return getString(R.string.status_completed);
        return status;
    }
}
