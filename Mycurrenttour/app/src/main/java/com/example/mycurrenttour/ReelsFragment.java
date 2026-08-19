package com.example.mycurrenttour;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * "Reels" tab (nav_favorite) - was ReelsActivity, now hosted by HomeActivity's fragmentContainer.
 *
 * Two states depending on whether the video data source (currently getSharedTours()) has
 * anything to show:
 *  - Empty (today's reality - short-form video hasn't shipped yet): clapperboard illustration +
 *    "Chưa có video" + a CTA back to Discovery.
 *  - Non-empty: the existing paged feed (recyclerFavorite / ReelsAdapter).
 */
public class ReelsFragment extends Fragment {

    private View layoutEmpty;
    private RecyclerView recyclerReels;
    private ReelsAdapter adapter;
    private List<Tour> reelsList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_reels, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        layoutEmpty = view.findViewById(R.id.layoutReelsEmpty);
        recyclerReels = view.findViewById(R.id.recyclerFavorite);

        recyclerReels.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerReels.setHasFixedSize(true);
        recyclerReels.setItemViewCacheSize(10);
        recyclerReels.setDrawingCacheEnabled(true);
        recyclerReels.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);

        PagerSnapHelper snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(recyclerReels);

        view.findViewById(R.id.btnExploreTours).setOnClickListener(v -> goToDiscovery());
        view.findViewById(R.id.icReelsSearch).setOnClickListener(v ->
                Toast.makeText(getContext(), getString(R.string.coming_soon_format, getString(R.string.search_label)), Toast.LENGTH_SHORT).show());

        BottomNavScrollHelper.attach(recyclerReels, (HomeActivity) requireActivity());

        // Start in the empty state until the data source actually answers back.
        showEmptyState();
        loadReelsData();
    }

    private void loadReelsData() {
        ApiService apiService = ApiClient.getClient().create(ApiService.class);

        apiService.getSharedTours().enqueue(new Callback<List<Tour>>() {
            @Override
            public void onResponse(Call<List<Tour>> call, Response<List<Tour>> response) {
                if (!isAdded()) return;

                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    reelsList = response.body();
                    adapter = new ReelsAdapter(reelsList);
                    recyclerReels.setAdapter(adapter);
                    showVideoFeed();
                } else {
                    showEmptyState();
                }
            }

            @Override
            public void onFailure(Call<List<Tour>> call, Throwable t) {
                if (!isAdded()) return;
                showEmptyState();
                Toast.makeText(getContext(), R.string.video_load_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showVideoFeed() {
        // TODO: item_reel_video currently pages through each tour's photos (SlideshowAdapter) as
        // a stand-in "reel". Once real short-form video capture/upload exists, this is where a
        // genuine video feed screen/player (e.g. ExoPlayer) should be wired in instead.
        layoutEmpty.setVisibility(View.GONE);
        recyclerReels.setVisibility(View.VISIBLE);
    }

    private void showEmptyState() {
        recyclerReels.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.VISIBLE);
    }

    private void goToDiscovery() {
        if (getActivity() == null) return;
        BottomNavigationView nav = getActivity().findViewById(R.id.bottomNavigation);
        if (nav != null) {
            nav.setSelectedItemId(R.id.nav_explore);
        }
    }
}
