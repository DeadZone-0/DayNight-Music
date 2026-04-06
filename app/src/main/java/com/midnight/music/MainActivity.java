package com.midnight.music;

import android.Manifest;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.pm.PackageManager;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.view.View;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import androidx.palette.graphics.Palette;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.midnight.music.data.auth.SessionManager;
import com.midnight.music.data.db.AppDatabase;
import com.midnight.music.data.model.Song;
import com.midnight.music.data.repository.CloudSyncWorker;
import com.midnight.music.databinding.ActivityMainBinding;
import com.midnight.music.player.MusicPlayerManager;
import com.midnight.music.ui.player.PlayerActivity;
import com.midnight.music.utils.ThemeManager;

import android.content.res.ColorStateList;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1001;
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
        requestNotificationPermission();
        scheduleCloudSync();

        // Observe accent colour changes and tint bottom nav + other elements
        ThemeManager.getInstance(this).getAccentColor().observe(this, this::applyAccentColor);

        // Check for updates seamlessly on startup
        com.midnight.music.utils.UpdateManager updateManager = new com.midnight.music.utils.UpdateManager(this);
        updateManager.checkForUpdates(false);
    }

    /**
     * Request POST_NOTIFICATIONS permission on Android 13+ (API 33).
     * Without this, the media notification will not appear.
     */
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Notification permission granted");
            } else {
                Log.w(TAG, "Notification permission denied â€” media controls won't show in notifications");
            }
        }
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

        // Enable hardware acceleration on the mini player for smooth 60fps rendering
        binding.miniPlayer.getRoot().setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // Handle click on mini player to open full player with shared-element transition
        binding.miniPlayer.miniPlayerContainer.setOnClickListener(v -> {
            Intent intent = new Intent(this, PlayerActivity.class);

            Pair<View, String> albumArt = Pair.create(
                    binding.miniPlayer.imgMiniArt,
                    "transition_album_art");
            Pair<View, String> songTitle = Pair.create(
                    binding.miniPlayer.txtMiniTitle,
                    "transition_song_title");
            Pair<View, String> artistName = Pair.create(
                    binding.miniPlayer.txtMiniArtist,
                    "transition_artist_name");
            Pair<View, String> playButton = Pair.create(
                    binding.miniPlayer.btnMiniPlayPause,
                    "transition_play_button");

            ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(
                    this, albumArt, songTitle, artistName, playButton);

            startActivity(intent, options.toBundle());
            overridePendingTransition(R.anim.slide_up, R.anim.stay);
        });

        // Enable marquee scrolling for long song titles
        binding.miniPlayer.txtMiniTitle.setSelected(true);

        // Set initial play/pause button state
        updatePlayPauseButton(playerManager.isPlaying());

        // Play/Pause button
        binding.miniPlayer.btnMiniPlayPause.setOnClickListener(v -> {
            try {
                boolean isCurrentlyPlaying = playerManager.isPlaying();
                updatePlayPauseButton(!isCurrentlyPlaying);
                playerManager.togglePlayPause();
                v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80)
                        .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(80).start())
                        .start();
            } catch (Exception e) {
                Log.e(TAG, "Error toggling play/pause state", e);
                updatePlayPauseButton(playerManager.isPlaying());
            }
        });

        // Heart / Favorite button — toggles liked status in Room DB
        binding.miniPlayer.btnMiniHeart.setOnClickListener(v -> {
            try {
                Song currentSong = playerManager.getCurrentSongLiveData().getValue();
                if (currentSong == null) return;

                // Pulse animation for the heart
                v.animate().scaleX(1.3f).scaleY(1.3f).setDuration(100)
                        .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start())
                        .start();

                Executors.newSingleThreadExecutor().execute(() -> {
                    AppDatabase db = AppDatabase.getInstance(this);
                    boolean isCurrentlyLiked = db.songDao().isSongLiked(currentSong.getId());
                    boolean newLikedState = !isCurrentlyLiked;

                    // Ensure the song exists in DB first
                    currentSong.setLiked(newLikedState);
                    currentSong.setTimestamp(System.currentTimeMillis());
                    db.songDao().insert(currentSong);

                    mainHandler.post(() -> updateHeartButton(newLikedState));
                });
            } catch (Exception e) {
                Log.e(TAG, "Error toggling favorite", e);
            }
        });

        // Next button
        binding.miniPlayer.btnMiniNext.setOnClickListener(v -> {
            try {
                playerManager.skipToNext();
                v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80)
                        .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(80).start())
                        .start();
            } catch (Exception e) {
                Log.e(TAG, "Error skipping to next track", e);
            }
        });

        // Observe current song
        playerManager.getCurrentSongLiveData().observe(this, this::updateMiniPlayer);

        // Observe playback state
        playerManager.getPlayingLiveData().observe(this, this::updatePlayPauseButton);

        // Observe progress — smooth progress updates
        playerManager.getCurrentPosition().observe(this, position -> {
            try {
                if (position != null && playerManager.getDuration() > 0) {
                    int progress = (int) ((position * 100) / playerManager.getDuration());
                    if (binding != null && binding.miniPlayer != null && binding.miniPlayer.progressMini != null) {
                        binding.miniPlayer.progressMini.setProgressCompat(progress, true);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating progress", e);
            }
        });

        // Show/hide mini player — smooth alpha fade instead of jerky slide
        playerManager.getCurrentSongLiveData().observe(this, song -> {
            try {
                if (binding != null && binding.miniPlayer != null) {
                    View root = binding.miniPlayer.getRoot();
                    if (song != null && root.getVisibility() != View.VISIBLE) {
                        root.setAlpha(0f);
                        root.setTranslationY(root.getHeight() > 0 ? root.getHeight() : 80);
                        root.setVisibility(View.VISIBLE);
                        root.animate()
                                .alpha(1f)
                                .translationY(0f)
                                .setDuration(250)
                                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                                .start();
                    } else if (song == null && root.getVisibility() == View.VISIBLE) {
                        root.animate()
                                .alpha(0f)
                                .translationY(80)
                                .setDuration(200)
                                .withEndAction(() -> root.setVisibility(View.GONE))
                                .start();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating miniplayer visibility", e);
            }
        });

        // Direct player listener for immediate play-state synchronization
        playerManager.getPlayer().addListener(new androidx.media3.common.Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                mainHandler.post(() -> updatePlayPauseButton(isPlaying));
            }
        });
    }

    private void updateMiniPlayer(Song song) {
        try {
            if (song == null || binding == null || binding.miniPlayer == null)
                return;

            binding.miniPlayer.txtMiniTitle.setText(song.getSong());
            binding.miniPlayer.txtMiniArtist.setText(song.getSingers());

            // Re-enable marquee after text change
            binding.miniPlayer.txtMiniTitle.setSelected(true);

            Glide.with(this)
                    .load(song.getImageUrl())
                    .placeholder(R.drawable.placeholder_song)
                    .into(binding.miniPlayer.imgMiniArt);

            // Extract palette for glassy color tinting
            Glide.with(this)
                    .asBitmap()
                    .load(song.getImageUrl())
                    .into(new SimpleTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(Bitmap bitmap, Transition<? super Bitmap> transition) {
                            try {
                                Palette.from(bitmap).generate(palette -> {
                                    if (palette != null && binding != null && binding.miniPlayer != null) {
                                        // Forward palette to ThemeManager for dynamic accent
                                        ThemeManager.getInstance(MainActivity.this).updateFromPalette(palette);

                                        int accentColor = ThemeManager.getInstance(MainActivity.this).getAccentColorValue();
                                        int mutedColor = palette.getMutedColor(accentColor);

                                        // Tint the progress bar with accent color
                                        binding.miniPlayer.progressMini.setIndicatorColor(accentColor);

                                        // Create a frosted glass background blended with album color
                                        int glassBase = Color.parseColor("#141414");
                                        int bgColor = blendColors(glassBase, mutedColor, 0.12f);
                                        // Apply with alpha for translucency
                                        int glassBg = Color.argb(230, Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor));

                                        GradientDrawable bg = new GradientDrawable();
                                        bg.setShape(GradientDrawable.RECTANGLE);
                                        bg.setCornerRadius(dpToPx(16));
                                        bg.setColor(glassBg);
                                        bg.setStroke(1, Color.argb(24, 255, 255, 255));
                                        binding.miniPlayer.miniPlayerContainer.setBackground(bg);
                                    }
                                });
                            } catch (Exception e) {
                                Log.e(TAG, "Error extracting palette for mini player", e);
                            }
                        }
                    });

            updatePlayPauseButton(playerManager.isPlaying());

            // Check liked status for the heart button
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    boolean liked = AppDatabase.getInstance(this).songDao().isSongLiked(song.getId());
                    mainHandler.post(() -> updateHeartButton(liked));
                } catch (Exception e) {
                    Log.e(TAG, "Error checking liked status", e);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error updating mini player", e);
        }
    }

    private int blendColors(int color1, int color2, float ratio) {
        float inverseRatio = 1f - ratio;
        int r = (int) (Color.red(color1) * inverseRatio + Color.red(color2) * ratio);
        int g = (int) (Color.green(color1) * inverseRatio + Color.green(color2) * ratio);
        int b = (int) (Color.blue(color1) * inverseRatio + Color.blue(color2) * ratio);
        return Color.rgb(r, g, b);
    }

    private float dpToPx(int dp) {
        return dp * getResources().getDisplayMetrics().density;
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
                        isPlaying ? R.drawable.ic_pause_rounded : R.drawable.ic_play_rounded);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating play/pause button", e);
        }
    }

    private void updateHeartButton(boolean isLiked) {
        try {
            if (binding != null && binding.miniPlayer != null && binding.miniPlayer.btnMiniHeart != null) {
                binding.miniPlayer.btnMiniHeart.setImageResource(
                        isLiked ? R.drawable.ic_heart_filled : R.drawable.ic_favorite_border);
                // Tint: liked = accent color, unliked = muted white
                int accent = ThemeManager.getInstance(this).getAccentColorValue();
                binding.miniPlayer.btnMiniHeart.setImageTintList(
                        ColorStateList.valueOf(
                                isLiked ? accent : Color.parseColor("#88FFFFFF")));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating heart button", e);
        }
    }

    private void applyAccentColor(int color) {
        try {
            if (binding == null) return;
            // Bottom nav icon tint
            binding.bottomNav.setItemIconTintList(ColorStateList.valueOf(color));
            // Progress bar
            if (binding.miniPlayer != null && binding.miniPlayer.progressMini != null) {
                binding.miniPlayer.progressMini.setIndicatorColor(color);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying accent color", e);
        }
    }

    /**
     * Schedule periodic cloud sync if the user is logged in.
     */
    private void scheduleCloudSync() {
        if (SessionManager.getInstance(this).isLoggedIn()) {
            CloudSyncWorker.schedulePeriodicSync(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        binding = null;
    }
}
