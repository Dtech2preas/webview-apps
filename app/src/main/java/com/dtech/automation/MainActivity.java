package com.dtech.automation;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.WindowManager;
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

public class MainActivity extends Activity implements AutomationService.ServiceCallback {

    private static final String TAG = "WebAutomation";
    private WebView mWebView;
    private final String mainUrl = "https://leofame.com/free-tiktok-views";
    private Button btnRecord, btnStop, btnPlay, btnSettings;

    // Modes
    private boolean isRecording = false;
    private boolean isReplaying = false; // Manual replay
    private boolean isBatchMode = false; // Service driven

    private long recordingStartTime = 0;
    private long replayStartTime = 0;
    private int lastExecutedIndex = -1;

    // Batch specific
    private AutomationService mService;
    private boolean mBound = false;
    private String currentBatchOverrideValue = null;

    private List<JSONObject> currentSessionEvents = Collections.synchronizedList(new ArrayList<>());

    private static final String PREFS_NAME = "AutomationPrefs";
    private static final String KEY_EVENTS = "saved_events";

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            AutomationService.LocalBinder binder = (AutomationService.LocalBinder) service;
            mService = binder.getService();
            mService.setCallback(MainActivity.this);
            mBound = true;

            // Check if we should start?
            // User requested "Start All Due" button.
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep Screen On
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        checkPermissions();

        mWebView = findViewById(R.id.activity_main_webview);
        btnRecord = findViewById(R.id.btn_record);
        btnStop = findViewById(R.id.btn_stop);
        btnPlay = findViewById(R.id.btn_play);

        // Add settings button to layout or find it if we updated xml
        // Since we didn't update XML yet, let's assume we need to or reusing PLAY for batch?
        // Plan said: "Add Settings button... Add Start All Due button".
        // Let's assume we add them or hijack existing for now.
        // Wait, I should have updated layout. I will just find them if I can or adding logic.
        // Actually, let's update layout in next step or now?
        // I will use `btnStop` as "Settings" when not recording? No, better to add.
        // For now, let's hook logic and assume Buttons exist, I will fix layout after this.

        setupWebView();

        // Bind to Service
        Intent intent = new Intent(this, AutomationService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);

        // Initial button setup (will fail if IDs missing, but I'll fix XML next)
        btnSettings = findViewById(R.id.btn_settings); // Need to add this to XML
        Button btnBatch = findViewById(R.id.btn_batch); // Need to add this to XML

        setupButtons();

        loadSavedEvents();

        if (isConnected()) {
            // mWebView.loadUrl(mainUrl); // Don't load automatically if we want menu?
            // Default load is fine.
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
                    // Manual Replay
                    injectReplayer(null);
                } else if (isBatchMode) {
                    // Service notified us page is ready via this callback?
                    // Actually, Service logic is: Load URL -> Wait for Page Finish -> Inject.
                    // So we should notify Service or just Inject here if mode is batch?
                    if (mBound && mService != null) {
                         mService.onPageReady();
                         // Service will call back 'injectScript' with the value
                    }
                }
            }
        });
    }

    private void setupButtons() {
        if(btnRecord != null) btnRecord.setOnClickListener(v -> startRecording());
        if(btnStop != null) btnStop.setOnClickListener(v -> stopRecording());
        if(btnPlay != null) btnPlay.setOnClickListener(v -> startReplay());

        // If IDs don't exist yet, this will crash. I must update XML first or handle null.
        // Assuming I update XML immediately after.
        if(btnSettings != null) btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        });

        if(btnBatch != null) btnBatch.setOnClickListener(v -> {
             if (mBound && mService != null) {
                 isBatchMode = true;
                 isRecording = false;
                 isReplaying = false;
                 Intent serviceIntent = new Intent(this, AutomationService.class);
                 startService(serviceIntent); // Ensure started
                 mService.startBatchProcess();
             }
        });
    }

    // --- Service Callback Implementation ---
    @Override
    public void loadUrl(String url) {
        runOnUiThread(() -> mWebView.loadUrl(url));
    }

    @Override
    public void injectScript(String overrideValue) {
        runOnUiThread(() -> {
            Log.d(TAG, "Batch Injecting with override: " + overrideValue);
            replayStartTime = System.currentTimeMillis();
            lastExecutedIndex = -1;
            injectReplayer(overrideValue);
        });
    }

    @Override
    public void onQueueFinished() {
        runOnUiThread(() -> {
            isBatchMode = false;
            Toast.makeText(this, "Batch Processing Complete", Toast.LENGTH_LONG).show();
        });
    }
    // ---------------------------------------

    private void startRecording() {
        isRecording = true;
        isReplaying = false;
        isBatchMode = false;
        recordingStartTime = System.currentTimeMillis();
        currentSessionEvents.clear();

        Toast.makeText(this, "Recording Started", Toast.LENGTH_SHORT).show();
        injectRecorder();
    }

    private void stopRecording() {
        if (!isRecording) return;
        isRecording = false;
        saveSessionToPrefs();
        Toast.makeText(this, "Stopped & Saved", Toast.LENGTH_SHORT).show();
    }

    private void startReplay() {
        if (currentSessionEvents.isEmpty()) {
            Toast.makeText(this, "No saved session to replay", Toast.LENGTH_SHORT).show();
            return;
        }

        isRecording = false;
        isReplaying = true;
        isBatchMode = false;
        replayStartTime = System.currentTimeMillis();
        lastExecutedIndex = -1;

        mWebView.loadUrl(mainUrl);
        Toast.makeText(this, "Replay Started", Toast.LENGTH_SHORT).show();
    }

    private void injectRecorder() {
        String js = readAssetFile("recorder.js");
        String setup = "window.recordingStartTime = " + recordingStartTime + ";";
        mWebView.evaluateJavascript(setup + js, null);
    }

    private void injectReplayer(String overrideValue) {
        String js = readAssetFile("replayer.js");

        JSONArray jsonArray = new JSONArray(currentSessionEvents);
        String eventsJson = jsonArray.toString();

        String overrideJs = "window.overrideInputValue = null;";
        if (overrideValue != null) {
            overrideJs = "window.overrideInputValue = " + JSONObject.quote(overrideValue) + ";";
        }

        String setup = "window.replayEvents = " + eventsJson + "; " +
                       "window.replayStartTime = " + replayStartTime + "; " +
                       "window.lastExecutedIndex = " + lastExecutedIndex + "; " +
                       overrideJs;

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
            .setMessage("Check internet.")
            .setPositiveButton("Exit", (dialog, which) -> finish())
            .show();
    }

    private boolean isConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnected();
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBound) {
            unbindService(connection);
            mBound = false;
        }
    }

    public class WebAppInterface {
        Context mContext;

        WebAppInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void recordEvent(String eventJson) {
            try {
                JSONObject event = new JSONObject(eventJson);
                currentSessionEvents.add(event);
            } catch (JSONException e) {
                Log.e(TAG, "Failed to parse event", e);
            }
        }

        @JavascriptInterface
        public void eventExecuted(int index) {
            if (index > lastExecutedIndex) {
                lastExecutedIndex = index;
            }
        }

        @JavascriptInterface
        public void replayFinished() {
            Log.d(TAG, "Replay Finished from JS");
            if (isBatchMode && mBound && mService != null) {
                mService.onReplayFinished();
            }
        }
    }
}
