package com.dtech.automation;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class AdSystemActivity extends Activity {

    private TextView tvTimer;
    private Button btnWatchAd;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private long manualAdStartTime = 0;
    private boolean isWaitingForAdReturn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ad_system);

        Button btnBack = findViewById(R.id.btn_back_ad);
        btnBack.setOnClickListener(v -> finish());

        tvTimer = findViewById(R.id.tv_timer_countdown);
        btnWatchAd = findViewById(R.id.btn_watch_ad);

        btnWatchAd.setOnClickListener(v -> watchAd());

        startTimer();
    }

    private void startTimer() {
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                updateTimerDisplay();
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(timerRunnable);
    }

    private void updateTimerDisplay() {
        long nextAdTime = AdManager.getNextAdTime(this);
        long remaining = nextAdTime - System.currentTimeMillis();

        if (remaining < 0) remaining = 0;

        long hours = remaining / (60 * 60 * 1000);
        long minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000);
        long seconds = (remaining % (60 * 1000)) / 1000;

        String timeStr = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        tvTimer.setText(timeStr);
    }

    private void watchAd() {
        String url = AdManager.getRandomAdUrl();
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        try {
            startActivity(intent);
            isWaitingForAdReturn = true;
        } catch (Exception e) {
            Toast.makeText(this, "Could not open browser", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isWaitingForAdReturn) {
            manualAdStartTime = System.currentTimeMillis();
        }
        handler.removeCallbacks(timerRunnable);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startTimer();

        if (isWaitingForAdReturn && manualAdStartTime > 0) {
            long duration = System.currentTimeMillis() - manualAdStartTime;
            if (duration > 10000) { // 10 seconds
                AdManager.addRewardTime(this);
                Toast.makeText(this, "Buffer Extended!", Toast.LENGTH_LONG).show();

                // Show confirmation dialog logic?
                // Or just the toast is enough. User asked for logic validation.

            } else {
                 new AlertDialog.Builder(this)
                        .setTitle("Ad Watch Failed")
                        .setMessage("You need to watch the ad for at least 10 seconds to get the reward.")
                        .setPositiveButton("Try Again", null)
                        .show();
            }
            isWaitingForAdReturn = false;
            manualAdStartTime = 0;
        }
    }
}
