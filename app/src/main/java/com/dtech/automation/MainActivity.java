package com.dtech.automation;

import android.annotation.SuppressLint;
import android.app.Activity;
import java.io.FileInputStream;
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

    // Scanner UI
    private android.widget.RelativeLayout overlayScanner;
    private android.view.View draggableBox;
    private Button btnScanCatch, btnScanFinish, btnScanCancel;
    private Button btnScanSizeInc, btnScanSizeDec;
    private float dX, dY;
    private List<ServiceRepository.ExtractionPoint> tempExtractionPoints = new ArrayList<>();

    // --- Recording State Machine ---
    private static final int RECORD_MODE_NONE = 0;
    private static final int RECORD_MODE_SUCCESS = 1;
    private static final int RECORD_MODE_FAILURE = 2;
    private int recordingMode = RECORD_MODE_NONE;

    private boolean isReplaying = false;
    private long recordingStartTime = 0;
    private String recordingStartUrl = "";
    private long replayStartTime = 0;
    private int lastExecutedIndex = -1;

    // Current Service
    private ServiceRepository serviceRepo;
    private ServiceRepository.ServiceData currentService;

    // Batch Execution State
    private List<String> credentialList = new ArrayList<>();
    private int currentCredentialIndex = 0;
    private boolean isBatchRunning = false;
    private boolean isWaitingForNext = false;
    private boolean useCoordinateMode = false;
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

    // Promo Popup Scheduler
    private android.os.Handler promoHandler = new android.os.Handler();
    private Runnable promoRunnable;

    private static final String PREFS_NAME = "AutomationPrefs";
    private static final String KEY_BATCH_INDEX = "batch_current_index";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);

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
        setupScannerUI();

        loadLastService();
        startAdChecker();
        startPromoScheduler();
    }

    private void startPromoScheduler() {
        // Schedule random popups (every 5-15 minutes)
        // For testing/demo, let's say every 5-10 minutes
        long delay = 300000 + (long)(Math.random() * 300000); // 5 to 10 mins

        promoRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isBatchRunning && !isReplaying && recordingMode == RECORD_MODE_NONE) {
                    showRandomPromo();
                }
                // Schedule next
                long nextDelay = 300000 + (long)(Math.random() * 600000); // 5-15 mins
                promoHandler.postDelayed(this, nextDelay);
            }
        };
        promoHandler.postDelayed(promoRunnable, delay);
    }

    private void showRandomPromo() {
        if (isFinishing()) return;

        boolean showChannel = Math.random() > 0.5;
        String title = showChannel ? "Stay in the Loop!" : "Suggestions?";
        String msg = showChannel
            ? "Join our D-TECH Telegram channel for new updates and features."
            : "Have an improvement idea? Reach out to me on my personal Telegram.";
        String btnText = showChannel ? "JOIN CHANNEL" : "CONTACT ME";
        String url = showChannel ? "https://t.me/DTECHX24" : "https://t.me/PREASX24";

        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(R.layout.dialog_promo);
        dialog.setCancelable(true);
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        TextView tvTitle = dialog.findViewById(R.id.tv_promo_title);
        TextView tvMsg = dialog.findViewById(R.id.tv_promo_message);
        Button btnAction = dialog.findViewById(R.id.btn_promo_action);
        Button btnClose = dialog.findViewById(R.id.btn_promo_close);

        tvTitle.setText(title);
        tvMsg.setText(msg);
        btnAction.setText(btnText);

        btnAction.setOnClickListener(v -> {
            try {
                startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)));
            } catch (Exception e) {}
            dialog.dismiss();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        // Auto-close after 30 seconds
        new android.os.Handler().postDelayed(() -> {
            if (dialog.isShowing()) dialog.dismiss();
        }, 30000);

        dialog.show();
    }

    private void setupScannerUI() {
        overlayScanner = findViewById(R.id.overlay_scanner);
        draggableBox = findViewById(R.id.draggable_box);
        btnScanCatch = findViewById(R.id.btn_scan_catch);
        btnScanFinish = findViewById(R.id.btn_scan_finish);
        btnScanCancel = findViewById(R.id.btn_scan_cancel);
        btnScanSizeInc = findViewById(R.id.btn_scan_size_inc);
        btnScanSizeDec = findViewById(R.id.btn_scan_size_dec);

        // Resize Logic
        btnScanSizeInc.setOnClickListener(v -> {
            android.view.ViewGroup.LayoutParams params = draggableBox.getLayoutParams();
            params.width += 20;
            params.height += 20;
            draggableBox.setLayoutParams(params);
        });

        btnScanSizeDec.setOnClickListener(v -> {
            android.view.ViewGroup.LayoutParams params = draggableBox.getLayoutParams();
            if (params.width > 50 && params.height > 50) {
                params.width -= 20;
                params.height -= 20;
                draggableBox.setLayoutParams(params);
            }
        });

        // Draggable Logic
        draggableBox.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    dX = view.getX() - event.getRawX();
                    dY = view.getY() - event.getRawY();
                    break;
                case android.view.MotionEvent.ACTION_MOVE:
                    view.animate()
                        .x(event.getRawX() + dX)
                        .y(event.getRawY() + dY)
                        .setDuration(0)
                        .start();
                    break;
                default:
                    return false;
            }
            return true;
        });

        btnScanCatch.setOnClickListener(v -> {
            // Calculate center of box relative to WebView
            int[] webViewLoc = new int[2];
            mWebView.getLocationOnScreen(webViewLoc);

            int[] boxLoc = new int[2];
            draggableBox.getLocationOnScreen(boxLoc);

            // Center of box
            float centerX = boxLoc[0] + (draggableBox.getWidth() / 2f);
            float centerY = boxLoc[1] + (draggableBox.getHeight() / 2f);

            // Relative to WebView
            float relX = centerX - webViewLoc[0];
            float relY = centerY - webViewLoc[1];

            // Convert to density pixels if needed, or JS pixels
            float density = getResources().getDisplayMetrics().density;
            float jsX = relX / density;
            float jsY = relY / density;

            // Better approach: inject a helper function
            String helper = "(function(x, y) {" +
                            "  var el = document.elementFromPoint(x, y);" +
                            "  if (!el) return null;" +
                            "  var getSelector = function(el) {" +
                            "    if (el.id) return '#' + el.id;" +
                            "    var path = [];" +
                            "    var current = el;" +
                            "    while (current && current.nodeType === 1) {" +
                            "       var selector = current.nodeName.toLowerCase();" +
                            "       if (current.id) {" +
                            "           selector = '#' + current.id;" +
                            "           path.unshift(selector);" +
                            "           break;" +
                            "       } else {" +
                            "           var sib = current, nth = 1;" +
                            "           while (sib = sib.previousElementSibling) {" +
                            "               if (sib.nodeName.toLowerCase() == selector) nth++;" +
                            "           }" +
                            "           if (nth != 1) selector += ':nth-of-type('+nth+')';" +
                            "       }" +
                            "       path.unshift(selector);" +
                            "       current = current.parentNode;" +
                            "    }" +
                            "    return path.join(' > ');" +
                            "  };" +
                            "  return JSON.stringify({ selector: getSelector(el), text: el.innerText });" +
                            "})(" + jsX + ", " + jsY + ")";

            mWebView.evaluateJavascript(helper, val -> {
                if (val != null && val.length() > 2) {
                     String jsonStr = val;
                     if (jsonStr.startsWith("\"") && jsonStr.endsWith("\"")) {
                         jsonStr = jsonStr.substring(1, jsonStr.length() - 1).replace("\\\"", "\"");
                     }
                     try {
                         JSONObject obj = new JSONObject(jsonStr);
                         String selector = obj.getString("selector");
                         String text = obj.optString("text", "");
                         showLabelDialog(selector, text);
                     } catch (Exception e) {
                         Toast.makeText(this, "Failed to catch element", Toast.LENGTH_SHORT).show();
                     }
                }
            });
        });

        btnScanFinish.setOnClickListener(v -> {
            if (currentService != null) {
                currentService.setExtractionPoints(new ArrayList<>(tempExtractionPoints));
                serviceRepo.addOrUpdateService(currentService);
                Toast.makeText(this, "Extraction Points Saved!", Toast.LENGTH_SHORT).show();
            }
            overlayScanner.setVisibility(android.view.View.GONE);
            startRecordingPhase2();
        });

        btnScanCancel.setOnClickListener(v -> {
            overlayScanner.setVisibility(android.view.View.GONE);
            startRecordingPhase2();
        });
    }

    private void showLabelDialog(String selector, String text) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Label (e.g. Balance)");

        boolean hasDigits = text.matches(".*\\d.*");

        // Smart Regex Generation
        String pattern = "";
        if (hasDigits) {
             // Replace any sequence of digits with \d+ and escape potentially regex-sensitive chars (simple)
             // We will take a simple approach: Escape special chars, then unescape our \d+ placeholder
             // Actually, easier:
             // 1. Split text by digits
             // 2. Escape the parts
             // 3. Join with \d+
             // But wait, "Balance: 50.00" -> "Balance: \d+.\d+"
             // JS Regex: /Balance: \d+\.\d+/

             // Implementation:
             // replace all digit sequences with the token "___NUM___"
             String temp = text.replaceAll("\\d+", "___NUM___");
             // Escape the string for Regex (quote it)
             // Since Java doesn't have a built-in Regex.escape for Javascript, we do basic escaping
             String escaped = temp.replace("\\", "\\\\")
                                  .replace("^", "\\^")
                                  .replace("$", "\\$")
                                  .replace(".", "\\.")
                                  .replace("|", "\\|")
                                  .replace("?", "\\?")
                                  .replace("*", "\\*")
                                  .replace("+", "\\+")
                                  .replace("(", "\\(")
                                  .replace(")", "\\)")
                                  .replace("[", "\\[")
                                  .replace("{", "\\{");

             // Restore token to Javascript Regex \d+
             pattern = escaped.replace("___NUM___", "\\d+");
        }

        String msg = "Captured: " + (text.length() > 20 ? text.substring(0, 20) + "..." : text) + "\n\n" +
                     (hasDigits ? "Detected Numbers -> Smart Pattern Generated" : "Static Content");

        final String finalPattern = pattern;

        new AlertDialog.Builder(this)
            .setTitle("Label this Element")
            .setMessage(msg)
            .setView(input)
            .setPositiveButton("Save", (d, w) -> {
                String label = input.getText().toString().trim();
                if (label.isEmpty()) label = "Data";

                tempExtractionPoints.add(new ServiceRepository.ExtractionPoint(selector, label, hasDigits, finalPattern));
                Toast.makeText(this, "Added: " + label, Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
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
    protected void onDestroy() {
        super.onDestroy();
        if (adCheckRunnable != null) adCheckHandler.removeCallbacks(adCheckRunnable);
        if (promoRunnable != null) promoHandler.removeCallbacks(promoRunnable);
        if (batchHandler != null) {
            batchHandler.removeCallbacksAndMessages(null);
        }
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

                if (isWaitingForNext) return;

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
            .setMessage("Please log in correctly to your account.\n\nClick STOP only after you have fully logged in and can see your dashboard.")
            .setPositiveButton("Start", (d, w) -> {
                // Reset session
                currentSessionEvents.clear();
                recordingStartTime = System.currentTimeMillis();
                recordingMode = RECORD_MODE_SUCCESS;
                recordingStartUrl = currentService.getLoginUrl();

                // Reset UI
                btnRecord.setVisibility(android.view.View.GONE);
                btnStop.setVisibility(android.view.View.VISIBLE);
                btnPlay.setVisibility(android.view.View.GONE);

                mWebView.loadUrl(recordingStartUrl);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showKeywordConfirmationDialog(List<String> keywords) {
        StringBuilder msg = new StringBuilder("Found potential success indicators:\n");
        for(String k : keywords) msg.append("- ").append(k).append("\n");
        msg.append("\nUse these to verify login?");

        new AlertDialog.Builder(this)
            .setTitle("Verify Success Logic")
            .setMessage(msg.toString())
            .setPositiveButton("Yes, Use These", (d, w) -> {
                currentService.setSuccessKeywords(keywords);
                currentService.setSuccessSelector(null); // Clear selector if using keywords
                serviceRepo.addOrUpdateService(currentService);

                askToExtractData();
            })
            .setNegativeButton("No, Open Scanner", (d, w) -> {
                 openScannerOverlay();
            })
            .setCancelable(false)
            .show();
    }

    private void showPostSuccessDialog(String reason, boolean forceManual) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
             .setTitle("Success Verification")
             .setMessage(reason + "\n\nPlease use the Scanner tool to select something unique on this page (like your Balance or Profile Name).")
             .setPositiveButton("Open Scanner", (d, w) -> openScannerOverlay())
             .setCancelable(false);

        if (!forceManual) {
            builder.setNegativeButton("Skip (Not Recommended)", (d, w) -> {
                 // Fallback to just URL check (risky)
                 currentService.setSuccessKeywords(new ArrayList<>());
                 currentService.setSuccessSelector(null);
                 serviceRepo.addOrUpdateService(currentService);

                 recordingMode = RECORD_MODE_NONE;
                 btnStop.setVisibility(android.view.View.GONE);
                 startRecordingPhase2();
            });
        } else {
            builder.setMessage(reason + "\n\n⚠️ Important: You must pick an element on the screen (like 'Balance') to prove you are logged in.");
            builder.setNegativeButton("Help", (d, w) -> {
                 new AlertDialog.Builder(this)
                     .setTitle("Why is this required?")
                     .setMessage("The system can't tell if you logged in because the website address didn't change.\n\nYou need to show it a part of the page that only appears when you are logged in.")
                     .setPositiveButton("Got it", (d2, w2) -> showPostSuccessDialog(reason, true))
                     .show();
            });
        }

        builder.show();
    }

    private void askToExtractData() {
        new AlertDialog.Builder(this)
            .setTitle("Want to save data?")
            .setMessage("Do you want to save specific info like Account Balance or Status?")
            .setPositiveButton("Yes", (d, w) -> openScannerOverlay())
            .setNegativeButton("No, I'm done", (d, w) -> {
                recordingMode = RECORD_MODE_NONE;
                btnStop.setVisibility(android.view.View.GONE);
                startRecordingPhase2();
            })
            .show();
    }

    private void openScannerOverlay() {
        tempExtractionPoints.clear();
        overlayScanner.setVisibility(android.view.View.VISIBLE);
        Toast.makeText(this, "Scanner Mode Active", Toast.LENGTH_SHORT).show();
    }

    private void enableManualSelectionMode() {
        Toast.makeText(this, "Click on a success element...", Toast.LENGTH_LONG).show();
        mWebView.evaluateJavascript("window.enableSelectionMode()", null);
    }

    private void startRecordingPhase2() {
        new AlertDialog.Builder(this)
            .setTitle("Step 2: Failure Recording")
            .setMessage("Now, try to log in with a WRONG password.\n\nClick STOP when you see the error message (e.g., 'Incorrect Password').")
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
            // Save initial data
            String currentUrl = mWebView.getUrl();
            currentService.setSuccessUrl(currentUrl);
            JSONArray arr = new JSONArray(currentSessionEvents);
            currentService.setScriptJson(arr.toString());

            // Analyze page for success indicators
            mWebView.evaluateJavascript("window.analyzeSuccessState()", value -> {
                 List<String> foundKeywords = new ArrayList<>();
                 try {
                     // value might be double encoded like "\"['logout']\"" or just "['logout']"
                     String jsonStr = value;
                     if (jsonStr != null && jsonStr.length() > 2 && jsonStr.startsWith("\"") && jsonStr.endsWith("\"")) {
                         // Remove outer quotes and unescape
                         jsonStr = jsonStr.substring(1, jsonStr.length() - 1).replace("\\\"", "\"");
                     }

                     JSONArray json = new JSONArray(jsonStr);
                     for(int i=0; i<json.length(); i++) foundKeywords.add(json.getString(i));
                 } catch (Exception e) {
                     Log.e(TAG, "Error parsing success keywords", e);
                 }

                 // Check if URL changed significantly
                 boolean urlChanged = !currentUrl.split("\\?")[0].equals(recordingStartUrl.split("\\?")[0]);

                 if (foundKeywords.isEmpty()) {
                     // No auto-keywords found. Ask user to select manually.
                     // If URL didn't change, we FORCE manual selection.
                     if (!urlChanged) {
                         showPostSuccessDialog("The URL did not change after login.", true);
                     } else {
                         showPostSuccessDialog("No common success indicators found.", false);
                     }
                 } else {
                     // Found keywords. Ask user to confirm.
                     // Even if keywords found, if URL didn't change, we should be careful.
                     // But keywords are usually good enough if they are "Logout" or "My Account".
                     showKeywordConfirmationDialog(foundKeywords);
                 }
            });

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
        useCoordinateMode = false; // Default to smart logic

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

        Toast.makeText(this, "Starting Batch: " + currentService.getName() + (useCoordinateMode ? " (Coords)" : ""), Toast.LENGTH_SHORT).show();
        processNextCredential();
    }

    private void stopBatch() {
        isBatchRunning = false;
        isReplaying = false;
        isWaitingForNext = false;
        useCoordinateMode = false;

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
                 isWaitingForNext = false;
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
                       "window.overridePassword = overrides.password;" +
                       "window.coordinateMode = " + useCoordinateMode + ";";

        mWebView.evaluateJavascript(setup + js, null);

        final int targetIndex = currentCredentialIndex;
        // Start verification
        verificationRunnable = () -> checkVerificationStatus(targetIndex);
        batchHandler.postDelayed(verificationRunnable, 2000);
    }

    private void checkVerificationStatus(int targetIndex) {
        if (!isBatchRunning) return;
        if (targetIndex != currentCredentialIndex) return;
        if (isWaitingForNext) return;

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

        String successSelector = currentService.getSuccessSelector() != null ? currentService.getSuccessSelector() : "";
        // Escape single quotes for JS string
        String safeSelector = successSelector.replace("'", "\\'");

        List<String> successKeywords = currentService.getSuccessKeywords();
        JSONArray skwJson = new JSONArray();
        if (successKeywords != null) {
            for(String k : successKeywords) skwJson.put(k);
        }

        List<ServiceRepository.ExtractionPoint> extractionPoints = currentService.getExtractionPoints();
        JSONArray epJson = new JSONArray();
        if (extractionPoints != null) {
            for (ServiceRepository.ExtractionPoint ep : extractionPoints) {
                try { epJson.put(ep.toJson()); } catch (JSONException e) {}
            }
        }

        String injection = "window.targetSuccessUrl = '" + successUrl + "'; " +
                           "window.successSelector = '" + safeSelector + "'; " +
                           "window.successKeywords = " + skwJson.toString() + "; " +
                           "window.failureKeywords = " + kwJson.toString() + "; " +
                           "window.extractionPoints = " + epJson.toString() + ";";

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
                         String extracted = "";
                         if (res.has("extractedData")) {
                             JSONObject ext = res.getJSONObject("extractedData");
                             // Format extraction
                             StringBuilder sb = new StringBuilder();
                             java.util.Iterator<String> keys = ext.keys();
                             while(keys.hasNext()) {
                                 String key = keys.next();
                                 sb.append(" | ").append(key).append(": ").append(ext.getString(key));
                             }
                             extracted = sb.toString();
                         }
                         logResult(true, res.optString("detail") + extracted, targetIndex);
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

        // Format: STATUS|SERVICE_NAME|email:pass (powered by DTECH)
        String serviceName = currentService != null ? currentService.getName() : "Unknown";
        String msg = status + "|" + serviceName + "|" + cred + " (powered by DTECH)";

        Log.i(TAG, "Batch Result: " + msg);
        runOnUiThread(() -> Toast.makeText(this, status + ": " + cred, Toast.LENGTH_SHORT).show());
        saveResultToFile(msg);

        if (verificationRunnable != null) batchHandler.removeCallbacks(verificationRunnable);

        // Special Check for First Account (Index 0)
        if (index == 0 && !useCoordinateMode) {
             // Pause and ask user
             runOnUiThread(() -> {
                 new AlertDialog.Builder(this)
                     .setTitle("First Account Check")
                     .setMessage("Account #1 processed.\n\nWas the login accurate and successful?")
                     .setPositiveButton("Yes, Continue", (d, w) -> {
                         // Proceed
                         moveToNext(index);
                     })
                     .setNegativeButton("No, Try Coordinates", (d, w) -> {
                         // Switch mode and restart from 0
                         Toast.makeText(this, "Switching to Coordinate Mode...", Toast.LENGTH_SHORT).show();
                         useCoordinateMode = true;
                         currentCredentialIndex = 0;
                         // Clear logs for retry? Maybe just append.
                         processNextCredential();
                     })
                     .setCancelable(false)
                     .show();
             });
             // Return early to prevent auto-move
             return;
        }
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

            // Map<ServiceName, List<Lines>>
            java.util.Map<String, List<String>> groups = new java.util.HashMap<>();
            List<String> rawLines = new ArrayList<>();

            String line;
            while ((line = reader.readLine()) != null) {
                rawLines.add(line);
                String[] parts = line.split("\\|");
                String svc = "General";
                String content = line;

                // Check if new format: STATUS|SERVICE|CONTENT
                if (parts.length >= 3) {
                    svc = parts[1];
                    // Reconstruct content without the pipes if desired, or keep raw
                    // Let's keep a cleaner display format: STATUS content
                    content = parts[0] + " " + parts[2];
                }

                if (!groups.containsKey(svc)) groups.put(svc, new ArrayList<>());
                groups.get(svc).add(content);
            }
            reader.close();

            // Build Display String
            for (String svc : groups.keySet()) {
                sb.append("--- ").append(svc).append(" ---\n");
                for (String l : groups.get(svc)) {
                    sb.append(l).append("\n");
                }
                sb.append("\n");
            }

            String res = sb.toString();
            String rawRes = android.text.TextUtils.join("\n", rawLines);

            new AlertDialog.Builder(this)
                .setTitle("Batch Results")
                .setMessage(res.length() > 0 ? res : "No results.")
                .setPositiveButton("OK", null)
                .setNegativeButton("Share", (d, w) -> shareResults(res))
                .setNeutralButton("Options", (d, w) -> showResultOptions(rawRes, new ArrayList<>(groups.keySet())))
                .show();
        } catch (IOException e) { Toast.makeText(this, "No results.", Toast.LENGTH_SHORT).show(); }
    }

    private void showResultOptions(String rawResults, List<String> serviceNames) {
        String[] options = {"Copy Success Only", "Clear Results"};
        new AlertDialog.Builder(this).setItems(options, (d, w) -> {
            if (w == 0) showCopyOptions(rawResults, serviceNames);
            else if (w == 1) { deleteFile("batch_results.txt"); Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show(); }
        }).show();
    }

    private void showCopyOptions(String rawResults, List<String> serviceNames) {
        // Add "All Services" to the list
        List<String> choices = new ArrayList<>();
        choices.add("All Services");
        choices.addAll(serviceNames);

        String[] choiceArray = choices.toArray(new String[0]);

        new AlertDialog.Builder(this)
            .setTitle("Copy from which service?")
            .setItems(choiceArray, (d, w) -> {
                String selected = choiceArray[w];
                copySuccessResults(rawResults, selected);
            })
            .show();
    }

    private void copySuccessResults(String full, String targetService) {
        StringBuilder sb = new StringBuilder();
        for (String line : full.split("\n")) {
            if (!line.contains("SUCCESS")) continue;

            String[] parts = line.split("\\|");
            // Format: STATUS|SERVICE|CONTENT or OldFormat

            if (parts.length >= 3) {
                String svc = parts[1];
                if (targetService.equals("All Services") || targetService.equals(svc)) {
                    sb.append(parts[2]).append("\n");
                }
            } else {
                // Handle legacy format (treat as General/All)
                if (targetService.equals("All Services") || targetService.equals("General")) {
                     sb.append(line).append("\n");
                }
            }
        }

        if (sb.length() == 0) { Toast.makeText(this, "No success results found for " + targetService, Toast.LENGTH_SHORT).show(); return; }

        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Results", sb.toString()));
        Toast.makeText(this, "Copied Success Results", Toast.LENGTH_SHORT).show();
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
        isWaitingForNext = true;
        mWebView.stopLoading();
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
        @JavascriptInterface public void onSuccessElementSelected(String selector) {
            Log.d(TAG, "Manual Success Element Selected: " + selector);
            if (currentService != null) {
                currentService.setSuccessSelector(selector);
                // Also clear generic keywords to prioritize this specific element
                currentService.setSuccessKeywords(new ArrayList<>());
                serviceRepo.addOrUpdateService(currentService);

                runOnUiThread(() -> {
                     Toast.makeText(mContext, "Success Indicator Set!", Toast.LENGTH_SHORT).show();

                     // Resume normal flow
                     recordingMode = RECORD_MODE_NONE;
                     btnStop.setVisibility(android.view.View.GONE);
                     startRecordingPhase2();
                });
            }
        }
    }
}
