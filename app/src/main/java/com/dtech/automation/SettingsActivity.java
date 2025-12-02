package com.dtech.automation;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    private EditText etCredentials;
    private Button btnSave, btnAdmin;

    public static final String PREFS_NAME = "AutomationPrefs";
    public static final String KEY_CREDENTIALS = "saved_credentials";
    public static final String KEY_IS_ADMIN = "is_admin";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        etCredentials = findViewById(R.id.et_credentials);
        btnSave = findViewById(R.id.btn_save);
        btnAdmin = findViewById(R.id.btn_admin_login);

        loadCredentials();

        btnSave.setOnClickListener(v -> saveCredentials());
        btnAdmin.setOnClickListener(v -> showAdminLogin());
    }

    private void loadCredentials() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String saved = prefs.getString(KEY_CREDENTIALS, "");
        etCredentials.setText(saved);

        boolean isAdmin = prefs.getBoolean(KEY_IS_ADMIN, false);
        if (isAdmin) {
            btnAdmin.setText("Admin Logged In");
            btnAdmin.setEnabled(false);
        }
    }

    private void showAdminLogin() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Admin Login");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);

        final EditText inputUser = new EditText(this);
        inputUser.setHint("Username");
        layout.addView(inputUser);

        final EditText inputPass = new EditText(this);
        inputPass.setHint("Password");
        inputPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(inputPass);

        builder.setView(layout);

        builder.setPositiveButton("Login", (dialog, which) -> {
            String u = inputUser.getText().toString();
            String p = inputPass.getText().toString();
            if ("admin".equals(u) && "preasx24".equals(p)) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_IS_ADMIN, true).apply();
                Toast.makeText(this, "Admin Access Granted", Toast.LENGTH_SHORT).show();
                btnAdmin.setText("Admin Logged In");
                btnAdmin.setEnabled(false);
            } else {
                Toast.makeText(this, "Invalid Credentials", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
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
