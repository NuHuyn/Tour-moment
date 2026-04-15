package com.example.mycurrenttour;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeActivity extends AppCompatActivity {

    private ImageView iconProfile;
    private RecyclerView recyclerTours, recyclerPopular;
    private TourAdapter adapter;
    private PopularAdapter popularAdapter;
    private ChipGroup chipGroup, selectedGroup;

    private List<Tour> originalList = new ArrayList<>();
    private Map<String, List<String>> mockData = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        initViews();
        checkUserLogin();
        initMockData();


        adapter = new TourAdapter(new ArrayList<>(), true);
        recyclerTours.setAdapter(adapter);

        loadTours();
        setupFilterLogic();
        setupBottomNav();
    }

    private void initViews() {
        iconProfile = findViewById(R.id.imgProfile);
        chipGroup = findViewById(R.id.chipGroup);
        selectedGroup = findViewById(R.id.selectedGroup);
        recyclerPopular = findViewById(R.id.recyclerPopular);
        recyclerTours = findViewById(R.id.recyclerTours);

        recyclerPopular.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));


        recyclerTours.setLayoutManager(new GridLayoutManager(this, 2));

        iconProfile.setOnClickListener(v -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                showLogoutDialog(user.getDisplayName());
            } else {
                startActivity(new Intent(HomeActivity.this, LoginActivity.class));
            }
        });
    }

    private void showLogoutDialog(String userName) {
        new AlertDialog.Builder(this)
                .setTitle("Log out")
                .setMessage("Hello " + userName + ", Are you sure you want to log out?")
                .setPositiveButton("Log out", (dialog, which) -> signOut())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void checkUserLogin() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getPhotoUrl() != null) {
            Picasso.get()
                    .load(user.getPhotoUrl())
                    .placeholder(R.drawable.centralvietnam)
                    .into(iconProfile);
        }
    }

    private void signOut() {
        FirebaseAuth.getInstance().signOut();
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken("312403546799-hjn39b9pu5c5aquisn640orj81qatqub.apps.googleusercontent.com")
                .requestEmail()
                .build();
        GoogleSignInClient mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
            Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            Toast.makeText(HomeActivity.this, "See you again!", Toast.LENGTH_SHORT).show();
        });
    }

    private void initMockData() {
        mockData.put("Tất cả", Arrays.asList("Đà Lạt", "TP.HCM", "Hà Nội", "Đà Nẵng"));
        mockData.put("Việt Nam", Arrays.asList("Đà Lạt", "TP.HCM", "Vũng Tàu"));
        mockData.put("Quốc Tế", Arrays.asList("Bangkok", "Tokyo", "Seoul"));
    }

    private void loadTours() {
        ApiService apiService = ApiClient.getClient().create(ApiService.class);

        apiService.getSharedTours().enqueue(new Callback<List<Tour>>() {
            @Override
            public void onResponse(Call<List<Tour>> call, Response<List<Tour>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    originalList = response.body();


                    adapter.updateList(originalList);

                    List<Tour> popularList = new ArrayList<>();
                    int limit = Math.min(originalList.size(), 4);
                    for (int i = 0; i < limit; i++) {
                        popularList.add(originalList.get(i));
                    }
                    popularAdapter = new PopularAdapter(HomeActivity.this, popularList);
                    recyclerPopular.setAdapter(popularAdapter);
                }
            }

            @Override
            public void onFailure(Call<List<Tour>> call, Throwable t) {
                Toast.makeText(HomeActivity.this, "Server connection error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupFilterLogic() {
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            Chip chip = (Chip) chipGroup.getChildAt(i);
            chip.setOnClickListener(v -> {
                String selected = chip.getText().toString();
                showSubFilter(selected);
                filterTours(selected);
            });
        }
    }

    private void showSubFilter(String parent) {
        selectedGroup.removeAllViews();
        List<String> list = mockData.get(parent);
        if (list == null) return;

        for (String item : list) {
            Chip chip = new Chip(this);
            chip.setText(item);
            chip.setClickable(true);
            chip.setCheckable(true);
            chip.setOnClickListener(v -> filterTours(item));
            selectedGroup.addView(chip);
        }
    }

    private void filterTours(String query) {
        if (originalList == null || adapter == null) return;

        if (query.equals("All")) {
            adapter.updateList(originalList);
        } else {
            List<Tour> filtered = new ArrayList<>();
            for (Tour t : originalList) {
                boolean match = false;
                if (t.getTitle() != null && t.getTitle().toLowerCase().contains(query.toLowerCase())) {
                    match = true;
                }
                if (t.getWaypoints() != null) {
                    for (Tour.Waypoint wp : t.getWaypoints()) {
                        if (wp.getLocationName() != null && wp.getLocationName().toLowerCase().contains(query.toLowerCase())) {
                            match = true;
                            break;
                        }
                    }
                }
                if (match) filtered.add(t);
            }
            adapter.updateList(filtered);
        }
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_explore) return true;
            if (id == R.id.nav_favorite) {
                startActivity(new Intent(this, ReelsActivity.class));
                return true;
            }
            if (id == R.id.nav_trip) {
                startActivity(new Intent(this, MyTourActivity.class));
                return true;
            }
            if (id == R.id.nav_profile) {
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user != null) showLogoutDialog(user.getDisplayName());
                return true;
            }
            return false;
        });
    }
}