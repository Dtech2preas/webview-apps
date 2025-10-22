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
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

public class MainActivity extends Activity {

    private WebView mWebView;
    private final String mainUrl = "https://www.preasx24.co.za/log.html";
    private boolean isFirstLoad = true;
    private File musicDirectory;
    private File downloadsJsonFile;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        musicDirectory = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MUSIC), "SpotifyDownloads");
        if (!musicDirectory.exists()) musicDirectory.mkdirs();

        downloadsJsonFile = new File(musicDirectory, "downloads.json");
        if (!downloadsJsonFile.exists()) {
            try (FileWriter fw = new FileWriter(downloadsJsonFile)) {
                fw.write("[]");
            } catch (Exception ignored) {}
        }

        mWebView = findViewById(R.id.activity_main_webview);
        setupWebView();

        if (savedInstanceState != null) {
            mWebView.restoreState(savedInstanceState);
            isFirstLoad = false;
        } else {
            if (isConnected()) mWebView.loadUrl(mainUrl);
            else showOfflineDialog();
        }
    }

    private void setupWebView() {
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(mWebView, true);

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
                injectDownloadHandler();
            }
            private boolean handleUrl(String url) {
                if (url.startsWith("tel:") || url.startsWith("sms:") || url.startsWith("mailto:") || url.startsWith("whatsapp:")) {
                    openExternally(url);
                    return true;
                }
                return false;
            }
        });
    }

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

    private void injectDownloadHandler() {
        String js = "javascript:(function(){" +
                "window.updateDownloadStatus=function(fileName){" +
                "document.querySelectorAll('.song-card').forEach(card=>{" +
                "let song=card.querySelector('h3').textContent;" +
                "let artist=card.querySelector('.artist').textContent;" +
                "let name=song+' - '+artist+'.mp3';" +
                "if(name===fileName){" +
                "let btn=card.querySelector('.download-btn');" +
                "if(btn){btn.innerHTML='<i class=\\\"fas fa-check\\\"></i> Downloaded';btn.style.background='#1DB954';btn.disabled=true;}" +
                "}});" +
                "};})();";
        mWebView.evaluateJavascript(js, null);
    }

    private void handleAudioDownload(final String url, final String fileName) {
        new Thread(() -> {
            try {
                String safeName = fileName.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
                if (!safeName.endsWith(".mp3")) safeName += ".mp3";

                File outputFile = new File(musicDirectory, safeName);
                if (outputFile.exists()) outputFile.delete();

                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "Downloading " + safeName, Toast.LENGTH_SHORT).show());

                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                String cookies = CookieManager.getInstance().getCookie(url);
                if (cookies != null) conn.setRequestProperty("Cookie", cookies);
                conn.connect();

                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(outputFile)) {
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                }

                // Save to downloads JSON
                addSongToJson(outputFile);

                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Saved: " + safeName, Toast.LENGTH_LONG).show();
                    mWebView.evaluateJavascript(
                            "window.updateDownloadStatus && window.updateDownloadStatus('" + safeName + "')", null);
                });

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void addSongToJson(File songFile) {
        try {
            JSONArray arr = new JSONArray(readFile(downloadsJsonFile));
            JSONObject song = new JSONObject();
            song.put("name", songFile.getName());
            song.put("path", songFile.getAbsolutePath());
            song.put("size", songFile.length());
            arr.put(song);
            try (FileWriter fw = new FileWriter(downloadsJsonFile, false)) {
                fw.write(arr.toString());
            }
        } catch (Exception ignored) {}
    }

    private String readFile(File f) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(f));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    private String getSongsListAsJson() {
        try {
            return readFile(downloadsJsonFile);
        } catch (Exception e) {
            return "[]";
        }
    }

    private void openMusicPlayer(String songPath) {
        ArrayList<File> songList = getDownloadedSongs();
        int pos = findSongPosition(songList, songPath);
        Intent i = new Intent(MainActivity.this, MusicPlayerActivity.class);
        i.putExtra("songPath", songPath);
        i.putExtra("songList", songList);
        i.putExtra("position", pos);
        startActivity(i);
    }

    private ArrayList<File> getDownloadedSongs() {
        ArrayList<File> list = new ArrayList<>();
        File[] files = musicDirectory.listFiles();
        if (files != null) for (File f : files)
            if (f.getName().endsWith(".mp3")) list.add(f);
        return list;
    }

    private int findSongPosition(ArrayList<File> list, String path) {
        for (int i = 0; i < list.size(); i++)
            if (list.get(i).getAbsolutePath().equals(path)) return i;
        return 0;
    }

    private boolean isConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo n = cm.getActiveNetworkInfo();
        return n != null && n.isConnected();
    }

    private void openExternally(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse(url));
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "No app found to open link.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showOfflineDialog() {
        new AlertDialog.Builder(this)
                .setTitle("No Internet Connection")
                .setMessage("Please check your connection and restart the app.")
                .setPositiveButton("Exit", (d, w) -> finish())
                .show();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mWebView.saveState(outState);
    }
}