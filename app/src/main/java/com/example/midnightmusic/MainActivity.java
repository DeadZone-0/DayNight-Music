package com.example.midnightmusic;

import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.animation.AnimationUtils;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.bumptech.glide.Glide;
import com.example.midnightmusic.data.model.Song;
import com.example.midnightmusic.databinding.ActivityMainBinding;
import com.example.midnightmusic.player.MusicPlayerManager;
import com.example.midnightmusic.ui.player.PlayerActivity;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private ActivityMainBinding binding;
    private MusicPlayerManager playerManager;
    private NavController navController;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        setupNavigation();
        setupMiniPlayer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Force update play state to ensure UI is consistent
        if (playerManager != null) {
            playerManager.forcePlayStateUpdate();
            
            // Also update the mini player if there's a current song
            Song currentSong = playerManager.getCurrentSong();
            if (currentSong != null) {
                updateMiniPlayer(currentSong);
            }
        }
    }

    private void setupNavigation() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        navController = navHostFragment.getNavController();
        NavigationUI.setupWithNavController(binding.bottomNav, navController);
    }

    private void setupMiniPlayer() {
        playerManager = MusicPlayerManager.getInstance(this);

        // Handle click on mini player to open full player with slide up animation
        binding.miniPlayer.miniPlayerContainer.setOnClickListener(v -> {
            Intent intent = new Intent(this, PlayerActivity.class);
            
            // Create transition pairs for shared elements
            Pair<View, String> albumArt = Pair.create(
                binding.miniPlayer.imgMiniArt, 
                "transition_album_art"
            );
            Pair<View, String> songTitle = Pair.create(
                binding.miniPlayer.txtMiniTitle, 
                "transition_song_title"
            );
            Pair<View, String> artistName = Pair.create(
                binding.miniPlayer.txtMiniArtist, 
                "transition_artist_name"
            );
            Pair<View, String> playButton = Pair.create(
                binding.miniPlayer.btnMiniPlayPause,
                "transition_play_button"
            );

            ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(
                this, albumArt, songTitle, artistName, playButton
            );
            
            startActivity(intent, options.toBundle());
            overridePendingTransition(R.anim.slide_up, R.anim.stay);
        });

        // Set initial play/pause button state
        updatePlayPauseButton(playerManager.isPlaying());

        // Handle play/pause button with error handling
        binding.miniPlayer.btnMiniPlayPause.setOnClickListener(v -> {
            try {
                // Update UI immediately for better responsiveness
                boolean isCurrentlyPlaying = playerManager.isPlaying();
                updatePlayPauseButton(!isCurrentlyPlaying);
                
                // Then perform the actual toggle
                playerManager.togglePlayPause();
                
                // Apply click animation
                v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.button_click));
            } catch (Exception e) {
                Log.e(TAG, "Error toggling play/pause state", e);
                // Revert to correct state if there was an error
                updatePlayPauseButton(playerManager.isPlaying());
            }
        });

        // Handle skip previous button with error handling
        binding.miniPlayer.btnMiniPrevious.setOnClickListener(v -> {
            try {
                playerManager.skipToPrevious();
                v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.button_click));
            } catch (Exception e) {
                Log.e(TAG, "Error skipping to previous track", e);
            }
        });

        // Handle skip next button with error handling
        binding.miniPlayer.btnMiniNext.setOnClickListener(v -> {
            try {
                playerManager.skipToNext();
                v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.button_click));
            } catch (Exception e) {
                Log.e(TAG, "Error skipping to next track", e);
            }
        });

        // Observe current song
        playerManager.getCurrentSongLiveData().observe(this, this::updateMiniPlayer);
        
        // Observe playback state
        playerManager.getPlayingLiveData().observe(this, this::updatePlayPauseButton);

        // Observe progress with error handling
        playerManager.getCurrentPosition().observe(this, position -> {
            try {
                if (position != null && playerManager.getDuration() > 0) {
                    int progress = (int) ((position * 100) / playerManager.getDuration());
                    if (binding != null && binding.miniPlayer != null && binding.miniPlayer.progressMini != null) {
                        binding.miniPlayer.progressMini.setProgress(progress);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating progress", e);
            }
        });

        // Show/hide mini player based on current song
        playerManager.getCurrentSongLiveData().observe(this, song -> {
            try {
                if (binding != null && binding.miniPlayer != null) {
                    if (song != null && binding.miniPlayer.getRoot().getVisibility() != View.VISIBLE) {
                        binding.miniPlayer.getRoot().setVisibility(View.VISIBLE);
                        binding.miniPlayer.getRoot().startAnimation(
                            AnimationUtils.loadAnimation(this, R.anim.slide_up)
                        );
                    } else if (song == null) {
                        binding.miniPlayer.getRoot().setVisibility(View.GONE);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating miniplayer visibility", e);
            }
        });
        
        // Add direct listener to player for immediate updates
        playerManager.getPlayer().addListener(new androidx.media3.common.Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                // Update on main thread
                mainHandler.post(() -> updatePlayPauseButton(isPlaying));
            }
        });
    }

    private void updateMiniPlayer(Song song) {
        try {
            if (song == null || binding == null || binding.miniPlayer == null) return;

            binding.miniPlayer.txtMiniTitle.setText(song.getSong());
            binding.miniPlayer.txtMiniArtist.setText(song.getSingers());
            
            Glide.with(this)
                .load(song.getImageUrl())
                .placeholder(R.drawable.placeholder_song)
                .into(binding.miniPlayer.imgMiniArt);

            // Update play/pause button state
            updatePlayPauseButton(playerManager.isPlaying());
        } catch (Exception e) {
            Log.e(TAG, "Error updating mini player", e);
        }
    }

    private void updatePlayPauseButton(boolean isPlaying) {
        try {
            // Always run on main thread
            if (Looper.myLooper() != Looper.getMainLooper()) {
                mainHandler.post(() -> updatePlayPauseButton(isPlaying));
                return;
            }
            
            if (binding != null && binding.miniPlayer != null && binding.miniPlayer.btnMiniPlayPause != null) {
                binding.miniPlayer.btnMiniPlayPause.setImageResource(
                    isPlaying ? R.drawable.ic_pause : R.drawable.ic_play
                );
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating play/pause button", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        binding = null;
    }
} 