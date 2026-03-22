package com.example.mycurrenttour;

import android.util.Log;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;


import android.location.Location;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;

import com.bumptech.glide.Glide;
import com.google.android.gms.location.*;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;

import retrofit2.*;
import retrofit2.converter.gson.GsonConverterFactory;

public class TourDetailActivity extends AppCompatActivity {

    ImageView imgTour, iconWeather, iconMap;
    TextView txtTitle, txtPrice, txtBooking, txtWeather;

    // ✅ MAP
    MapView mapView;
    GoogleMap mMap;
    FusedLocationProviderClient fusedLocationClient;

    LatLng userLatLng;
    LatLng tourLatLng;

    String locationName;

    String API_KEY = "7cd5d05343fa81acb765e10039c4aa73";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tour_detail);

        imgTour = findViewById(R.id.imgTourDetail);
        txtTitle = findViewById(R.id.txtTitleDetail);
        txtPrice = findViewById(R.id.txtPriceDetail);
        txtBooking = findViewById(R.id.txtBooking);
        iconWeather = findViewById(R.id.iconWeather);
        txtWeather = findViewById(R.id.txtWeather);

        iconMap = findViewById(R.id.iconMap);

        iconMap.setOnClickListener(v -> {
            Log.d("DEBUG", "ICON MAP CLICKED");
            drawRoute();
        });

        // ✅ MAP VIEW
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.onResume();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        String title = getIntent().getStringExtra("title");
        int price = getIntent().getIntExtra("price", 0);
        String imageUrl = getIntent().getStringExtra("image");
        locationName = getIntent().getStringExtra("location");
        tourLatLng = new LatLng(12.8477, 108.2557);
        userLatLng = new LatLng(10.8231, 106.6297);

        String startDateRaw = getIntent().getStringExtra("start_date");

        txtTitle.setText(title);
        txtPrice.setText("$" + price);



        Glide.with(this)
                .load(imageUrl)
                .into(imgTour);

        // ✅ FIX CRASH
        String startDate = "";
        if (startDateRaw != null && startDateRaw.length() >= 10) {
            startDate = startDateRaw.substring(0, 10);
        }

        // ================= MAP =================
        mapView.getMapAsync(googleMap -> {
            mMap = googleMap;

            // Bắt sự kiện click map
            mMap.setOnMapClickListener(latLng -> {
                drawRoute(); // khi click thì vẽ line
            });
        });

        // ================= WEATHER =================
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.openweathermap.org/data/2.5/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        WeatherApi api = retrofit.create(WeatherApi.class);

        String finalStartDate = startDate;

        iconWeather.setOnClickListener(v -> {

            txtWeather.setText("Loading...");

            api.getForecastByLatLng(
                            tourLatLng.latitude,
                            tourLatLng.longitude,
                            API_KEY,
                            "metric"
                    )
                    .enqueue(new Callback<ForecastResponse>() {

                        @Override
                        public void onResponse(Call<ForecastResponse> call, Response<ForecastResponse> response) {

                            if (response.isSuccessful() && response.body() != null) {

                                boolean found = false;

                                for (ForecastResponse.Item item : response.body().list) {

                                    if (item.dt_txt != null && item.dt_txt.startsWith(finalStartDate)) {

                                        float temp = item.main.temp;
                                        txtWeather.setText("🌡 " + temp + "°C");
                                        found = true;
                                        break;
                                    }
                                }

                                if (!found) {
                                    txtWeather.setText("No forecast available");
                                }
                            }
                        }

                        @Override
                        public void onFailure(Call<ForecastResponse> call, Throwable t) {
                            txtWeather.setText("Error loading weather");
                        }
                    });
        });

        // ================= BOOKING =================
        txtBooking.setOnClickListener(v -> {
            Intent intent = new Intent(TourDetailActivity.this, BookingActivity.class);
            intent.putExtra("title", title);
            intent.putExtra("price", price);
            startActivity(intent);
        });
    }

    // ================= GET USER LOCATION =================
    private void getUserLocation() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }

        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(5000);

        LocationCallback locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;

                for (Location location : locationResult.getLocations()) {

                    userLatLng = new LatLng(
                            location.getLatitude(),
                            location.getLongitude()
                    );

                    Log.d("DEBUG", "USER: " + userLatLng);
                    Log.d("DEBUG", "TOUR: " + tourLatLng);
                    drawRoute();

                    // chỉ cần lấy 1 lần là đủ
                    fusedLocationClient.removeLocationUpdates(this);


                    break;
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                getMainLooper()
        );
    }



    // ================= DRAW =================
    private void drawRoute() {

        Log.d("DEBUG", "DRAW ROUTE CALLED");

        if (mMap == null || userLatLng == null || tourLatLng == null) {
            Log.d("DEBUG", "NULL DATA");
            return;
        }

        mMap.clear();

        // marker
        mMap.addMarker(new MarkerOptions().position(userLatLng).title("You"));
        mMap.addMarker(new MarkerOptions().position(tourLatLng).title(locationName));

        // line đỏ
        PolylineOptions polyline = new PolylineOptions()
                .add(userLatLng)
                .add(tourLatLng)
                .width(10)
                .color(android.graphics.Color.RED);

        mMap.addPolyline(polyline);

        // 🔥 QUAN TRỌNG: zoom vào giữa 2 điểm
        LatLng center = new LatLng(
                (userLatLng.latitude + tourLatLng.latitude) / 2,
                (userLatLng.longitude + tourLatLng.longitude) / 2
        );

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(center, 6));
    }
    // ================= LIFECYCLE =================
    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }
}