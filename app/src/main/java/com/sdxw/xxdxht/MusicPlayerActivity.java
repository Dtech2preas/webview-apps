package com.sdxw.xxdxht;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class MusicPlayerActivity extends Activity {
    private MediaPlayer mediaPlayer;
    private SeekBar seekBar;
    private TextView songTitle, currentTime, totalTime;
    private Button playBtn, prevBtn, nextBtn;
    private Handler handler = new Handler();
    private ArrayList<File> songFiles;
    private int currentPosition;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music_player);
        
        initializeViews();
        setupMusicPlayer();
        setupSeekBar();
    }
    
    private void initializeViews() {
        seekBar = findViewById(R.id.seekBar);
        songTitle = findViewById(R.id.songTitle);
        currentTime = findViewById(R.id.currentTime);
        totalTime = findViewById(R.id.totalTime);
        playBtn = findViewById(R.id.playBtn);
        prevBtn = findViewById(R.id.prevBtn);
        nextBtn = findViewById(R.id.nextBtn);
        
        playBtn.setOnClickListener(v -> togglePlayPause());
        prevBtn.setOnClickListener(v -> playPrevious());
        nextBtn.setOnClickListener(v -> playNext());
    }
    
    private void setupMusicPlayer() {
        Intent intent = getIntent();
        String songPath = intent.getStringExtra("songPath");
        songFiles = (ArrayList<File>) intent.getSerializableExtra("songList");
        currentPosition = intent.getIntExtra("position", 0);
        
        if (songPath != null) {
            playSong(songPath);
        }
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
            
            File file = new File(songPath);
            songTitle.setText(file.getName().replace(".mp3", ""));
            
            updateSeekBar();
            updatePlayPauseButton();
            
            mediaPlayer.setOnCompletionListener(mp -> playNext());
            
        } catch (Exception e) {
            Toast.makeText(this, "Error playing song", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void setupSeekBar() {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer.seekTo(progress);
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }
    
    private void updateSeekBar() {
        if (mediaPlayer != null) {
            seekBar.setMax(mediaPlayer.getDuration());
            
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mediaPlayer != null) {
                        int currentPos = mediaPlayer.getCurrentPosition();
                        seekBar.setProgress(currentPos);
                        
                        // Update time labels
                        currentTime.setText(formatTime(currentPos));
                        totalTime.setText(formatTime(mediaPlayer.getDuration()));
                        
                        handler.postDelayed(this, 1000);
                    }
                }
            }, 1000);
        }
    }
    
    private String formatTime(int milliseconds) {
        return String.format("%02d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(milliseconds),
            TimeUnit.MILLISECONDS.toSeconds(milliseconds) - 
            TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(milliseconds))
        );
    }
    
    private void togglePlayPause() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
            } else {
                mediaPlayer.start();
            }
            updatePlayPauseButton();
        }
    }
    
    private void updatePlayPauseButton() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            playBtn.setText("Pause");
        } else {
            playBtn.setText("Play");
        }
    }
    
    private void playPrevious() {
        if (songFiles != null && songFiles.size() > 0) {
            currentPosition--;
            if (currentPosition < 0) currentPosition = songFiles.size() - 1;
            playSong(songFiles.get(currentPosition).getAbsolutePath());
        }
    }
    
    private void playNext() {
        if (songFiles != null && songFiles.size() > 0) {
            currentPosition++;
            if (currentPosition >= songFiles.size()) currentPosition = 0;
            playSong(songFiles.get(currentPosition).getAbsolutePath());
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        handler.removeCallbacksAndMessages(null);
    }
}