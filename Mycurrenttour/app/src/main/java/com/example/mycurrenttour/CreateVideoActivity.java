package com.example.mycurrenttour;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class CreateVideoActivity extends AppCompatActivity {

    private Button btnSelectMedia, btnStartProcess;
    private List<Uri> selectedUris = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_video);

        btnSelectMedia = findViewById(R.id.btnSelectMedia);
        btnStartProcess = findViewById(R.id.btnStartProcess);


        ActivityResultLauncher<PickVisualMediaRequest> pickMultipleMedia =
                registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(10), uris -> {
                    if (uris != null && !uris.isEmpty()) {
                        selectedUris.clear();
                        selectedUris.addAll(uris);


                        btnSelectMedia.setText("Selected " + uris.size() + " photos");
                        Toast.makeText(this, "Selected " + uris.size() + " image", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show();
                    }
                });

        btnSelectMedia.setOnClickListener(v -> {
            pickMultipleMedia.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });

        btnStartProcess.setOnClickListener(v -> {
            if (selectedUris.size() < 2) {
                Toast.makeText(this, "Please select at least 2 images to create a tour video!", Toast.LENGTH_SHORT).show();
                return;
            }
            processVideo();
        });
    }

    private void processVideo() {
        btnStartProcess.setEnabled(false);
        btnStartProcess.setText("Processing...");


        new Handler().postDelayed(() -> {
            ArrayList<String> uriStrings = new ArrayList<>();
            for (Uri u : selectedUris) {

                grantUriPermission(getPackageName(), u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                uriStrings.add(u.toString());
            }

            Intent resultIntent = new Intent();

            resultIntent.putStringArrayListExtra("PHOTO_LIST", uriStrings);

            resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            setResult(RESULT_OK, resultIntent);
            Toast.makeText(this, "Tour video created successfully!", Toast.LENGTH_SHORT).show();
            finish();
        }, 1500);
    }
}