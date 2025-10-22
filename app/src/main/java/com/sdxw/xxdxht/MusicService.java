package com.sdxw.xxdxht;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.widget.Toast;
import androidx.annotation.Nullable;
import java.io.IOException;

public class MusicService extends Service {
    private MediaPlayer mediaPlayer;
    private String currentSongPath;
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getStringExtra("action");
            String songPath = intent.getStringExtra("songPath");
            
            if ("play".equals(action) && songPath != null) {
                playSong(songPath);
            } else if ("pause".equals(action)) {
                pauseSong();
            } else if ("stop".equals(action)) {
                stopSong();
            }
        }
        return START_STICKY;
    }
    
    private void playSong(String songPath) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.release();
            }
            
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(songPath);
            mediaPlayer.prepare();
            mediaPlayer.start();
            currentSongPath = songPath;
            
        } catch (IOException e) {
            Toast.makeText(this, "Error playing song", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void pauseSong() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }
    
    private void stopSong() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopSong();
    }
}