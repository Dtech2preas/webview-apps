package com.dtech.automation;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

public class SecurityManager {

    private static final String PREFS_NAME = "SecurityPrefs";
    private static final String KEY_BIO_ENABLED = "biometric_enabled";
    private static final String KEY_EVIDENCE_ENABLED = "evidence_enabled";

    private final Context context;
    private final SharedPreferences prefs;

    public SecurityManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isBiometricEnabled() {
        return prefs.getBoolean(KEY_BIO_ENABLED, false);
    }

    public void setBiometricEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_BIO_ENABLED, enabled).apply();
    }

    public boolean isEvidenceEnabled() {
        return prefs.getBoolean(KEY_EVIDENCE_ENABLED, false);
    }

    public void setEvidenceEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_EVIDENCE_ENABLED, enabled).apply();
    }

    public void authenticate(FragmentActivity activity, Runnable onSuccess, Runnable onFailure) {
        Executor executor = ContextCompat.getMainExecutor(activity);
        BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                if (onFailure != null) onFailure.run();
            }

            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                if (onSuccess != null) onSuccess.run();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                // Let user try again, don't trigger failure immediately
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("DTECH Security")
                .setSubtitle("Confirm identity to access automation")
                .setNegativeButtonText("Exit")
                .build();

        biometricPrompt.authenticate(promptInfo);
    }
}
