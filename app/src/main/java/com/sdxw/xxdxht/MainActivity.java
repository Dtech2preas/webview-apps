package com.sdxw.xxdxht;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;

public class MainActivity extends Activity {

    private WebView mWebView;
    private final String mainUrl = "https://www.preasx24.co.za/log.html";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mWebView = findViewById(R.id.activity_main_webview);

        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(mWebView, true);

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(url);
            }

            private boolean handleUrl(String url) {
                if (isDownloadLink(url.toLowerCase())) {
                    openDownload(url);
                    return true;
                }
                return false; // Load in WebView
            }

            private boolean isDownloadLink(String url) {
                // Recognized downloadable file types
                String[] downloadExtensions = {
                        ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
                        ".zip", ".rar", ".7z", ".tar", ".gz",
                        ".mp3", ".wav", ".ogg", ".mp4", ".avi", ".mkv", ".mov", ".flv",
                        ".apk", ".exe", ".dmg", ".pkg", ".deb", ".rpm",
                        ".csv", ".json", ".xml", ".epub", ".mobi", ".txt"
                };

                for (String ext : downloadExtensions) {
                    if (url.contains(ext + "?") || url.endsWith(ext)) return true;
                }

                // Detect links that are likely downloads
                if (url.contains("download=") ||
                    url.contains("dl=") ||
                    url.contains("token=") ||
                    url.contains("export=") ||
                    url.contains("/download/") ||
                    url.contains("report") ||
                    url.contains("log") ||
                    url.contains("attachment")) {
                    return true;
                }

                return false;
            }
        });

        mWebView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, 
                                      String contentDisposition, String mimeType, 
                                      long contentLength) {
                // Give user choice to download or view
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("File Download")
                    .setMessage("Do you want to download or view this file?")
                    .setPositiveButton("View", (dialog, which) -> openInBrowser(url))
                    .setNegativeButton("Download", (dialog, which) -> downloadFile(url, contentDisposition, mimeType))
                    .setNeutralButton("Cancel", null)
                    .show();
            }
        });

        if (isConnected()) {
            mWebView.loadUrl(mainUrl);
        } else {
            showOfflineDialog();
        }
    }

    private void openDownload(String url) {
        // Give user choice to open in browser or download
        new AlertDialog.Builder(this)
            .setTitle("Open File")
            .setMessage("How would you like to handle this file?")
            .setPositiveButton("View in Browser", (dialog, which) -> openInBrowser(url))
            .setNegativeButton("Download", (dialog, which) -> downloadFile(url, null, null))
            .show();
    }

    private void openInBrowser(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // Check if there's an activity that can handle this intent
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(this, "No application can handle this request", Toast.LENGTH_SHORT).show();
            }
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No application can handle this request", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error opening file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void downloadFile(String url, String contentDisposition, String mimeType) {
        try {
            // Create download manager request
            android.app.DownloadManager.Request request = 
                new android.app.DownloadManager.Request(Uri.parse(url));
            
            // Get filename from URL or content disposition
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
            
            // Set destination
            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS, 
                fileName);
            
            // Set notification visibility
            request.setNotificationVisibility(
                android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            
            // Get download service and enqueue file
            android.app.DownloadManager dm = 
                (android.app.DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            dm.enqueue(request);
            
            Toast.makeText(this, "Downloading file: " + fileName, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Download failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            // Fallback to browser if download fails
            openInBrowser(url);
        }
    }

    private boolean isConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnected();
    }

    private void showOfflineDialog() {
        new AlertDialog.Builder(this)
                .setTitle("No Internet Connection")
                .setMessage("Please check your internet connection and restart the app.")
                .setCancelable(false)
                .setPositiveButton("Exit", (dialog, which) -> finish())
                .show();
    }

    @Override
    public void onBackPressed() {
        if (mWebView.canGoBack()) {
            mWebView.goBack();
        } else if (!mWebView.getUrl().equals(mainUrl)) {
            mWebView.loadUrl(mainUrl);
        } else {
            super.onBackPressed();
        }
    }
}