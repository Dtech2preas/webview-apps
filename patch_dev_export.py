import re

file_path = "app/src/main/java/com/dtech/automation/DeveloperActivity.java"
with open(file_path, "r") as f:
    content = f.read()

import_statements = """
import android.app.Activity;
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
"""

content = re.sub(r'import android.app.Activity;[\s\S]*?import android.widget.Button;', import_statements.strip(), content)


export_logic = """
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
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File exportFile = new File(downloadsDir, service.getName().replaceAll("[^a-zA-Z0-9_-]", "") + "_raw.json");

            FileOutputStream fos = new FileOutputStream(exportFile);
            fos.write(json.getBytes());
            fos.close();

            Toast.makeText(this, "Exported to Downloads: " + exportFile.getName(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
"""

content = re.sub(r'btnExportJson.setOnClickListener\(v -> \{[\s\S]*?\}\);[\s\S]*?\}', export_logic.strip(), content)

with open(file_path, "w") as f:
    f.write(content)
