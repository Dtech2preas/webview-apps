package com.dtech.automation;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Random;

public class UnlockActivity extends AppCompatActivity {

    private TextView tvTerminalLog;
    private ProgressBar progressBar;
    private ImageView ivLockIcon;
    private TextView tvLockStatus;
    private Button btnUnlockAction;

    private String scriptName = "Unknown";
    private String scriptUrl = "Unknown";
    private String scriptDesc = "No description.";

    private boolean isAdClicked = false;
    private boolean isUnlocked = false;
    private Handler handler = new Handler(Looper.getMainLooper());

    private static final String[] AD_URLS = {
        "https://otieu.com/4/10358600",
        "https://otieu.com/4/10205357",
        "https://otieu.com/4/9515888",
        "https://otieu.com/4/10250311"
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_unlock);

        // Hide ActionBar if present
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        // Get Metadata
        Intent intent = getIntent();
        if (intent != null) {
            scriptName = intent.getStringExtra("META_NAME");
            scriptUrl = intent.getStringExtra("META_URL");
            scriptDesc = intent.getStringExtra("META_DESC");
        }
        if (scriptName == null) scriptName = "Encrypted Script";

        initViews();
        startFakeLoadingSequence();
    }

    private void initViews() {
        tvTerminalLog = findViewById(R.id.tv_terminal_log);
        progressBar = findViewById(R.id.progress_unlock);
        ivLockIcon = findViewById(R.id.iv_lock_icon);
        tvLockStatus = findViewById(R.id.tv_lock_status);
        btnUnlockAction = findViewById(R.id.btn_unlock_action);

        btnUnlockAction.setOnClickListener(v -> {
            if (!isUnlocked) {
                openAd();
            } else {
                finishWithSuccess();
            }
        });
    }

    private void startFakeLoadingSequence() {
        appendLog("> System Initialized...");

        // Timed Logs
        handler.postDelayed(() -> safeAppendLog("> Reading Header: " + scriptName), 500);
        handler.postDelayed(() -> safeAppendLog("> Target: " + scriptUrl), 1200);
        handler.postDelayed(() -> safeAppendLog("> Description: " + (scriptDesc.length() > 20 ? scriptDesc.substring(0, 20) + "..." : scriptDesc)), 1900);
        handler.postDelayed(() -> safeAppendLog("> Verifying Integrity..."), 2600);

        // Progress Animation (approx 3 seconds total)
        new Thread(() -> {
            for (int i = 0; i <= 100; i++) {
                if (isFinishing() || isDestroyed()) return;
                try { Thread.sleep(30); } catch (InterruptedException e) {}
                final int p = i;
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) progressBar.setProgress(p);
                });
            }

            // Wait a moment for last log
            try { Thread.sleep(200); } catch (InterruptedException e) {}

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                safeAppendLog("> Integrity Check [OK]");
                safeAppendLog("> Content Encrypted. Authentication Required.");
                showLockedState();
            });
        }).start();
    }

    private void safeAppendLog(String text) {
        if (!isFinishing() && !isDestroyed()) {
            appendLog(text);
        }
    }

    private void appendLog(String text) {
        String current = tvTerminalLog.getText().toString();
        tvTerminalLog.setText(current + "\n" + text);
    }

    private void showLockedState() {
        ivLockIcon.setVisibility(View.VISIBLE);
        tvLockStatus.setVisibility(View.VISIBLE);
        btnUnlockAction.setVisibility(View.VISIBLE);
        btnUnlockAction.setText("UNLOCK CONFIGURATION");
        btnUnlockAction.setBackgroundColor(Color.parseColor("#FF1744")); // Red
        progressBar.setVisibility(View.INVISIBLE);
    }

    private void openAd() {
        String url = AD_URLS[new Random().nextInt(AD_URLS.length)];
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        try {
            startActivity(browserIntent);
            isAdClicked = true;
        } catch (Exception e) {
            Toast.makeText(this, "Could not open browser.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isAdClicked && !isUnlocked) {
            transitionToUnlocked();
        }
    }

    private void transitionToUnlocked() {
        isUnlocked = true;

        ivLockIcon.setVisibility(View.GONE);

        tvLockStatus.setText("ACCESS GRANTED");
        tvLockStatus.setTextColor(Color.parseColor("#00E676")); // Green

        btnUnlockAction.setText("INITIALIZE ENGINE");
        btnUnlockAction.setBackgroundColor(Color.parseColor("#00E676")); // Green

        appendLog("> Access Granted.");
        appendLog("> Ready to Initialize.");
    }

    private void finishWithSuccess() {
        setResult(RESULT_OK);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
