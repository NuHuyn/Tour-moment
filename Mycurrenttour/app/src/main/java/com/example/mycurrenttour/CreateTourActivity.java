package com.example.mycurrenttour;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
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
import java.util.TimeZone;

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
    private ProgressDialog loadingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_tour);

        apiService = ApiClient.getClient().create(ApiService.class);
        loadingDialog = new ProgressDialog(this);
        loadingDialog.setCancelable(false);
        isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        initViews();
        setupStatusSpinner();

        btnPickStartDate.setOnClickListener(v -> showDatePicker(true));
        btnPickEndDate.setOnClickListener(v -> showDatePicker(false));
        btnUploadCover.setOnClickListener(v -> {
            isSelectingCover = true;
            openGallery();
        });

        btnAddWaypoint.setOnClickListener(v -> addWaypointField());
        addWaypointField();

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
        edtTitle.requestFocus();
    }

    private void setupStatusSpinner() {
        String[] opts = {"Upcoming", "Ongoing", "Completed"};
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, opts);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        if (spnStatus != null) {
            spnStatus.setAdapter(ad);
            spnStatus.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    btnSaveAndStart.setText(opts[position].equalsIgnoreCase("Completed") ? "SAVE THE MEMORY" : "START THE JOURNEY");
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }
    }

    private void startSavingProcess() {
        String title = edtTitle.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(this, R.string.error_trip_name_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (endDate.before(startDate)) {
            Toast.makeText(this, R.string.error_end_before_start, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!validateWaypoints()) return;

        btnSaveAndStart.setEnabled(false);
        if (pendingCoverUri != null) {
            uploadCoverThenSave(title);
        } else {
            saveFullTour(title, "");
        }
    }

    private void uploadCoverThenSave(String title) {
        loadingDialog.setMessage("Compressing and uploading cover image...");
        loadingDialog.show();

        new Thread(() -> {
            File file = FileUtils.getFile(this, pendingCoverUri);
            runOnUiThread(() -> {
                if (file == null) {
                    loadingDialog.dismiss();
                    resetSaveButton();
                    return;
                }

                String mimeType = getContentResolver().getType(pendingCoverUri);
                RequestBody requestFile = RequestBody.create(
                        MediaType.parse(mimeType != null ? mimeType : "image/jpeg"), file);
                MultipartBody.Part body = MultipartBody.Part.createFormData("image", file.getName(), requestFile);

                apiService.uploadImage(body).enqueue(new Callback<ApiService.UploadResponse>() {
                    @Override
                    public void onResponse(Call<ApiService.UploadResponse> call, Response<ApiService.UploadResponse> response) {
                        loadingDialog.dismiss();
                        if (response.isSuccessful() && response.body() != null) {
                            saveFullTour(title, response.body().getImageUrl());
                        } else {
                            Toast.makeText(CreateTourActivity.this, "Cover upload failed", Toast.LENGTH_SHORT).show();
                            resetSaveButton();
                        }
                    }
                    @Override
                    public void onFailure(Call<ApiService.UploadResponse> call, Throwable t) {
                        loadingDialog.dismiss();
                        resetSaveButton();
                    }
                });
            });
        }).start();
    }

    private void saveFullTour(String title, String coverUrl) {
        // The backend uses the verified Firebase uid as User._id; SessionManager stores the same
        // stable id after POST /api/auth/verify succeeds.
        String userId = SessionManager.getUserId(this);
        if (userId == null) {
            Toast.makeText(this, "User not logged in!", Toast.LENGTH_SHORT).show();
            resetSaveButton();
            return;
        }

        loadingDialog.setMessage("Saving tour...");
        loadingDialog.show();

        Tour tour = new Tour();
        tour.setTitle(title);
        tour.setDescription(edtTourDesc.getText().toString().trim());
        tour.setImageUrl(coverUrl);
        tour.setAuthorId(userId);
        tour.setStatus(spnStatus.getSelectedItem().toString());
        tour.setStartDate(isoFormat.format(startDate));
        tour.setEndDate(isoFormat.format(endDate));

        List<Tour.Waypoint> waypoints = new ArrayList<>();

        for (int i = 0; i < waypointContainer.getChildCount(); i++) {
            waypoints.add(parseWaypoint(waypointContainer.getChildAt(i)));
        }
        tour.setWaypoints(waypoints);

        apiService.createTour(tour).enqueue(new Callback<Tour>() {
            @Override
            public void onResponse(Call<Tour> call, Response<Tour> response) {
                loadingDialog.dismiss();
                if (response.isSuccessful()) {
                    Toast.makeText(CreateTourActivity.this, "Tour Saved Successfully!", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Log.e("SAVE_ERROR", "Error Code: " + response.code());
                    Toast.makeText(CreateTourActivity.this, "Server Error: " + response.code(), Toast.LENGTH_LONG).show();
                    resetSaveButton();
                }
            }
            @Override
            public void onFailure(Call<Tour> call, Throwable t) {
                loadingDialog.dismiss();
                Log.e("SAVE_ERROR", t.getMessage());
                Toast.makeText(CreateTourActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                resetSaveButton();
            }
        });
    }

    private Tour.Waypoint parseWaypoint(View v) {
        TextInputEditText edtWpName = v.findViewById(R.id.edtWpLocationName);
        TextInputEditText edtWpPrice = v.findViewById(R.id.edtWpPrice);
        TextInputEditText edtWpNote = v.findViewById(R.id.edtWpNote);
        TextInputEditText edtLat = v.findViewById(R.id.edtWpLat);
        TextInputEditText edtLng = v.findViewById(R.id.edtWpLng);
        ImageView imgWp = v.findViewById(R.id.imgWpPreview);

        Tour.Waypoint wp = new Tour.Waypoint();
        wp.setLocationName(edtWpName.getText().toString().trim());
        wp.setNote(edtWpNote.getText().toString().trim());
        String pStr = edtWpPrice.getText().toString().trim();
        try {
            wp.setPrice(pStr.isEmpty() ? 0 : Integer.parseInt(pStr));
        } catch (NumberFormatException ignored) {
            wp.setPrice(0);
        }

        if (imgWp.getTag() != null) {
            String wpUrl = (String) imgWp.getTag();
            wp.setPhotos(Arrays.asList(wpUrl));
        }

        try {
            double lat = Double.parseDouble(edtLat.getText().toString());
            double lng = Double.parseDouble(edtLng.getText().toString());
            Tour.Coordinate coord = new Tour.Coordinate();
            coord.setType("Point");
            coord.setCoordinates(GisHelper.swapToGeoJsonCoordinates(lat, lng));
            wp.setCoordinate(coord);
        } catch (Exception e) {}
        return wp;
    }

    private boolean validateWaypoints() {
        if (waypointContainer.getChildCount() == 0) {
            Toast.makeText(this, R.string.error_stop_required, Toast.LENGTH_SHORT).show();
            return false;
        }

        for (int i = 0; i < waypointContainer.getChildCount(); i++) {
            View waypointView = waypointContainer.getChildAt(i);
            TextInputEditText name = waypointView.findViewById(R.id.edtWpLocationName);
            TextInputEditText price = waypointView.findViewById(R.id.edtWpPrice);
            TextInputEditText latitude = waypointView.findViewById(R.id.edtWpLat);
            TextInputEditText longitude = waypointView.findViewById(R.id.edtWpLng);

            if (name.getText() == null || name.getText().toString().trim().isEmpty()) {
                name.setError(getString(R.string.error_location_name_required));
                name.requestFocus();
                return false;
            }
            try {
                String rawPrice = price.getText() == null ? "" : price.getText().toString().trim();
                if (!rawPrice.isEmpty() && Integer.parseInt(rawPrice) < 0) throw new NumberFormatException();
            } catch (NumberFormatException error) {
                price.setError(getString(R.string.error_price_invalid));
                price.requestFocus();
                return false;
            }
            try {
                double lat = Double.parseDouble(latitude.getText().toString().trim());
                double lng = Double.parseDouble(longitude.getText().toString().trim());
                if (!Double.isFinite(lat) || !Double.isFinite(lng)
                        || Math.abs(lat) > 90 || Math.abs(lng) > 180) {
                    throw new NumberFormatException();
                }
            } catch (Exception error) {
                latitude.setError(getString(R.string.error_coordinates_invalid));
                latitude.requestFocus();
                return false;
            }
        }
        return true;
    }

    private void uploadWaypointImage(Uri uri, ImageView targetImageView) {
        loadingDialog.setMessage("Uploading waypoint image...");
        loadingDialog.show();

        new Thread(() -> {
            File file = FileUtils.getFile(this, uri);
            runOnUiThread(() -> {
                if (file == null) {
                    loadingDialog.dismiss();
                    return;
                }

                String mimeType = getContentResolver().getType(uri);
                RequestBody requestFile = RequestBody.create(
                        MediaType.parse(mimeType != null ? mimeType : "image/jpeg"), file);
                MultipartBody.Part body = MultipartBody.Part.createFormData("image", file.getName(), requestFile);

                apiService.uploadImage(body).enqueue(new Callback<ApiService.UploadResponse>() {
                    @Override
                    public void onResponse(Call<ApiService.UploadResponse> call, Response<ApiService.UploadResponse> response) {
                        loadingDialog.dismiss();
                        if (response.isSuccessful() && response.body() != null) {
                            targetImageView.setTag(response.body().getImageUrl());
                            Toast.makeText(CreateTourActivity.this, "Image uploaded!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(CreateTourActivity.this, "Upload failed: " + response.code(), Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(Call<ApiService.UploadResponse> call, Throwable t) {
                        loadingDialog.dismiss();
                        Log.e("UPLOAD_WP_ERROR", t.getMessage());
                    }
                });
            });
        }).start();
    }

    private void addWaypointField() {
        View v = getLayoutInflater().inflate(R.layout.item_waypoint, waypointContainer, false);
        ImageView imgWpPreview = v.findViewById(R.id.imgWpPreview);
        Button btnSelectImg = v.findViewById(R.id.btnSelectImg);
        ((TextView)v.findViewById(R.id.txtWpTitle)).setText("Stop " + (waypointContainer.getChildCount() + 1));

        btnSelectImg.setOnClickListener(view -> {
            isSelectingCover = false;
            currentWpImageView = imgWpPreview;
            openGallery();
        });

        v.findViewById(R.id.btnGetLocation).setOnClickListener(view -> {
            int index = waypointContainer.indexOfChild(v);
            if (index < 0) {
                index = waypointContainer.getChildCount();
            }
            GisHelper.MockLocation mockLoc = GisHelper.getMockLocationForIndex(index);
            
            ((TextInputEditText) v.findViewById(R.id.edtWpLocationName)).setText(mockLoc.getLocationName());
            ((TextInputEditText) v.findViewById(R.id.edtWpLat)).setText(String.valueOf(mockLoc.getLatitude()));
            ((TextInputEditText) v.findViewById(R.id.edtWpLng)).setText(String.valueOf(mockLoc.getLongitude()));
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
                        uploadWaypointImage(uri, currentWpImageView);
                    }
                }
            }
    );

    private void resetSaveButton() {
        btnSaveAndStart.setEnabled(true);
        String status = spnStatus.getSelectedItem().toString();
        btnSaveAndStart.setText(status.equalsIgnoreCase("Completed") ? "SAVE THE MEMORY" : "START THE JOURNEY");
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
