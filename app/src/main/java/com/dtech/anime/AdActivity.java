package com.dtech.anime;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.View;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.net.URISyntaxException;

public class AdActivity extends Activity {

    private WebView popupWebView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        popupWebView = new WebView(this);
        setContentView(popupWebView);

        WebSettings settings = popupWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);

        popupWebView.setWebChromeClient(new WebChromeClient());
        popupWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed(); // Ignore SSL errors
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(view, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(view, request.getUrl().toString());
            }

            private boolean handleUrl(WebView view, String url) {
                if (url == null) return false;

                // Standard Web URLs: Load in WebView
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false;
                }

                // INTENT scheme
                if (url.startsWith("intent://")) {
                    try {
                        Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                        if (intent != null) {
                            view.getContext().startActivity(intent);
                            return true;
                        }
                    } catch (URISyntaxException e) {
                        // Log.e("WebView", "Invalid Intent URI", e);
                    } catch (ActivityNotFoundException e) {
                        // App not installed. Try fallback URL or Market.
                        try {
                            Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                            String fallbackUrl = intent.getStringExtra("browser_fallback_url");
                            if (fallbackUrl != null) {
                                view.loadUrl(fallbackUrl);
                                return true;
                            }

                            // Redirect to Play Store if package is available
                            String packageName = intent.getPackage();
                            if (packageName != null) {
                                Intent marketIntent = new Intent(Intent.ACTION_VIEW);
                                marketIntent.setData(Uri.parse("market://details?id=" + packageName));
                                view.getContext().startActivity(marketIntent);
                                return true;
                            }
                        } catch (Exception ex) {
                            // Log.e("WebView", "Failed to handle fallback/market", ex);
                        }
                    }
                    return true; // Prevent error page for unknown scheme
                }

                // Other Custom Schemes (market:, tel:, mailto:, etc.)
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    view.getContext().startActivity(intent);
                    return true;
                } catch (Exception e) {
                    return true; // Ignore if system cannot handle it, don't show error page
                }
            }
        });

        String url = getIntent().getStringExtra("url");
        if (url != null) {
            popupWebView.loadUrl(url);
        }

        enterImmersiveMode();
    }

    @Override
    public void onBackPressed() {
        if (popupWebView.canGoBack()) {
            popupWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterImmersiveMode();
        }
    }
}
