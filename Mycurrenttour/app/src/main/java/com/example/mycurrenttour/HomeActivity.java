package com.example.mycurrenttour;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import android.os.Bundle;
import android.widget.Toast;
import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;

public class HomeActivity extends AppCompatActivity {

    RecyclerView recyclerTours;
    TourAdapter adapter;

    RecyclerView recyclerPopular;
    PopularAdapter popularAdapter;

    ChipGroup chipGroup, selectedGroup;
    List<Tour> originalList = new ArrayList<>();

    Map<String, List<String>> mockData = new HashMap<>();

    private void showSubFilter(String parent) {

        selectedGroup.removeAllViews();

        List<String> list = mockData.get(parent);

        if (list == null) return;

        for (String item : list) {

            Chip chip = new Chip(this);
            chip.setText(item);
            chip.setClickable(true);

            chip.setOnClickListener(v -> {
                Toast.makeText(this, "Bạn chọn: " + item, Toast.LENGTH_SHORT).show();
            });

            selectedGroup.addView(chip);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);


        mockData.put("Tất cả", Arrays.asList("Việt Nam", "Thái Lan", "Nhật Bản", "Hàn Quốc"));

        mockData.put("Việt Nam", Arrays.asList("Hà Nội", "Đà Nẵng", "Vũng Tàu"));
        mockData.put("Thái Lan", Arrays.asList("Bangkok", "Phuket"));
        mockData.put("Nhật Bản", Arrays.asList("Tokyo", "Osaka"));
        mockData.put("Hàn Quốc", Arrays.asList("Seoul", "Busan"));

        // 🔥 ánh xạ view
        chipGroup = findViewById(R.id.chipGroup);
        selectedGroup = findViewById(R.id.selectedGroup);

        recyclerPopular = findViewById(R.id.recyclerPopular);
        recyclerPopular.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        );

        recyclerTours = findViewById(R.id.recyclerTours);
        recyclerTours.setLayoutManager(new GridLayoutManager(this, 2));

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);

        loadTours();

        // 🔥 xử lý click CHIP
        for (int i = 0; i < chipGroup.getChildCount(); i++) {

            Chip chip = (Chip) chipGroup.getChildAt(i);

            chip.setOnClickListener(v -> {

                String selected = chip.getText().toString();

                showSubFilter(selected);

            });
        }





        // bottom nav
        bottomNav.setOnItemSelectedListener(item -> {

            if (item.getItemId() == R.id.nav_explore) return true;

            else if (item.getItemId() == R.id.nav_favorite) {
                startActivity(new Intent(this, FavoriteActivity.class));
                return true;
            }

            else if (item.getItemId() == R.id.nav_trip) {
                startActivity(new Intent(this, MyTourActivity.class));
                return true;
            }

            else if (item.getItemId() == R.id.nav_profile) {
                Toast.makeText(this, "Profile", Toast.LENGTH_SHORT).show();
                return true;
            }

            return false;
        });
    }



    // ================= LOAD DATA =================
    private void loadTours() {

        ApiService apiService = ApiClient.getClient().create(ApiService.class);

        Call<List<Tour>> call = apiService.getTours();

        call.enqueue(new Callback<List<Tour>>() {

            @Override
            public void onResponse(Call<List<Tour>> call, Response<List<Tour>> response) {

                if (response.isSuccessful() && response.body() != null) {

                    List<Tour> tours = response.body();

                    // 🔥 LƯU LIST GỐC
                    originalList = tours;

                    // list tour
                    adapter = new TourAdapter(tours);
                    recyclerTours.setAdapter(adapter);

                    // popular
                    List<Tour> popularList = new ArrayList<>();
                    int start = Math.max(0, tours.size() - 4);

                    for (int i = start; i < tours.size(); i++) {
                        popularList.add(tours.get(i));
                    }

                    popularAdapter = new PopularAdapter(HomeActivity.this, popularList);
                    recyclerPopular.setAdapter(popularAdapter);
                }
            }

            @Override
            public void onFailure(Call<List<Tour>> call, Throwable t) {
                Toast.makeText(HomeActivity.this, "Failed to load tours", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ================= ADD CHIP =================
    private void addSelectedFilter(String name) {

        // tránh trùng chip
        for (int i = 0; i < selectedGroup.getChildCount(); i++) {
            Chip existing = (Chip) selectedGroup.getChildAt(i);
            if (existing.getText().toString().equals(name)) {
                return;
            }
        }

        Chip chip = new Chip(this);
        chip.setText(name);
        chip.setCloseIconVisible(true);

        chip.setOnCloseIconClickListener(v -> {
            selectedGroup.removeView(chip);

            // reset lại list
            adapter = new TourAdapter(originalList);
            recyclerTours.setAdapter(adapter);
        });

        selectedGroup.addView(chip);
    }

    // ================= FILTER =================
    private void filterTours(String location) {

        if (location.equals("Tất cả")) {
            adapter = new TourAdapter(originalList);
            recyclerTours.setAdapter(adapter);
            return;
        }

        List<Tour> filtered = new ArrayList<>();

        for (Tour t : originalList) {
            if (t.getLocation().toLowerCase().contains(location.toLowerCase())) {
                filtered.add(t);
            }
        }

        adapter = new TourAdapter(filtered);
        recyclerTours.setAdapter(adapter);
    }
}