package com.sdxw.xxdxht;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.DownloadListener;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {

    private WebView mWebView;
    private final String mainUrl = "https://free.dtech24.co.za/next.html";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mWebView = findViewById(R.id.activity_main_webview);
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleCustomUrlLogic(url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleCustomUrlLogic(request.getUrl().toString());
            }

            private boolean handleCustomUrlLogic(String url) {
                if (isDownloadableFile(url)) {
                    openUrlExternally(url);
                    return true;
                }
                return false;
            }

            private boolean isDownloadableFile(String url) {
                String[] downloadExtensions = {
                    // Documents
                    ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt", ".rtf",
                    // Archives
                    ".zip", ".rar", ".7z", ".tar", ".gz",
                    // Media
                    ".mp3", ".wav", ".ogg", ".mp4", ".avi", ".mkv", ".mov", ".flv",
                    // Images
                    ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".svg",
                    // Executables
                    ".apk", ".exe", ".dmg", ".pkg", ".deb", ".rpm",
                    // Others
                    ".csv", ".json", ".xml", ".html", ".htm", ".epub", ".mobi"
                };

                String lowerUrl = url.toLowerCase();
                for (String ext : downloadExtensions) {
                    if (lowerUrl.contains(ext + "?") || lowerUrl.endsWith(ext)) {
                        return true;
                    }
                }

                return lowerUrl.contains("download=") ||
                       lowerUrl.contains("download.php") ||
                       lowerUrl.contains("/download/");
            }
        });

        mWebView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                openUrlExternally(url);
            }
        });

        if (isConnected()) {
            mWebView.loadUrl(mainUrl);
        } else {
            showOfflineDialog();
        }
    }

    private void openUrlExternally(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No application can handle this download", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            if (!mWebView.getUrl().equals(mainUrl)) {
                mWebView.loadUrl(mainUrl);
            } else {
                super.onBackPressed();
            }
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
}