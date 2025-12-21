package com.dtech.automation;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
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
import java.util.Arrays;

public class DTechFileManager {

    private static final String TAG = "DTechFileManager";
    private static final byte[] XOR_KEY = "DTECH_SECURE_KEY_2024".getBytes(StandardCharsets.UTF_8);
    public static final String EXTENSION = ".dtech";
    private static final String SEPARATOR = "--------------------------------------------------";
    private static final String META_PREFIX = "#META_";

    private final Context context;

    public DTechFileManager(Context context) {
        this.context = context;
    }

    /**
     * Generates the binary content for a .dtech file with metadata header.
     */
    public byte[] generateDTechData(ServiceRepository.ServiceData service, String metaName, String metaUrl, String metaDesc) {
        try {
            String json = service.toJson().toString();
            byte[] data = json.getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = xorProcess(data);

            // Construct Header
            StringBuilder header = new StringBuilder();
            header.append("#META_NAME: ").append(metaName).append("\n");
            header.append("#META_URL: ").append(metaUrl).append("\n");
            header.append("#META_DESC: ").append(metaDesc).append("\n");
            header.append("#META_DATE: ").append(new java.util.Date().toString()).append("\n");
            header.append(SEPARATOR).append("\n");

            byte[] headerBytes = header.toString().getBytes(StandardCharsets.UTF_8);

            // Combine
            byte[] combined = new byte[headerBytes.length + encrypted.length];
            System.arraycopy(headerBytes, 0, combined, 0, headerBytes.length);
            System.arraycopy(encrypted, 0, combined, headerBytes.length, encrypted.length);

            return combined;

        } catch (Exception e) {
            Log.e(TAG, "Data generation failed", e);
            return null;
        }
    }

    public void saveDTechToDownloads(String fileName, byte[] content) {
        try {
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream");
            values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/DTech_Configs");

            android.net.Uri uri = context.getContentResolver().insert(android.provider.MediaStore.Files.getContentUri("external"), values);

            if (uri != null) {
                try (java.io.OutputStream os = context.getContentResolver().openOutputStream(uri)) {
                    os.write(content);
                }
                android.widget.Toast.makeText(context, "Saved to Downloads/DTech_Configs", android.widget.Toast.LENGTH_LONG).show();
            }
        } catch (java.io.IOException e) {
            android.util.Log.e("DTECH_EXPORT", "Error", e);
            android.widget.Toast.makeText(context, "Export Failed: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Peeks at the file to see if it has metadata.
     * Returns Bundle with keys META_NAME, META_URL, META_DESC if found, else null.
     */
    public Bundle peekMetadata(Uri uri) {
        try {
            InputStream is = context.getContentResolver().openInputStream(uri);
            if (is == null) return null;

            // Read first KB to check header
            byte[] buffer = new byte[2048];
            int read = is.read(buffer);
            is.close();

            if (read <= 0) return null;

            String content = new String(buffer, 0, read, StandardCharsets.UTF_8);
            if (!content.startsWith(META_PREFIX)) return null;

            if (!content.contains(SEPARATOR)) return null; // Incomplete header

            Bundle bundle = new Bundle();
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (line.trim().equals(SEPARATOR)) break;
                if (line.startsWith("#META_NAME: ")) bundle.putString("META_NAME", line.substring(12).trim());
                if (line.startsWith("#META_URL: ")) bundle.putString("META_URL", line.substring(11).trim());
                if (line.startsWith("#META_DESC: ")) bundle.putString("META_DESC", line.substring(12).trim());
            }
            return bundle;

        } catch (Exception e) {
            Log.e(TAG, "Peek failed", e);
            return null;
        }
    }

    /**
     * Imports a service from a URI (content:// or file://).
     * Handles both raw binary and metadata-prepended files.
     */
    public ServiceRepository.ServiceData importServiceFromUri(Uri uri) {
        try {
            InputStream is = context.getContentResolver().openInputStream(uri);
            if (is == null) return null;

            byte[] allBytes = readAllBytes(is);
            is.close();

            byte[] payload = allBytes;

            // Check for header
            String prefix = new String(allBytes, 0, Math.min(allBytes.length, 50), StandardCharsets.UTF_8);
            if (prefix.startsWith(META_PREFIX)) {
                // Find separator (just the dash line, without forcing \n yet)
                byte[] sepBytes = SEPARATOR.getBytes(StandardCharsets.UTF_8);
                int splitIndex = indexOf(allBytes, sepBytes);

                if (splitIndex != -1) {
                    int payloadStart = splitIndex + sepBytes.length;

                    // Skip any newline characters (\r or \n) to find start of encrypted data
                    while (payloadStart < allBytes.length &&
                            (allBytes[payloadStart] == 10 || allBytes[payloadStart] == 13)) {
                        payloadStart++;
                    }

                    if (payloadStart < allBytes.length) {
                        payload = Arrays.copyOfRange(allBytes, payloadStart, allBytes.length);
                    }
                }
            }

            byte[] decrypted = xorProcess(payload);
            String json = new String(decrypted, StandardCharsets.UTF_8);

            return ServiceRepository.ServiceData.fromJson(new JSONObject(json));

        } catch (Exception e) {
            Log.e(TAG, "Import failed", e);
            return null;
        }
    }

    // Helper to find byte pattern
    private int indexOf(byte[] data, byte[] pattern) {
        for (int i = 0; i < data.length - pattern.length + 1; i++) {
            boolean found = true;
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    found = false;
                    break;
                }
            }
            if (found) return i;
        }
        return -1;
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
