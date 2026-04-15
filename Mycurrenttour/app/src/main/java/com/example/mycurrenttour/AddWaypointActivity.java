package com.example.mycurrenttour;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AddWaypointActivity extends AppCompatActivity {

    private EditText edtName, edtNote, edtPrice, edtLat, edtLng;
    private ImageView imgPreview;
    private Button btnSelectImg, btnSaveWp, btnFinish;

    private String currentTourId;
    private String uploadedPath = "";
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_waypoint);

        initViews();
        apiService = ApiClient.getClient().create(ApiService.class);


        currentTourId = getIntent().getStringExtra("TOUR_ID");

        btnSelectImg.setOnClickListener(v -> openGallery());
        btnSaveWp.setOnClickListener(v -> onSavePointClick());


        btnFinish.setOnClickListener(v -> {
            Intent intent = new Intent(AddWaypointActivity.this, HomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void initViews() {
        edtName = findViewById(R.id.edtWpName);
        edtNote = findViewById(R.id.edtWpNote);
        edtPrice = findViewById(R.id.edtWpPrice);
        edtLat = findViewById(R.id.edtWpLat);
        edtLng = findViewById(R.id.edtWpLng);
        imgPreview = findViewById(R.id.imgWpPreview);
        btnSelectImg = findViewById(R.id.btnSelectImg);
        btnSaveWp = findViewById(R.id.btnSaveWp);
        btnFinish = findViewById(R.id.btnFinishJourney);
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
    }

    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri selectedUri = result.getData().getData();
                    imgPreview.setImageURI(selectedUri);
                    uploadImageToServer(selectedUri);
                }
            }
    );

    private void uploadImageToServer(Uri uri) {
        File file = FileUtils.getFile(AddWaypointActivity.this, uri);
        if (file == null) return;

        RequestBody requestFile = RequestBody.create(MediaType.parse("image/*"), file);
        MultipartBody.Part body = MultipartBody.Part.createFormData("image", file.getName(), requestFile);

        apiService.uploadImage(body).enqueue(new Callback<ApiService.UploadResponse>() {
            @Override
            public void onResponse(Call<ApiService.UploadResponse> call, Response<ApiService.UploadResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    uploadedPath = response.body().getImageUrl();
                    Toast.makeText(AddWaypointActivity.this, "Image uploaded!", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiService.UploadResponse> call, Throwable t) {
                Toast.makeText(AddWaypointActivity.this, "Image upload failed!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void onSavePointClick() {
        String name = edtName.getText().toString().trim();
        String latStr = edtLat.getText().toString().trim();
        String lngStr = edtLng.getText().toString().trim();

        if (name.isEmpty() || latStr.isEmpty() || lngStr.isEmpty()) {
            Toast.makeText(this, "Please enter name and coordinates!", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Tour.Waypoint wp = new Tour.Waypoint();
            wp.setLocationName(name);
            wp.setNote(edtNote.getText().toString());

            String priceStr = edtPrice.getText().toString();
            wp.setPrice(priceStr.isEmpty() ? 0 : Integer.parseInt(priceStr));

            Tour.Coordinate coord = new Tour.Coordinate();
            coord.setType("Point");
            coord.setCoordinates(Arrays.asList(
                    Double.parseDouble(latStr),
                    Double.parseDouble(lngStr)
            ));
            wp.setCoordinate(coord);

            if (!uploadedPath.isEmpty()) {
                wp.setPhotos(Collections.singletonList(uploadedPath));
            }


            apiService.addWaypoint(currentTourId, wp).enqueue(new Callback<Tour>() {
                @Override
                public void onResponse(Call<Tour> call, Response<Tour> response) {
                    if (response.isSuccessful()) {
                        Toast.makeText(AddWaypointActivity.this, "Trip saved", Toast.LENGTH_SHORT).show();
                        clearForm();
                    } else {
                        Toast.makeText(AddWaypointActivity.this, "Error: Could not save route", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<Tour> call, Throwable t) {
                    Toast.makeText(AddWaypointActivity.this, "Server Connection Error", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid coordinates or price!", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearForm() {
        edtName.setText("");
        edtNote.setText("");
        edtPrice.setText("");
        edtLat.setText("");
        edtLng.setText("");
        uploadedPath = "";
        imgPreview.setImageResource(R.drawable.centralvietnam);
    }
}