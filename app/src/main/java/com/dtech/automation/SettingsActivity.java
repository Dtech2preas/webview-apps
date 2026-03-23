package com.dtech.automation;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class SettingsActivity extends Activity {

    private EditText etCredentials;
    private Button btnSave, btnImportList;
    private Switch switchBiometric;
    private Switch switchEvidence;
    private EditText etRedirectWaitTime;

    public static final String PREFS_NAME = "AutomationPrefs";
    public static final String KEY_REDIRECT_WAIT_TIME = "redirect_wait_time";
    private String currentServiceId;
    private ServiceRepository serviceRepo;
    private SecurityManager securityManager;

    private static final int REQUEST_CODE_IMPORT_TXT = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);

        etCredentials = findViewById(R.id.et_credentials);
        btnSave = findViewById(R.id.btn_save);
        btnImportList = findViewById(R.id.btn_import_list);
        switchBiometric = findViewById(R.id.switch_biometric);
        switchEvidence = findViewById(R.id.switch_evidence);
        etRedirectWaitTime = findViewById(R.id.et_redirect_wait_time);

        serviceRepo = new ServiceRepository(this);
        securityManager = new SecurityManager(this);

        currentServiceId = serviceRepo.getLastUsedServiceId();

        // Load Global Security Settings
        switchBiometric.setChecked(securityManager.isBiometricEnabled());
        switchEvidence.setChecked(securityManager.isEvidenceEnabled());

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int waitTime = prefs.getInt(KEY_REDIRECT_WAIT_TIME, 20);
        etRedirectWaitTime.setText(String.valueOf(waitTime));

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

            try {
                int newWaitTime = Integer.parseInt(etRedirectWaitTime.getText().toString().trim());
                if (newWaitTime < 0) newWaitTime = 20;
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt(KEY_REDIRECT_WAIT_TIME, newWaitTime).apply();
            } catch (NumberFormatException e) {
                // Keep default if invalid
            }

            Toast.makeText(this, "Settings Saved", Toast.LENGTH_SHORT).show();
            finish();
        });

        btnImportList.setOnClickListener(v -> initiateImport());
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

    private void initiateImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        startActivityForResult(intent, REQUEST_CODE_IMPORT_TXT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_IMPORT_TXT && resultCode == Activity.RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri uri = data.getData();
                try {
                    InputStream is = getContentResolver().openInputStream(uri);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // Basic validation or filtering if needed
                        if (line.trim().length() > 0) {
                            sb.append(line.trim()).append("\n");
                        }
                    }
                    reader.close();

                    String existing = etCredentials.getText().toString();
                    if (existing.length() > 0 && !existing.endsWith("\n")) existing += "\n";
                    etCredentials.setText(existing + sb.toString());

                    Toast.makeText(this, "Imported!", Toast.LENGTH_SHORT).show();
                } catch(Exception e) {
                    Toast.makeText(this, "Import Failed", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
    }
}
