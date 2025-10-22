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
import android.util.Log;
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
    private static final String TAG = "MainActivity";

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

        if (webView == null || countdownText == null) {
            Toast.makeText(this, "Layout IDs missing!", Toast.LENGTH_LONG).show();
            Log.e(TAG, "WebView or TextView is null!");
            return;
        }

        setupWebView();
        requestStoragePermission();
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.addJavascriptInterface(new WebAppInterface(), "AndroidApp");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());

        // Load a test URL first
        webView.loadUrl("https://dtech.preas24.co.za");

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                if (!hasStoragePermission()) {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            PERMISSION_REQUEST_CODE);
                    return;
                }

                String safeName = URLUtil.guessFileName(url, contentDisposition, mimeType);
                Toast.makeText(MainActivity.this, "Downloading " + safeName, Toast.LENGTH_SHORT).show();

                try {
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    request.setMimeType(mimeType);
                    request.addRequestHeader("cookie", CookieManager.getInstance().getCookie(url));
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, safeName);
                    downloadManager.enqueue(request);
                } catch (Exception e) {
                    Log.e(TAG, "Download failed: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "Download failed", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private boolean hasStoragePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !hasStoragePermission()) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
        }
    }

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

    private class WebAppInterface {
        @JavascriptInterface
        public void playDownloadedSong(String fileName) {
            File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
            File songFile = new File(musicDir, fileName);

            if (songFile.exists()) {
                ArrayList<File> songs = new ArrayList<>();
                File[] files = musicDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.getName().endsWith(".mp3")) {
                            songs.add(f);
                        }
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