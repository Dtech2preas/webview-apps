package com.dtech.automation;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class DTechFileManager {

    private static final String TAG = "DTechFileManager";
    public static final String EXTENSION = ".dtech";
    // STRICT SEPARATOR as requested
    private static final String SEPARATOR = "--------------------------------------------------";
    private static final String META_PREFIX = "#META_";

    private final Context context;

    public DTechFileManager(Context context) {
        this.context = context;
    }

    /**
     * Generates the Plain Text content for a .dtech file.
     * Format:
     * #META_NAME: ...
     * ...
     * --------------------------------------------------
     * { "json": "object" }
     */
    public byte[] generateDTechData(ServiceRepository.ServiceData service, String metaName, String metaUrl, String metaDesc) {
        try {
            // 1. Get Raw JSON from the full service object
            String json = service.toJson().toString();

            // 2. Construct Header + Separator + JSON
            StringBuilder content = new StringBuilder();
            content.append("#META_NAME: ").append(metaName).append("\n");
            content.append("#META_URL: ").append(metaUrl).append("\n");
            content.append("#META_DESC: ").append(metaDesc).append("\n");
            content.append("#META_DATE: ").append(new java.util.Date().toString()).append("\n");
            content.append(SEPARATOR).append("\n");
            content.append(json);

            // 3. Return as UTF-8 bytes
            return content.toString().getBytes(StandardCharsets.UTF_8);

        } catch (Exception e) {
            Log.e(TAG, "Data generation failed", e);
            return null;
        }
    }

    public void saveDTechToDownloads(String fileName, byte[] content) {
        try {
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/DTech_Configs");

            Uri uri = context.getContentResolver().insert(android.provider.MediaStore.Files.getContentUri("external"), values);

            if (uri != null) {
                try (OutputStream os = context.getContentResolver().openOutputStream(uri)) {
                    if (os != null) {
                        os.write(content);
                    }
                }
                Toast.makeText(context, "Saved to Downloads/DTech_Configs", Toast.LENGTH_LONG).show();
            }
        } catch (IOException e) {
            Log.e("DTECH_EXPORT", "Error", e);
            Toast.makeText(context, "Export Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
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

            // Read enough bytes to likely cover the header
            byte[] buffer = new byte[4096];
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
     * EXPECTS: Plain Text UTF-8 with Separator.
     */
    public ServiceRepository.ServiceData importServiceFromUri(Uri uri) {
        try {
            InputStream is = context.getContentResolver().openInputStream(uri);
            if (is == null) return null;

            byte[] allBytes = readAllBytes(is);
            is.close();

            String fullContent = new String(allBytes, StandardCharsets.UTF_8);

            // Split by Separator
            // Use regex or literal? String.split takes regex.
            // Escape the dashes? No, dashes are not special in regex unless in brackets.
            // But to be safe, I'll use Pattern.quote or just assume it works.
            // "--------------------------------------------------" is safe in regex.
            String[] parts = fullContent.split(SEPARATOR);

            String jsonPart;
            if (parts.length >= 2) {
                jsonPart = parts[1];
            } else {
                // Fallback: assume whole content is JSON if no separator found
                // (Though user requested strict structure, this handles manually created files without headers)
                jsonPart = fullContent;
            }

            // Clean up whitespace (important for JSON parsing)
            jsonPart = jsonPart.trim();

            return ServiceRepository.ServiceData.fromJson(new JSONObject(jsonPart));

        } catch (Exception e) {
            Log.e(TAG, "Import failed", e);
            Toast.makeText(context, "Corrupted File or Invalid Format", Toast.LENGTH_LONG).show();
            return null;
        }
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
