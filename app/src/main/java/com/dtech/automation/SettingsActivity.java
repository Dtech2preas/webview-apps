package com.dtech.automation;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    private EditText etCredentials;
    private Button btnSave;

    // We still use AutomationPrefs, but keys will be dynamic based on ServiceID
    public static final String PREFS_NAME = "AutomationPrefs";

    // Legacy key, kept just in case, but we will use service-specific keys
    // public static final String KEY_CREDENTIALS = "saved_credentials";

    private String currentServiceId;
    private ServiceRepository serviceRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        etCredentials = findViewById(R.id.et_credentials);
        btnSave = findViewById(R.id.btn_save);

        serviceRepo = new ServiceRepository(this);
        currentServiceId = serviceRepo.getLastUsedServiceId();

        ServiceRepository.ServiceData service = null;
        if (currentServiceId != null) {
            service = serviceRepo.getServiceById(currentServiceId);
        }

        if (service != null) {
             setTitle("Settings: " + service.getName());
             loadCredentials(service.getId());
        } else {
             setTitle("Settings (No Service Selected)");
             // Fallback to global if no service selected (shouldn't happen in new flow)
             loadCredentials("global");
        }

        final String targetId = (currentServiceId != null) ? currentServiceId : "global";
        btnSave.setOnClickListener(v -> saveCredentials(targetId));
    }

    private void loadCredentials(String serviceId) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String saved = prefs.getString("creds_" + serviceId, "");
        etCredentials.setText(saved);
    }

    private void saveCredentials(String serviceId) {
        String input = etCredentials.getText().toString();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("creds_" + serviceId, input);
        editor.apply();

        Toast.makeText(this, "Credentials Saved", Toast.LENGTH_SHORT).show();
        finish();
    }
}
