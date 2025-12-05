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

        // Long click to delete
        listView.setOnItemLongClickListener((parent, v, position, id) -> {
            ServiceRepository.ServiceData selected = services.get(position);
            new AlertDialog.Builder(context)
                .setTitle("Delete " + selected.getName() + "?")
                .setPositiveButton("Delete", (d, w) -> {
                    repo.deleteService(selected.getId());
                    dialog.dismiss();
                    showServiceSelectionDialog(); // Refresh
                })
                .setNegativeButton("Cancel", null)
                .show();
            return true;
        });

        btnAdd.setOnClickListener(v -> {
            dialog.dismiss();
            showAddServiceDialog();
        });

        dialog.show();
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
