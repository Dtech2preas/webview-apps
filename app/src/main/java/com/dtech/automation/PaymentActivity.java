package com.dtech.automation;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class PaymentActivity extends AppCompatActivity {

    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Simple layout with a back button and a webview
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);

        // Header
        android.widget.LinearLayout header = new android.widget.LinearLayout(this);
        header.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        header.setBackgroundColor(android.graphics.Color.parseColor("#1e1e1e"));
        header.setPadding(16, 16, 16, 16);

        Button btnBack = new Button(this);
        btnBack.setText("Back");
        btnBack.setOnClickListener(v -> finish());

        android.widget.TextView title = new android.widget.TextView(this);
        title.setText("Top Up Quota");
        title.setTextColor(android.graphics.Color.WHITE);
        title.setTextSize(18);
        title.setPadding(32, 0, 0, 0);

        header.addView(btnBack);
        header.addView(title);

        // WebView
        webView = new WebView(this);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);

        webView.addJavascriptInterface(new WebAppInterface(), "Android");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Allow all domains to load directly in WebView to match "any URL load without issue" request
                return false;
            }
        });

        // Load the initial pay.html
        webView.loadUrl("https://preasx24.co.za/pay.html");

        layout.addView(header);

        android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT);
        layout.addView(webView, params);

        setContentView(layout);
    }

    // JS Interface to bridge web -> Android
    public class WebAppInterface {
        @JavascriptInterface
        public void addQuota(int randAmount) {
            int testsToAdd = 0;
            switch(randAmount) {
                case 10: testsToAdd = 100; break;
                case 20: testsToAdd = 250; break;
                case 30: testsToAdd = 400; break;
                case 40: testsToAdd = 600; break;
                case 50: testsToAdd = 1000; break;
            }

            if (testsToAdd > 0) {
                QuotaManager.addQuota(PaymentActivity.this, testsToAdd);
                int finalTestsToAdd = testsToAdd;
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(PaymentActivity.this, "Added " + finalTestsToAdd + " tests!", Toast.LENGTH_LONG).show();
                });
            }
        }

        @JavascriptInterface
        public void closePayment() {
            finish();
        }
    }
}
