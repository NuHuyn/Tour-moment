package com.example.mycurrenttour;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class FileUtils {
    public static File getFile(Context context, Uri uri) {
        File destinationFile = new File(context.getCacheDir(), "temp_image_" + System.currentTimeMillis() + ".jpg");
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {

            // 1. Chỉ đọc thông tin kích thước (không nạp cả ảnh vào RAM)
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(inputStream, null, options);

            // 2. Tính toán tỷ lệ giảm kích thước (InSampleSize)
            // Nếu ảnh quá lớn (ví dụ > 1000px), ta giảm xuống để xử lý cho nhanh
            int reqWidth = 1024;
            int reqHeight = 1024;
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
            options.inJustDecodeBounds = false;

            // 3. Đọc lại ảnh đã được thu nhỏ
            InputStream is = context.getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);

            // 4. Nén và ghi ra file
            FileOutputStream out = new FileOutputStream(destinationFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out);
            out.flush();
            out.close();

            return destinationFile;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Hàm phụ tính toán tỷ lệ nén để không gây tốn RAM
    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
}