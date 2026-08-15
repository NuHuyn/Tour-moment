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

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * "My Travel" video feed tab (nav_favorite) - was ReelsActivity. Moved into a Fragment hosted
 * by HomeActivity's fragmentContainer so the bottom nav bar stays fixed across tabs.
 */
public class ReelsFragment extends Fragment {

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

        recyclerReels = view.findViewById(R.id.recyclerFavorite);
        recyclerReels.setLayoutManager(new LinearLayoutManager(getContext()));

        recyclerReels.setHasFixedSize(true);
        recyclerReels.setItemViewCacheSize(10);
        recyclerReels.setDrawingCacheEnabled(true);
        recyclerReels.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);

        PagerSnapHelper snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(recyclerReels);

        loadReelsData();
    }

    private void loadReelsData() {
        ApiService apiService = ApiClient.getClient().create(ApiService.class);

        apiService.getSharedTours().enqueue(new Callback<List<Tour>>() {
            @Override
            public void onResponse(Call<List<Tour>> call, Response<List<Tour>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null) {
                    reelsList = response.body();
                    adapter = new ReelsAdapter(reelsList);
                    recyclerReels.setAdapter(adapter);
                }
            }

            @Override
            public void onFailure(Call<List<Tour>> call, Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(getContext(), "Unable to load video from the server", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
