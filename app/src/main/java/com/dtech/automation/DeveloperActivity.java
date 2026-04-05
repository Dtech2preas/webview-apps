package com.dtech.automation;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.LinearLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

public class DeveloperActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_developer);

        Button btnRecord = findViewById(R.id.btn_dev_record);
        Button btnExportJson = findViewById(R.id.btn_dev_export_json);

        btnRecord.setOnClickListener(v -> {
            // How do we trigger record? Usually it's in MainActivity.
            // We can open MainActivity with an intent extra that triggers the recording flow,
            // or just open MainActivity and let the user use the record buttons.
            // Wait, we hid the record buttons in MainActivity's UI.
            Intent intent = new Intent(DeveloperActivity.this, MainActivity.class);
            intent.putExtra("START_RECORDING_MODE", true);
            startActivity(intent);
        });

        btnExportJson.setOnClickListener(v -> {
            showPasswordPrompt();
        });
    }

    private void showPasswordPrompt() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Admin Password Required");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        builder.setView(input);

        builder.setPositiveButton("Verify", (dialog, which) -> {
            String password = input.getText().toString();
            if ("dtechx24".equals(password)) {
                showServiceSelectionForExport();
            } else {
                Toast.makeText(this, "Invalid Password", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void showServiceSelectionForExport() {
        ServiceRepository repo = new ServiceRepository(this);
        List<ServiceRepository.ServiceData> services = repo.getAllServices();

        if (services.isEmpty()) {
            Toast.makeText(this, "No services available to export.", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] names = new String[services.size()];
        for (int i = 0; i < services.size(); i++) {
            names[i] = services.get(i).getName();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Service to Export");
        builder.setItems(names, (dialog, which) -> {
            ServiceRepository.ServiceData selected = services.get(which);
            exportRawJson(selected);
        });
        builder.show();
    }

    private void exportRawJson(ServiceRepository.ServiceData service) {
        try {
            String json = service.toJson().toString(4);
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Raw JSON", json);
            clipboard.setPrimaryClip(clip);

            Toast.makeText(this, "Copied raw JSON to clipboard", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
