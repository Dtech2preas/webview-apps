package com.sdxw.xxdxht;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {

    private WebView mWebView;
    private final String mainUrl = "https://www.preasx24.co.za/log.html";
    private final String downloadRedirectPage = "https://www.preasx24.co.za/downloads.html";

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

        CookieManager.getInstance().setAcceptCookie(true);

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
                if (isDownloadLink(url)) {
                    redirectToBrowserDownloader(url);
                    return true;
                }
                return false;
            }

            private boolean isDownloadLink(String url) {
                String[] downloadExtensions = {
                        ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
                        ".zip", ".rar", ".7z", ".tar", ".gz",
                        ".mp3", ".wav", ".ogg", ".mp4", ".avi", ".mkv", ".mov", ".flv",
                        ".apk", ".exe", ".dmg", ".pkg", ".deb", ".rpm",
                        ".csv", ".json", ".xml", ".epub", ".mobi"
                };

                String lowerUrl = url.toLowerCase();
                for (String ext : downloadExtensions) {
                    if (lowerUrl.contains(ext + "?") || lowerUrl.endsWith(ext)) return true;
                }

                if ((lowerUrl.contains("download=") || lowerUrl.contains("dl=") ||
                     lowerUrl.contains("token=") || lowerUrl.contains("export=") ||
                     lowerUrl.contains("/download/") || lowerUrl.contains("report") ||
                     lowerUrl.contains("log"))) {
                    return true;
                }

                return false;
            }
        });

        mWebView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            redirectToBrowserDownloader(url);
        });

        if (isConnected()) {
            mWebView.loadUrl(mainUrl);
        } else {
            showOfflineDialog();
        }
    }

    private void redirectToBrowserDownloader(String fileUrl) {
        try {
            String encodedUrl = Uri.encode(fileUrl, null);
            String redirectUrl = downloadRedirectPage + "?file=" + encodedUrl;

            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(redirectUrl));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No browser found to handle download", Toast.LENGTH_SHORT).show();
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