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
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

public class MainActivity extends Activity {

    private WebView mWebView;
    private final String mainUrl = "https://dtech.preasx24.co.za";
    private boolean isFirstLoad = true;
    private File musicDirectory;
    private HashMap<String, String> downloadQueue = new HashMap<>();

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Create music directory
        musicDirectory = new File(Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_MUSIC), "SpotifyDownloads");
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
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setAllowFileAccessFromFileURLs(true);

        // Enable mixed content for HTTP/HTTPS
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(mWebView, true);

        // Add JavaScript interface for communication
        mWebView.addJavascriptInterface(new WebAppInterface(), "Android");

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Inject JavaScript to override download functionality
                injectDownloadHandler();
            }

            private boolean handleUrl(String url) {
                if (url.startsWith("tel:") || url.startsWith("sms:") || 
                    url.startsWith("mailto:") || url.startsWith("whatsapp:")) {
                    openExternally(url);
                    return true;
                }
                return false;
            }
        });

        // Enhanced Download Listener
        mWebView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (isAudioFile(url)) {
                handleAudioDownload(url, getFileNameFromUrl(url));
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

    // JavaScript Interface for communication between WebView and Android
    public class WebAppInterface {
        @JavascriptInterface
        public void downloadSong(String url, String songName, String artist) {
            handleAudioDownload(url, songName + " - " + artist + ".mp3");
        }

        @JavascriptInterface
        public void playSong(String filePath) {
            openMusicPlayer(filePath);
        }

        @JavascriptInterface
        public String getDownloadedSongs() {
            return getSongsListAsJson();
        }

        @JavascriptInterface
        public void showToast(String message) {
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
        }
    }

    // Inject JavaScript to handle download clicks - Java 8 compatible version
    private void injectDownloadHandler() {
        // Using regular string concatenation instead of text blocks
        String jsCode = "javascript:(function() {" +
            "// Override download buttons" +
            "const originalFetch = window.fetch;" +
            "window.fetch = function(...args) {" +
            "    const url = args[0];" +
            "    if (typeof url === 'string' && url.includes('/api/download')) {" +
            "        // Intercept download API calls" +
            "        return originalFetch.apply(this, args).then(response => {" +
            "            if (response.ok) {" +
            "                response.clone().json().then(data => {" +
            "                    if (data.download_id) {" +
            "                        // Monitor download status" +
            "                        monitorDownload(data.download_id, data);" +
            "                    }" +
            "                });" +
            "            }" +
            "            return response;" +
            "        });" +
            "    }" +
            "    return originalFetch.apply(this, args);" +
            "};" +

            "// Override download button clicks" +
            "document.addEventListener('click', function(e) {" +
            "    const btn = e.target.closest('.download-btn');" +
            "    if (btn) {" +
            "        e.preventDefault();" +
            "        e.stopPropagation();" +
            "        " +
            "        const songCard = btn.closest('.song-card');" +
            "        const songName = songCard.querySelector('h3').textContent;" +
            "        const artist = songCard.querySelector('.artist').textContent;" +
            "        " +
            "        // Get download URL from data attributes or href" +
            "        let downloadUrl = btn.getAttribute('data-url') || " +
            "                         btn.getAttribute('onclick')?.match(/'([^']+)'/)?.[1];" +
            "        " +
            "        if (downloadUrl && Android) {" +
            "            Android.downloadSong(downloadUrl, songName, artist);" +
            "            btn.innerHTML = '<i class=\\\"fas fa-spinner fa-spin\\\"></i> Downloading...';" +
            "            btn.disabled = true;" +
            "        }" +
            "    }" +
            "});" +

            "// Add play functionality to song cards" +
            "document.addEventListener('click', function(e) {" +
            "    const playBtn = e.target.closest('.play-button');" +
            "    if (playBtn) {" +
            "        const songCard = playBtn.closest('.song-card');" +
            "        const songName = songCard.querySelector('h3').textContent;" +
            "        const artist = songCard.querySelector('.artist').textContent;" +
            "        " +
            "        // Check if song is downloaded" +
            "        const fileName = songName + ' - ' + artist + '.mp3';" +
            "        if (Android) {" +
            "            const songs = JSON.parse(Android.getDownloadedSongs());" +
            "            const song = songs.find(s => s.name === fileName);" +
            "            if (song) {" +
            "                Android.playSong(song.path);" +
            "            } else {" +
            "                Android.showToast('Song not downloaded yet');" +
            "            }" +
            "        }" +
            "    }" +
            "});" +

            "function monitorDownload(downloadId, data) {" +
            "    const checkStatus = async () => {" +
            "        try {" +
            "            const response = await fetch('/api/download/' + downloadId + '/status');" +
            "            const status = await response.json();" +
            "            " +
            "            if (status.status === 'completed') {" +
            "                // Download completed - file is ready" +
            "                const fileResponse = await fetch('/api/download/' + downloadId + '/file');" +
            "                const blob = await fileResponse.blob();" +
            "                " +
            "                // Convert blob to object URL and trigger download" +
            "                const url = URL.createObjectURL(blob);" +
            "                if (Android) {" +
            "                    Android.downloadSong(url, data.song_name || 'song', data.artist || 'artist');" +
            "                }" +
            "            } else if (status.status === 'processing' || status.status === 'downloading') {" +
            "                // Still processing, check again in 2 seconds" +
            "                setTimeout(checkStatus, 2000);" +
            "            }" +
            "        } catch (error) {" +
            "            console.error('Status check error:', error);" +
            "        }" +
            "    };" +
            "    checkStatus();" +
            "}" +

            "// Update download buttons with local file status" +
            "setTimeout(() => {" +
            "    if (Android) {" +
            "        const downloadedSongs = JSON.parse(Android.getDownloadedSongs());" +
            "        document.querySelectorAll('.song-card').forEach(card => {" +
            "            const songName = card.querySelector('h3').textContent;" +
            "            const artist = card.querySelector('.artist').textContent;" +
            "            const fileName = songName + ' - ' + artist + '.mp3';" +
            "            const btn = card.querySelector('.download-btn');" +
            "            " +
            "            if (downloadedSongs.some(song => song.name === fileName)) {" +
            "                btn.innerHTML = '<i class=\\\"fas fa-check\\\"></i> Downloaded';" +
            "                btn.style.background = '#1DB954';" +
            "                btn.style.color = 'black';" +
            "                btn.disabled = true;" +
            "            }" +
            "        });" +
            "    }" +
            "}, 1000);" +
            "})();";

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            mWebView.evaluateJavascript(jsCode, null);
        } else {
            mWebView.loadUrl(jsCode);
        }
    }

    private void handleAudioDownload(final String url, final String originalFileName) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Process the filename outside the lambda/runnable
                    String processedFileName = originalFileName.replaceAll("[^a-zA-Z0-9.-]", "_");
                    if (!processedFileName.toLowerCase().endsWith(".mp3")) {
                        processedFileName += ".mp3";
                    }
                    
                    final String finalFileName = processedFileName;
                    
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Starting download: " + finalFileName, Toast.LENGTH_SHORT).show();
                        }
                    });

                    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0...");
                    
                    // Add cookies if available
                    String cookies = CookieManager.getInstance().getCookie(url);
                    if (cookies != null) {
                        connection.setRequestProperty("Cookie", cookies);
                    }

                    connection.connect();

                    if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        File outputFile = new File(musicDirectory, finalFileName);

                        InputStream input = connection.getInputStream();
                        FileOutputStream output = new FileOutputStream(outputFile);

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        long totalBytesRead = 0;
                        final int contentLength = connection.getContentLength();

                        while ((bytesRead = input.read(buffer)) != -1) {
                            output.write(buffer, 0, bytesRead);
                            totalBytesRead += bytesRead;
                            
                            // Update progress if needed
                            final long currentTotal = totalBytesRead;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (contentLength > 0) {
                                        int progress = (int) ((currentTotal * 100) / contentLength);
                                        // You could update a progress bar here
                                    }
                                }
                            });
                        }

                        output.flush();
                        output.close();
                        input.close();

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, 
                                    "Download complete: " + finalFileName, Toast.LENGTH_LONG).show();
                                
                                // Refresh the WebView to update download status
                                mWebView.reload();
                            }
                        });

                    } else {
                        throw new Exception("HTTP error: " + connection.getResponseCode());
                    }

                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, 
                                "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            e.printStackTrace();
                        }
                    });
                }
            }
        }).start();
    }

    private String getSongsListAsJson() {
        try {
            JSONArray songsArray = new JSONArray();
            File[] files = musicDirectory.listFiles();
            
            if (files != null) {
                for (File file : files) {
                    String name = file.getName().toLowerCase();
                    if (name.endsWith(".mp3") || name.endsWith(".wav") || 
                        name.endsWith(".ogg") || name.endsWith(".m4a")) {
                        
                        JSONObject song = new JSONObject();
                        song.put("name", file.getName());
                        song.put("path", file.getAbsolutePath());
                        song.put("size", file.length());
                        songsArray.put(song);
                    }
                }
            }
            return songsArray.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    private boolean isAudioFile(String url) {
        if (url == null) return false;
        String lowerUrl = url.toLowerCase();
        return lowerUrl.contains(".mp3") || lowerUrl.contains(".wav") || 
               lowerUrl.contains(".ogg") || lowerUrl.contains(".m4a") ||
               lowerUrl.contains("/api/download/") || lowerUrl.contains("/file");
    }

    private String getFileNameFromUrl(String url) {
        try {
            String fileName = URLUtil.guessFileName(url, null, null);
            if (fileName == null || fileName.isEmpty()) {
                fileName = "download_" + System.currentTimeMillis() + ".mp3";
            }
            return fileName;
        } catch (Exception e) {
            return "download_" + System.currentTimeMillis() + ".mp3";
        }
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