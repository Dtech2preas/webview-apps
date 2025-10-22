package com.sdxw.xxdxht;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
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
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

public class MainActivity extends Activity {

    private WebView mWebView;
    private final String mainUrl = "https://dtech.preasx24.co.za";
    private boolean isFirstLoad = true;
    private File musicDirectory;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Create music directory
        musicDirectory = new File(Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_MUSIC), "MyDownloadedSongs");
        if (!musicDirectory.exists()) {
            musicDirectory.mkdirs();
        }

        mWebView = findViewById(R.id.activity_main_webview);

        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setSaveFormData(true);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(mWebView, true);

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
                String lowerUrl = url.toLowerCase();

                if (isAudioDownload(lowerUrl)) {
                    downloadAndStoreSong(url);
                    return true;
                } else if (isRealDownload(lowerUrl)) {
                    openExternally(url);
                    return true;
                }

                return false;
            }

            private boolean isAudioDownload(String url) {
                String[] audioExtensions = {".mp3", ".wav", ".ogg", ".m4a", ".aac"};
                for (String ext : audioExtensions) {
                    if (url.contains(ext + "?") || url.endsWith(ext)) {
                        return true;
                    }
                }
                return false;
            }

            private boolean isRealDownload(String url) {
                String[] downloadExtensions = {
                        ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
                        ".zip", ".rar", ".7z", ".tar", ".gz",
                        ".mp4", ".avi", ".mkv", ".mov", ".flv",
                        ".apk", ".exe", ".dmg", ".pkg", ".deb", ".rpm",
                        ".csv", ".json", ".xml", ".epub", ".mobi"
                };

                for (String ext : downloadExtensions) {
                    if (url.contains(ext + "?") || url.endsWith(ext)) {
                        return true;
                    }
                }

                return url.contains("download=") ||
                        url.contains("download.php") ||
                        url.contains("/download/");
            }
        });

        // Enhanced Download Listener for audio files
        mWebView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            String lowerUrl = url.toLowerCase();
            if (lowerUrl.contains(".mp3") || lowerUrl.contains(".wav") || 
                lowerUrl.contains(".ogg") || lowerUrl.contains(".m4a")) {
                downloadAndStoreSong(url);
            } else {
                openExternally(url);
            }
        });

        if (savedInstanceState != null) {
            mWebView.restoreState(savedInstanceState);
            isFirstLoad = false;
        } else {
            if (isConnected()) {
                mWebView.loadUrl(mainUrl);
            } else {
                showOfflineDialog();
            }
        }
    }

    private void downloadAndStoreSong(String url) {
        new Thread(() -> {
            try {
                URL downloadUrl = new URL(url);
                URLConnection connection = downloadUrl.openConnection();
                connection.connect();

                // Get file name
                String fileName = URLUtil.guessFileName(url, null, null);
                File outputFile = new File(musicDirectory, fileName);

                InputStream input = connection.getInputStream();
                FileOutputStream output = new FileOutputStream(outputFile);

                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalBytesRead = 0;
                int contentLength = connection.getContentLength();

                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                }

                output.flush();
                output.close();
                input.close();

                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, 
                        "Song downloaded: " + fileName, Toast.LENGTH_LONG).show();
                    
                    // Optionally open music player
                    openMusicPlayer(outputFile.getAbsolutePath());
                });

            } catch (Exception e) {
                runOnUiThread(() -> 
                    Toast.makeText(MainActivity.this, 
                        "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void openMusicPlayer(String songPath) {
        ArrayList<File> songList = getDownloadedSongs();
        int position = findSongPosition(songList, songPath);
        
        Intent intent = new Intent(MainActivity.this, MusicPlayerActivity.class);
        intent.putExtra("songPath", songPath);
        intent.putExtra("songList", songList);
        intent.putExtra("position", position);
        startActivity(intent);
    }

    private ArrayList<File> getDownloadedSongs() {
        ArrayList<File> songs = new ArrayList<>();
        File[] files = musicDirectory.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName().toLowerCase();
                if (name.endsWith(".mp3") || name.endsWith(".wav") || 
                    name.endsWith(".ogg") || name.endsWith(".m4a")) {
                    songs.add(file);
                }
            }
        }
        return songs;
    }

    private int findSongPosition(ArrayList<File> songs, String songPath) {
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).getAbsolutePath().equals(songPath)) {
                return i;
            }
        }
        return 0;
    }

    // Rest of your existing methods remain the same...
    private void openExternally(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No app found to open the file.", Toast.LENGTH_SHORT).show();
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

    @Override
    protected void onResume() {
        super.onResume();
        if (isFirstLoad && isConnected()) {
            mWebView.loadUrl(mainUrl);
            isFirstLoad = false;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mWebView.saveState(outState);
    }
}