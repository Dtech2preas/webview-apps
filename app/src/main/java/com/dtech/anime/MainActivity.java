package com.dtech.anime;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {

    private WebView mWebView;
    private final String mainUrl = "https://anime.preasx24.co.za";

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

        // Add Javascript Bridge
        mWebView.addJavascriptInterface(new WebAppInterface(this), "Android");

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed(); // Ignore SSL errors
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false; // Allow everything to load in WebView
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false; // Allow everything to load in WebView
            }
        });

        // Handle downloads triggered by JavaScript (Optional, keeping it simple)
        mWebView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(url));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Cannot download file", Toast.LENGTH_SHORT).show();
            }
        });

        if (isConnected()) {
            mWebView.loadUrl(mainUrl);
        } else {
            showOfflineDialog();
        }
    }

    @Override
    public void onBackPressed() {
        if (mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed(); // Exit app if no history
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
            String targetUrl = url;
            // Fallback random selection if URL is missing
            if (targetUrl == null || targetUrl.isEmpty()) {
                String[] adUrls = {
                    "https://otieu.com/4/10250311",
                    "https://otieu.com/4/10205357",
                    "https://otieu.com/4/9515888"
                };
                targetUrl = adUrls[new java.util.Random().nextInt(adUrls.length)];
            }

            Intent intent = new Intent(mContext, AdActivity.class);
            intent.putExtra("url", targetUrl);
            mContext.startActivity(intent);
        }
    }
}
