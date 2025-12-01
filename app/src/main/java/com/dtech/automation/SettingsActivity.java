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

    public static final String PREFS_NAME = "AutomationPrefs";
    public static final String KEY_CREDENTIALS = "saved_credentials";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        etCredentials = findViewById(R.id.et_credentials);
        btnSave = findViewById(R.id.btn_save);

        loadCredentials();

        btnSave.setOnClickListener(v -> saveCredentials());
    }

    private void loadCredentials() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String saved = prefs.getString(KEY_CREDENTIALS, "");
        etCredentials.setText(saved);
    }

    private void saveCredentials() {
        String input = etCredentials.getText().toString();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_CREDENTIALS, input);
        editor.apply();

        Toast.makeText(this, "Credentials Saved", Toast.LENGTH_SHORT).show();
        finish();
    }
}
