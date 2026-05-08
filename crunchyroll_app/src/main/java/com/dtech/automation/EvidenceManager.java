package com.dtech.automation;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class EvidenceManager {

    private static final String TAG = "EvidenceManager";
    private static final String FOLDER_NAME = "DTech_Evidence";
    private final Context context;

    public EvidenceManager(Context context) {
        this.context = context;
    }

    public void captureEvidence(Bitmap bitmap, String serviceName, String accountEmail) {
        if (bitmap == null) return;

        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String safeService = serviceName.replaceAll("[^a-zA-Z0-9]", "_");
            String safeEmail = accountEmail.split("@")[0].replaceAll("[^a-zA-Z0-9]", "_");
            String fileName = "EVIDENCE_" + safeService + "_" + safeEmail + "_" + timeStamp + ".jpg";

            OutputStream fos;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ (Scoped Storage)
                ContentResolver resolver = context.getContentResolver();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + FOLDER_NAME);

                Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
                if (imageUri == null) {
                    Log.e(TAG, "Failed to create MediaStore entry");
                    return;
                }
                fos = resolver.openOutputStream(imageUri);
            } else {
                // Legacy (Pre-Android 10)
                File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                File dtechDir = new File(picturesDir, FOLDER_NAME);
                if (!dtechDir.exists() && !dtechDir.mkdirs()) {
                    Log.e(TAG, "Failed to create evidence directory");
                    return;
                }
                File file = new File(dtechDir, fileName);
                fos = new FileOutputStream(file);

                // Trigger media scan for legacy file
                android.media.MediaScannerConnection.scanFile(context,
                        new String[]{file.toString()}, null, null);
            }

            if (fos != null) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
                fos.close();
                Log.d(TAG, "Evidence Saved Successfully");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error saving evidence", e);
        }
    }
}
