package com.dtech.automation;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import java.util.List;
import java.util.UUID;

public class ServiceSelectionManager {

    private final Context context;
    private final ServiceRepository repo;
    private final OnServiceSelectedListener listener;

    public interface OnServiceSelectedListener {
        void onServiceSelected(ServiceRepository.ServiceData service);
    }

    public ServiceSelectionManager(Context context, OnServiceSelectedListener listener) {
        this.context = context;
        this.repo = new ServiceRepository(context);
        this.listener = listener;
    }

    public void showServiceSelectionDialog() {
        List<ServiceRepository.ServiceData> services = repo.getAllServices();

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Select Service");

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_service_list, null);
        ListView listView = view.findViewById(R.id.list_services);
        View btnAdd = view.findViewById(R.id.btn_add_service);
        View btnImport = view.findViewById(R.id.btn_import_service);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1);
        for (ServiceRepository.ServiceData s : services) {
            adapter.add(s.getName());
        }
        listView.setAdapter(adapter);

        AlertDialog dialog = builder.setView(view).create();

        listView.setOnItemClickListener((parent, v, position, id) -> {
            ServiceRepository.ServiceData selected = services.get(position);
            repo.setLastUsedServiceId(selected.getId());
            listener.onServiceSelected(selected);
            dialog.dismiss();
        });

        // Long click for Options (Delete, Export, Edit)
        listView.setOnItemLongClickListener((parent, v, position, id) -> {
            ServiceRepository.ServiceData selected = services.get(position);
            String[] options = {"Delete", "Export JSON", "Edit Settings", "Cancel"};
            new AlertDialog.Builder(context)
                .setTitle(selected.getName())
                .setItems(options, (d, w) -> {
                     if (w == 0) {
                         // Delete
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
                     } else if (w == 1) {
                         // Export
                         exportService(selected.getId());
                     } else if (w == 2) {
                         // Edit Settings (UA)
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
            // Since we cannot use startActivityForResult in a non-Activity class easily without passing activity,
            // we will ask the user to paste JSON content OR rely on MainActivity to handle file picking.
            // For simplicity and robustness given the constraints, let's use a "Paste JSON" dialog first,
            // or if context is Activity, use File Picker.
            if (context instanceof android.app.Activity) {
                ((MainActivity) context).initiateImport();
                dialog.dismiss();
            } else {
                showPasteImportDialog();
            }
        });

        dialog.show();
    }

    private void showPasteImportDialog() {
        final EditText input = new EditText(context);
        input.setHint("Paste JSON here...");
        new AlertDialog.Builder(context)
            .setTitle("Import Service (Paste JSON)")
            .setView(input)
            .setPositiveButton("Import", (d, w) -> {
                String json = input.getText().toString().trim();
                if (repo.importService(json)) {
                    Toast.makeText(context, "Import Successful!", Toast.LENGTH_SHORT).show();
                    showServiceSelectionDialog();
                } else {
                    Toast.makeText(context, "Invalid JSON", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void exportService(String id) {
        String json = repo.exportService(id);
        if (json != null) {
            // Save to file in public directory or share intent
            // Since we are in an Activity usually, let's use Share Intent for text
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(android.content.Intent.EXTRA_TEXT, json);
            context.startActivity(android.content.Intent.createChooser(i, "Export Service Config"));
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
