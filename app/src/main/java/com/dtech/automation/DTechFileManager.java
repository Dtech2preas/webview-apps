package com.dtech.automation;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class DTechFileManager {

    private static final String TAG = "DTechFileManager";
    private static final byte[] XOR_KEY = "DTECH_SECURE_KEY_2024".getBytes(StandardCharsets.UTF_8);
    public static final String EXTENSION = ".dtech";

    private final Context context;

    public DTechFileManager(Context context) {
        this.context = context;
    }

    /**
     * Exports a service to a .dtech file in the app's cache (to be shared).
     * Returns the File object if successful.
     */
    public File exportServiceToFile(ServiceRepository.ServiceData service) {
        try {
            String json = service.toJson().toString();
            byte[] data = json.getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = xorProcess(data);

            // Create file
            String filename = service.getName().replaceAll("[^a-zA-Z0-9]", "_") + EXTENSION;
            File file = new File(context.getExternalCacheDir(), filename); // Use external cache for sharing

            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(encrypted);
            }
            return file;

        } catch (Exception e) {
            Log.e(TAG, "Export failed", e);
            return null;
        }
    }

    /**
     * Imports a service from a URI (content:// or file://).
     * Returns the ServiceData object or null.
     */
    public ServiceRepository.ServiceData importServiceFromUri(Uri uri) {
        try {
            InputStream is = context.getContentResolver().openInputStream(uri);
            if (is == null) return null;

            byte[] encrypted = readAllBytes(is);
            byte[] decrypted = xorProcess(encrypted);
            String json = new String(decrypted, StandardCharsets.UTF_8);

            return ServiceRepository.ServiceData.fromJson(new JSONObject(json));

        } catch (Exception e) {
            Log.e(TAG, "Import failed", e);
            return null;
        }
    }

    private byte[] xorProcess(byte[] input) {
        byte[] output = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = (byte) (input[i] ^ XOR_KEY[i % XOR_KEY.length]);
        }
        return output;
    }

    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }
}
