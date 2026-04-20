package com.example.mycurrenttour;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeActivity extends AppCompatActivity {

    private ImageView iconProfile;
    private EditText edtSearch;
    private RecyclerView recyclerTours, recyclerPopular;
    private TourAdapter adapter;
    private PopularAdapter popularAdapter;
    private ChipGroup chipGroup;

    private List<Tour> originalList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        initViews();
        loadTours();
        setupBottomNav();
    }

    private void initViews() {
        iconProfile = findViewById(R.id.imgProfile);
        edtSearch = findViewById(R.id.edtSearch);
        chipGroup = findViewById(R.id.chipGroup);
        recyclerPopular = findViewById(R.id.recyclerPopular);
        recyclerTours = findViewById(R.id.recyclerTours);

        // Thiết lập RecyclerView
        recyclerPopular.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerTours.setLayoutManager(new GridLayoutManager(this, 2));

        adapter = new TourAdapter(new ArrayList<>(), true);
        recyclerTours.setAdapter(adapter);

        // Xử lý Profile
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getPhotoUrl() != null) {
            Picasso.get().load(user.getPhotoUrl()).into(iconProfile);
        }
        iconProfile.setOnClickListener(v -> showLogoutDialog());

        // LOGIC TÌM KIẾM THEO CHỮ NHẬP
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterTours(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        // LOGIC TÌM KIẾM QUA CHIP (Gợi ý nhanh)
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                filterTours("");
            } else {
                Chip chip = findViewById(checkedIds.get(0));
                String category = chip.getText().toString();
                if (category.equals("Tất cả")) filterTours("");
                else filterTours(category);
            }
        });
    }

    private void filterTours(String query) {
        if (originalList == null || adapter == null) return;

        if (query.isEmpty()) {
            adapter.updateList(originalList);
            return;
        }

        List<Tour> filtered = new ArrayList<>();
        String lowerQuery = query.toLowerCase().trim();

        for (Tour t : originalList) {
            boolean isMatch = false;
            // Khớp tên Tour
            if (t.getTitle() != null && t.getTitle().toLowerCase().contains(lowerQuery)) isMatch = true;

            // Khớp địa danh trong Waypoints
            if (!isMatch && t.getWaypoints() != null) {
                for (Tour.Waypoint wp : t.getWaypoints()) {
                    if (wp.getLocationName() != null && wp.getLocationName().toLowerCase().contains(lowerQuery)) {
                        isMatch = true;
                        break;
                    }
                }
            }
            if (isMatch) filtered.add(t);
        }
        adapter.updateList(filtered);
    }

    private void loadTours() {
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        apiService.getSharedTours().enqueue(new Callback<List<Tour>>() {
            @Override
            public void onResponse(Call<List<Tour>> call, Response<List<Tour>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    originalList = response.body();
                    adapter.updateList(originalList);

                    // Hiển thị danh sách Popular (lấy 4 tour đầu)
                    List<Tour> popularList = originalList.subList(0, Math.min(originalList.size(), 4));
                    popularAdapter = new PopularAdapter(HomeActivity.this, popularList);
                    recyclerPopular.setAdapter(popularAdapter);
                }
            }
            @Override
            public void onFailure(Call<List<Tour>> call, Throwable t) {
                Toast.makeText(HomeActivity.this, "Network Error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Log out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Yes", (d, w) -> {
                    FirebaseAuth.getInstance().signOut();
                    startActivity(new Intent(this, LoginActivity.class));
                    finish();
                })
                .setNegativeButton("No", null).show();
    }

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottomNavigation);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_favorite) startActivity(new Intent(this, ReelsActivity.class));
            else if (id == R.id.nav_trip) startActivity(new Intent(this, MyTourActivity.class));
            return true;
        });
    }
}