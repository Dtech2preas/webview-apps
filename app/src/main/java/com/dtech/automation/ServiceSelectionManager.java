package com.dtech.automation;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.List;
import java.util.UUID;

public class ServiceSelectionManager {

    private final Context context;
    private final ServiceRepository repo;
    private final OnServiceSelectedListener listener;
    private final DTechFileManager fileManager;

    public interface OnServiceSelectedListener {
        void onServiceSelected(ServiceRepository.ServiceData service);
        // Optional: Method to request import, handled by the implementer (Activity)
        default void onImportRequested() {}
    }

    public ServiceSelectionManager(Context context, OnServiceSelectedListener listener) {
        this.context = context;
        this.repo = new ServiceRepository(context);
        this.listener = listener;
        this.fileManager = new DTechFileManager(context);
    }

    public void showServiceSelectionDialog() {
        List<ServiceRepository.ServiceData> services = repo.getAllServices();

        android.app.Dialog dialog = new android.app.Dialog(context);
        dialog.setContentView(R.layout.dialog_service_list);
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        ListView listView = dialog.findViewById(R.id.list_services);
        View btnAdd = dialog.findViewById(R.id.btn_add_service);
        View btnImport = dialog.findViewById(R.id.btn_import_service);

        ArrayAdapter<ServiceRepository.ServiceData> adapter = new ArrayAdapter<ServiceRepository.ServiceData>(context, android.R.layout.simple_list_item_1, services) {
             @Override
             public View getView(int position, View convertView, ViewGroup parent) {
                 View v = super.getView(position, convertView, parent);
                 TextView tv = (TextView) v.findViewById(android.R.id.text1);
                 tv.setTextColor(Color.WHITE);
                 tv.setTextSize(16);
                 tv.setPadding(0, 20, 0, 20);
                 return v;
             }
        };
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, v, position, id) -> {
            ServiceRepository.ServiceData selected = services.get(position);
            repo.setLastUsedServiceId(selected.getId());
            listener.onServiceSelected(selected);
            dialog.dismiss();
        });

        // Long click for Options
        listView.setOnItemLongClickListener((parent, v, position, id) -> {
            ServiceRepository.ServiceData selected = services.get(position);
            String[] options = {"Delete", "Export .dtech", "Edit Settings", "Cancel"};
            new AlertDialog.Builder(context)
                .setTitle(selected.getName())
                .setItems(options, (d, w) -> {
                     if (w == 0) { // Delete
                         new AlertDialog.Builder(context)
                            .setTitle("Confirm Delete")
                            .setMessage("Are you sure?")
                            .setPositiveButton("Yes", (d2, w2) -> {
                                repo.deleteService(selected.getId());
                                dialog.dismiss();
                                showServiceSelectionDialog(); // Refresh
                            })
                            .setNegativeButton("No", null)
                            .show();
                     } else if (w == 1) { // Export .dtech
                         showExportDialog(selected);
                     } else if (w == 2) { // Edit Settings
                         showEditServiceDialog(selected);
                         dialog.dismiss();
                     }
                })
                .show();
            return true;
        });

        btnAdd.setOnClickListener(v -> {
            dialog.dismiss();
            showAddServiceDialog();
        });

        btnImport.setOnClickListener(v -> {
            // Check if context is capable, or delegate to listener
            if (context instanceof MainActivity) {
                ((MainActivity) context).initiateImport();
                dialog.dismiss();
            } else {
                // If called from Dashboard or other context, try listener delegation
                // or just show a Toast that this feature requires the main view.
                // However, listener might implement it (Dashboard could implement initiateImport logic too)
                // For now, safest is to check MainActivity.
                 Toast.makeText(context, "Please open a Service to import files.", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void showExportDialog(ServiceRepository.ServiceData service) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Export Configuration");

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText etName = new EditText(context);
        etName.setHint("Script Name (e.g. Crunchyroll Auto-Login)");
        etName.setText(service.getName());
        layout.addView(etName);

        final EditText etUrl = new EditText(context);
        etUrl.setHint("Target URL");
        etUrl.setText(service.getLoginUrl());
        layout.addView(etUrl);

        final EditText etDesc = new EditText(context);
        etDesc.setHint("Description (Brief info about what this does)");
        etDesc.setMinLines(2);
        layout.addView(etDesc);

        builder.setView(layout);

        builder.setPositiveButton("Export", (d, w) -> {
            String name = etName.getText().toString().trim();
            String url = etUrl.getText().toString().trim();
            String desc = etDesc.getText().toString().trim();

            if (name.isEmpty()) name = service.getName();
            if (url.isEmpty()) url = service.getLoginUrl();
            if (desc.isEmpty()) desc = "No description.";

            exportServiceDtech(service, name, url, desc);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void exportServiceDtech(ServiceRepository.ServiceData service, String name, String url, String desc) {
        byte[] data = fileManager.generateDTechData(service, name, url, desc);
        if (data != null) {
            String filename = service.getName().replaceAll("[^a-zA-Z0-9]", "_") + DTechFileManager.EXTENSION;
            fileManager.saveDTechToDownloads(filename, data);
        } else {
            Toast.makeText(context, "Export Failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void showEditServiceDialog(ServiceRepository.ServiceData service) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Edit Service Settings");

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText etName = new EditText(context);
        etName.setHint("Service Name");
        etName.setText(service.getName());
        layout.addView(etName);

        final EditText etUA = new EditText(context);
        etUA.setHint("User Agent (Optional)");
        etUA.setText(service.getUserAgent());
        layout.addView(etUA);

        builder.setView(layout);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String ua = etUA.getText().toString().trim();

            if (!name.isEmpty()) {
                service.setName(name);
            }
            service.setUserAgent(ua);

            repo.addOrUpdateService(service);
            listener.onServiceSelected(service);
            Toast.makeText(context, "Settings Saved", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    public void showAddServiceDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Add New Service");

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText etName = new EditText(context);
        etName.setHint("Service Name (e.g. Facebook)");
        layout.addView(etName);

        final EditText etUrl = new EditText(context);
        etUrl.setHint("Login URL (e.g. https://...)");
        etUrl.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        layout.addView(etUrl);

        builder.setView(layout);

        builder.setPositiveButton("Create", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String url = etUrl.getText().toString().trim();

            if (name.isEmpty() || url.isEmpty()) {
                Toast.makeText(context, "Name and URL required", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!url.startsWith("http")) {
                url = "https://" + url;
            }

            String id = UUID.randomUUID().toString();
            ServiceRepository.ServiceData newService = new ServiceRepository.ServiceData(id, name, url);
            repo.addOrUpdateService(newService);
            repo.setLastUsedServiceId(id);

            listener.onServiceSelected(newService);
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
}
