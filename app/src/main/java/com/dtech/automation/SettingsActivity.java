package com.dtech.automation;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    private EditText etCredentials;
    private Button btnSave;
    private Switch switchBiometric;
    private Switch switchEvidence;

    public static final String PREFS_NAME = "AutomationPrefs";
    private String currentServiceId;
    private ServiceRepository serviceRepo;
    private SecurityManager securityManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);

        etCredentials = findViewById(R.id.et_credentials);
        btnSave = findViewById(R.id.btn_save);
        switchBiometric = findViewById(R.id.switch_biometric);
        switchEvidence = findViewById(R.id.switch_evidence);

        serviceRepo = new ServiceRepository(this);
        securityManager = new SecurityManager(this);

        currentServiceId = serviceRepo.getLastUsedServiceId();

        // Load Global Security Settings
        switchBiometric.setChecked(securityManager.isBiometricEnabled());
        switchEvidence.setChecked(securityManager.isEvidenceEnabled());

        // Load Service Specific Credentials
        ServiceRepository.ServiceData service = null;
        if (currentServiceId != null) {
            service = serviceRepo.getServiceById(currentServiceId);
        }

        if (service != null) {
             loadCredentials(service.getId());
        } else {
             loadCredentials("global");
        }

        final String targetId = (currentServiceId != null) ? currentServiceId : "global";
        btnSave.setOnClickListener(v -> {
            saveCredentials(targetId);
            securityManager.setBiometricEnabled(switchBiometric.isChecked());
            securityManager.setEvidenceEnabled(switchEvidence.isChecked());
            Toast.makeText(this, "Settings Saved", Toast.LENGTH_SHORT).show();
            finish();
        });
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
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
    }
}
