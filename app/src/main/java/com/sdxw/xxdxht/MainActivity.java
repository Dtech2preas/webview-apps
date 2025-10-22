package com.sdxw.xxdxht;

import android.Manifest;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private WebView webView;
    private TextView countdownText;
    private DownloadManager downloadManager;
    private ArrayList<File> downloadedSongs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.activity_main_webview);
        countdownText = findViewById(R.id.countdown_text);
        downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);

        setupWebView();
        requestStoragePermission();
    }

    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);

        webView.addJavascriptInterface(new WebAppInterface(), "AndroidApp");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());

        // Load your web app
        webView.loadUrl("https://dtech.preas24.co.za");

        // Download listener
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
                    return;
                }

                final String safeName = URLUtil.guessFileName(url, contentDisposition, mimeType);
                Toast.makeText(MainActivity.this, "Downloading " + safeName, Toast.LENGTH_SHORT).show();

                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                request.addRequestHeader("cookie", CookieManager.getInstance().getCookie(url));
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, safeName);

                downloadManager.enqueue(request);

                // Notify website of start
                webView.post(() ->
                        webView.evaluateJavascript(
                                "window.onDownloadStart && window.onDownloadStart('" + safeName + "')",
                                null
                        )
                );

                // When completed, simulate callback
                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "Saved: " + safeName, Toast.LENGTH_LONG).show();
                            webView.evaluateJavascript(
                                    "window.updateDownloadStatus && window.updateDownloadStatus('" + safeName + "')",
                                    null
                            );
                        });
                    } catch (InterruptedException ignored) {}
                }).start();
            }
        });
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
            }
        }
    }

    // Handle permission result
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage Permission Granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Storage Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // JavaScript Interface for web to app communication
    private class WebAppInterface {
        @JavascriptInterface
        public void playDownloadedSong(String fileName) {
            File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
            File songFile = new File(musicDir, fileName);

            if (songFile.exists()) {
                ArrayList<File> songs = new ArrayList<>();
                for (File f : musicDir.listFiles()) {
                    if (f.getName().endsWith(".mp3")) {
                        songs.add(f);
                    }
                }

                Intent intent = new Intent(MainActivity.this, MusicPlayerActivity.class);
                intent.putExtra("songPath", songFile.getAbsolutePath());
                intent.putExtra("songList", songs);
                intent.putExtra("position", songs.indexOf(songFile));
                startActivity(intent);
            } else {
                Toast.makeText(MainActivity.this, "Song not found", Toast.LENGTH_SHORT).show();
            }
        }

        @JavascriptInterface
        public void showToast(String message) {
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
        }
    }
}