package com.example.mycurrenttour;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CreateTourActivity extends AppCompatActivity {

    private EditText edtTitle, edtTourDesc;
    private Button btnPickStartDate, btnPickEndDate, btnSaveAndStart, btnUploadCover;
    private ImageView imgTourCover;
    private Spinner spnStatus;
    private LinearLayout waypointContainer;
    private ExtendedFloatingActionButton btnAddWaypoint;
    private View layoutPlaceholder;

    private Date startDate = new Date();
    private Date endDate = new Date();
    private Uri pendingCoverUri;
    private boolean isSelectingCover = true;
    private ImageView currentWpImageView;

    private ApiService apiService;
    private SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
    private SimpleDateFormat displayFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_tour);

        apiService = ApiClient.getClient().create(ApiService.class);

        initViews();
        setupStatusSpinner();

        btnPickStartDate.setOnClickListener(v -> showDatePicker(true));
        btnPickEndDate.setOnClickListener(v -> showDatePicker(false));

        btnUploadCover.setOnClickListener(v -> {
            isSelectingCover = true;
            openGallery();
        });

        btnAddWaypoint.setOnClickListener(v -> addWaypointField());
        addWaypointField(); // Mặc định tạo 1 điểm đầu tiên

        btnSaveAndStart.setOnClickListener(v -> startSavingProcess());
    }

    private void initViews() {
        edtTitle = findViewById(R.id.edtTourTitle);
        edtTourDesc = findViewById(R.id.edtTourDesc);
        btnPickStartDate = findViewById(R.id.btnPickStartDate);
        btnPickEndDate = findViewById(R.id.btnPickEndDate);
        btnSaveAndStart = findViewById(R.id.btnSaveAndStart);
        spnStatus = findViewById(R.id.spnStatus);
        imgTourCover = findViewById(R.id.imgTourCover);
        btnUploadCover = findViewById(R.id.btnUploadCover);
        waypointContainer = findViewById(R.id.waypointContainer);
        btnAddWaypoint = findViewById(R.id.btnAddWaypoint);
        layoutPlaceholder = findViewById(R.id.layoutPlaceholder);

        btnPickStartDate.setText(displayFormat.format(startDate));
        btnPickEndDate.setText(displayFormat.format(endDate));
    }

    private void setupStatusSpinner() {
        String[] opts = {"Upcoming", "Ongoing", "Completed"};
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, opts);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        if (spnStatus != null) spnStatus.setAdapter(ad);
    }

    private void startSavingProcess() {
        String title = edtTitle.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(this, "Please enter the trip name", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSaveAndStart.setEnabled(false);
        btnSaveAndStart.setText("Processing...");

        if (pendingCoverUri != null) {
            uploadCoverThenSave(title);
        } else {
            saveFullTour(title, "");
        }
    }

    private void uploadCoverThenSave(String title) {
        File file = FileUtils.getFile(this, pendingCoverUri);
        if (file == null) { resetSaveButton(); return; }

        RequestBody requestFile = RequestBody.create(MediaType.parse("image/*"), file);
        MultipartBody.Part body = MultipartBody.Part.createFormData("image", file.getName(), requestFile);

        apiService.uploadImage(body).enqueue(new Callback<ApiService.UploadResponse>() {
            @Override
            public void onResponse(Call<ApiService.UploadResponse> call, Response<ApiService.UploadResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    saveFullTour(title, response.body().getImageUrl());
                } else {
                    Toast.makeText(CreateTourActivity.this, "Cover image upload error", Toast.LENGTH_SHORT).show();
                    resetSaveButton();
                }
            }
            @Override
            public void onFailure(Call<ApiService.UploadResponse> call, Throwable t) { resetSaveButton(); }
        });
    }

    private void saveFullTour(String title, String coverUrl) {
        Tour tour = new Tour();
        tour.setTitle(title);
        tour.setDescription(edtTourDesc.getText().toString());
        tour.setImageUrl(coverUrl);
        tour.setStartDate(isoFormat.format(startDate));
        tour.setEndDate(isoFormat.format(endDate));
        tour.setStatus(spnStatus.getSelectedItem().toString());

        List<Tour.Waypoint> waypointList = new ArrayList<>();
        for (int i = 0; i < waypointContainer.getChildCount(); i++) {
            View v = waypointContainer.getChildAt(i);

            // Find new input views
            TextInputEditText edtWpName = v.findViewById(R.id.edtWpLocationName);
            TextInputEditText edtWpPrice = v.findViewById(R.id.edtWpPrice);
            TextInputEditText edtWpNote = v.findViewById(R.id.edtWpNote);
            TextInputEditText edtLat = v.findViewById(R.id.edtWpLat);
            TextInputEditText edtLng = v.findViewById(R.id.edtWpLng);
            ImageView imgWp = v.findViewById(R.id.imgWpPreview);

            Tour.Waypoint wp = new Tour.Waypoint();
            wp.setLocationName(edtWpName.getText().toString().trim());
            wp.setNote(edtWpNote.getText().toString().trim());

            // Handle price
            String pStr = edtWpPrice.getText().toString().trim();
            wp.setPrice(pStr.isEmpty() ? 0 : Integer.parseInt(pStr));

            // --- GET IMAGE URL FROM TAG ---
            if (imgWp.getTag() != null) {
                String wpUrl = (String) imgWp.getTag();
                wp.setPhotos(Arrays.asList(wpUrl));
            }

            // Handle coordinates [lon, lat]
            try {
                double lat = Double.parseDouble(edtLat.getText().toString());
                double lng = Double.parseDouble(edtLng.getText().toString());
                Tour.Coordinate coord = new Tour.Coordinate();
                coord.setType("Point");
                coord.setCoordinates(Arrays.asList(lng, lat));
                wp.setCoordinate(coord);
            } catch (Exception e) { Log.e("CreateTour", "Coordinate error"); }

            waypointList.add(wp);
        }
        tour.setWaypoints(waypointList);

        apiService.createTour(tour).enqueue(new Callback<Tour>() {
            @Override
            public void onResponse(Call<Tour> call, Response<Tour> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(CreateTourActivity.this, "Saved successfully!", Toast.LENGTH_SHORT).show();
                    finish();
                } else { resetSaveButton(); }
            }
            @Override
            public void onFailure(Call<Tour> call, Throwable t) { resetSaveButton(); }
        });
    }

    private void uploadWaypointImage(Uri uri, ImageView targetImageView) {
        File file = FileUtils.getFile(this, uri);
        if (file == null) return;

        RequestBody requestFile = RequestBody.create(MediaType.parse("image/*"), file);
        MultipartBody.Part body = MultipartBody.Part.createFormData("image", file.getName(), requestFile);

        apiService.uploadImage(body).enqueue(new Callback<ApiService.UploadResponse>() {
            @Override
            public void onResponse(Call<ApiService.UploadResponse> call, Response<ApiService.UploadResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // SAVE URL INTO TAG TO RETRIEVE WHEN SAVING TOUR
                    targetImageView.setTag(response.body().getImageUrl());
                    Toast.makeText(CreateTourActivity.this, "Waypoint image uploaded successfully", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<ApiService.UploadResponse> call, Throwable t) {}
        });
    }

    private void addWaypointField() {
        View v = getLayoutInflater().inflate(R.layout.item_waypoint, null);
        ImageView imgWpPreview = v.findViewById(R.id.imgWpPreview);
        Button btnSelectImg = v.findViewById(R.id.btnSelectImg);

        ((TextView)v.findViewById(R.id.txtWpTitle)).setText("Waypoint " + (waypointContainer.getChildCount() + 1));

        btnSelectImg.setOnClickListener(view -> {
            isSelectingCover = false;
            currentWpImageView = imgWpPreview;
            openGallery();
        });

        v.findViewById(R.id.btnRemoveWp).setOnClickListener(view -> waypointContainer.removeView(v));
        waypointContainer.addView(v);
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
                    Uri uri = result.getData().getData();
                    if (isSelectingCover) {
                        pendingCoverUri = uri;
                        imgTourCover.setVisibility(View.VISIBLE);
                        imgTourCover.setImageURI(uri);
                        if (layoutPlaceholder != null) layoutPlaceholder.setVisibility(View.GONE);
                    } else if (currentWpImageView != null) {
                        currentWpImageView.setVisibility(View.VISIBLE);
                        currentWpImageView.setImageURI(uri);
                        View parent = (View) currentWpImageView.getParent();
                        View placeholder = parent.findViewById(R.id.wpPlaceholder);
                        if (placeholder != null) placeholder.setVisibility(View.GONE);


                        uploadWaypointImage(uri, currentWpImageView);
                    }
                }
            }
    );

    private void resetSaveButton() {
        btnSaveAndStart.setEnabled(true);
        btnSaveAndStart.setText("START THE JOURNEY");
    }

    private void showDatePicker(boolean isStart) {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (v, y, m, d) -> {
            c.set(y, m, d);
            if (isStart) {
                startDate = c.getTime();
                btnPickStartDate.setText(displayFormat.format(startDate));
            } else {
                endDate = c.getTime();
                btnPickEndDate.setText(displayFormat.format(endDate));
            }
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }
}