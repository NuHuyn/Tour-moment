package com.example.mycurrenttour;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FileUtils {

    public static File getFile(Context context, Uri uri) {

        File destinationFile = new File(context.getCacheDir(), "temp_image_" + System.currentTimeMillis() + ".jpg");

        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             OutputStream outputStream = new FileOutputStream(destinationFile)) {

            if (inputStream == null) return null;

            byte[] buffer = new byte[4096];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.flush();
            return destinationFile;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}