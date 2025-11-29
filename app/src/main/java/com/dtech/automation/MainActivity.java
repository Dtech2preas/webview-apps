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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {

    private static final String TAG = "WebAutomation";
    private WebView mWebView;
    private final String mainUrl = "https://leofame.com/free-tiktok-views";
    private Button btnRecord, btnStop, btnPlay;

    private boolean isRecording = false;
    private boolean isReplaying = false;
    private long recordingStartTime = 0;
    private long replayStartTime = 0;
    private int lastExecutedIndex = -1;

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

        setupWebView();
        setupButtons();

        loadSavedEvents();

        if (isConnected()) {
            mWebView.loadUrl(mainUrl);
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
        btnRecord.setOnClickListener(v -> startRecording());
        btnStop.setOnClickListener(v -> stopRecording());
        btnPlay.setOnClickListener(v -> startReplay());
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

    private void startReplay() {
        if (currentSessionEvents.isEmpty()) {
            Toast.makeText(this, "No saved session to replay", Toast.LENGTH_SHORT).show();
            return;
        }

        isRecording = false;
        isReplaying = true;
        replayStartTime = System.currentTimeMillis();
        lastExecutedIndex = -1; // Reset execution progress

        mWebView.loadUrl(mainUrl);
        Toast.makeText(this, "Replay Started", Toast.LENGTH_SHORT).show();
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

        // Convert list to JSON string
        JSONArray jsonArray = new JSONArray(currentSessionEvents);
        String eventsJson = jsonArray.toString();

        // Inject events, start time, and progress
        String setup = "window.replayEvents = " + eventsJson + "; " +
                       "window.replayStartTime = " + replayStartTime + "; " +
                       "window.lastExecutedIndex = " + lastExecutedIndex + ";";
        mWebView.evaluateJavascript(setup + js, null);
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
