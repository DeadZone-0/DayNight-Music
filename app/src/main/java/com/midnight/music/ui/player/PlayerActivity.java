package com.midnight.music.ui.player;

import android.animation.ObjectAnimator;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.widget.SeekBar;
import android.widget.PopupMenu;
import android.view.MenuItem;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.media3.common.Player;
import androidx.media3.common.MediaItem;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.lifecycle.LiveData;
import com.bumptech.glide.Glide;
import com.midnight.music.R;
import com.midnight.music.data.model.Song;
import com.midnight.music.data.model.Playlist;
import com.midnight.music.data.model.PlaylistWithSongs;
import com.midnight.music.data.model.PlaylistSongCrossRef;
import com.midnight.music.databinding.ActivityPlayerBinding;
import com.midnight.music.player.MusicPlayerManager;
import com.midnight.music.data.db.AppDatabase;
import com.midnight.music.data.repository.MusicRepository;
import jp.wasabeef.glide.transformations.BlurTransformation;
import android.transition.Transition;
import android.transition.TransitionInflater;
import android.widget.Toast;
import android.view.GestureDetector;
import android.view.MotionEvent;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.view.GestureDetectorCompat;
import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;
import android.view.ViewGroup;
import android.util.Log;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class PlayerActivity extends AppCompatActivity
        implements QueueAdapter.QueueItemClickListener, MusicPlayerManager.PlayerCallback {
    private ActivityPlayerBinding binding;
    private MusicPlayerManager playerManager;
    private Handler handler;
    private boolean isUserSeeking = false;
    private static final int UPDATE_INTERVAL = 1000; // 1 second
    private QueueAdapter queueAdapter;
    private Animation slideUpAnimation;
    private Animation slideDownAnimation;
    private boolean isQueueVisible = false;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private static final String TAG = "PlayerActivity";
    private Player.Listener playerListener;
    private AlertDialog currentPlaylistDialog = null;
    private ObjectAnimator vinylAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set up transitions
        getWindow().setSharedElementEnterTransition(
                TransitionInflater.from(this).inflateTransition(android.R.transition.move));
        getWindow().setSharedElementReturnTransition(
                TransitionInflater.from(this).inflateTransition(android.R.transition.move));

        // Postpone the transition until all shared elements are ready
        postponeEnterTransition();

        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        playerManager = MusicPlayerManager.getInstance(this);
        handler = new Handler(Looper.getMainLooper());

        // Load animations
        slideUpAnimation = AnimationUtils.loadAnimation(this, R.anim.slide_up);
        slideDownAnimation = AnimationUtils.loadAnimation(this, R.anim.slide_down);

        setupUI();
        setupQueue();
        observePlayerState();
        startProgressUpdate();

        // Register as player callback to get direct updates
        playerManager.setCallback(this);

        // Start the transition once everything is loaded
        startPostponedEnterTransition();
    }

    private void setupUI() {
        // Back button
        binding.btnClose.setOnClickListener(v -> onBackPressed());

        // Options button (three dots menu)
        binding.btnOptions.setOnClickListener(v -> {
            showOptionsMenu(v);
        });

        // Add to Playlist button
        binding.btnAddToPlaylist.setOnClickListener(v -> {
            Song currentSong = playerManager.getCurrentSong();
            if (currentSong != null) {
                showPlaylistsDialog(currentSong);
            } else {
                Toast.makeText(this, "No song is currently playing", Toast.LENGTH_SHORT).show();
            }
        });

        // Play/Pause button with immediate visual feedback
        binding.btnPlayPause.setOnClickListener(v -> {
            try {
                // Toggle play state
                boolean isCurrentlyPlaying = playerManager.isPlaying();

                // Update UI immediately for better responsiveness
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

        // Skip buttons with click animation
        binding.btnPrevious.setOnClickListener(v -> {
            try {
                playerManager.skipToPrevious();
                v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.button_click));
            } catch (Exception e) {
                Log.e(TAG, "Error skipping to previous track", e);
            }
        });

        binding.btnNext.setOnClickListener(v -> {
            try {
                playerManager.skipToNext();
                v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.button_click));
            } catch (Exception e) {
                Log.e(TAG, "Error skipping to next track", e);
            }
        });

        // Shuffle button
        binding.btnShuffle.setOnClickListener(v -> playerManager.toggleShuffle());

        // Repeat button
        binding.btnRepeat.setOnClickListener(v -> playerManager.toggleRepeatMode());

        // Like button
        binding.btnLike.setOnClickListener(v -> {
            Song currentSong = playerManager.getCurrentSong();
            if (currentSong != null) {
                MusicRepository.getInstance(this).toggleLikeSong(currentSong, new MusicRepository.LikeCallback() {
                    @Override
                    public void onComplete(boolean isLiked) {
                        runOnUiThread(() -> {
                            updateLikeButton(isLiked);
                            Toast.makeText(PlayerActivity.this,
                                    isLiked ? "Added to Liked Songs" : "Removed from Liked Songs",
                                    Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        runOnUiThread(() -> Toast.makeText(PlayerActivity.this,
                                "Error updating like status: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
                    }
                });
            }
        });

        // Download button
        binding.btnDownload.setOnClickListener(v -> {
            Song currentSong = playerManager.getCurrentSong();
            if (currentSong == null) {
                Toast.makeText(this, "No song is currently playing", Toast.LENGTH_SHORT).show();
                return;
            }

            if (currentSong.isDownloaded()) {
                Toast.makeText(this, "Already downloaded", Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(this, "Downloading...", Toast.LENGTH_SHORT).show();
            binding.btnDownload.setEnabled(false);

            com.midnight.music.utils.DownloadManager.getInstance(this)
                    .downloadSong(currentSong, new com.midnight.music.utils.DownloadManager.DownloadListener() {
                        @Override
                        public void onProgress(int percent) {
                            // Could update a progress bar here
                        }

                        @Override
                        public void onComplete(String filePath) {
                            currentSong.setDownloaded(true);
                            currentSong.setLocalPath(filePath);
                            binding.btnDownload.setImageResource(R.drawable.ic_download_done);
                            binding.btnDownload.setEnabled(true);
                            Toast.makeText(PlayerActivity.this, "Downloaded!", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(Exception e) {
                            binding.btnDownload.setEnabled(true);
                            Toast.makeText(PlayerActivity.this,
                                    "Download failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        // Similar Songs button â€” toggle auto-queue recommendations
        binding.btnSimilarSongs.setOnClickListener(v -> {
            Song currentSong = playerManager.getCurrentSong();
            if (currentSong == null) {
                Toast.makeText(this, "No song playing", Toast.LENGTH_SHORT).show();
                return;
            }

            boolean isAutoQueue = playerManager.isAutoQueueEnabled();
            if (!isAutoQueue) {
                // Enable auto-queue and immediately fetch
                playerManager.setAutoQueueEnabled(true);
                binding.btnSimilarSongs.setColorFilter(
                        getResources().getColor(R.color.colorAccent, getTheme()));
                Toast.makeText(this, "Adding similar songs to queueâ€¦", Toast.LENGTH_SHORT).show();

                // Trigger an immediate fetch
                playerManager.fetchSimilarForQueue(currentSong);
            } else {
                // Disable auto-queue
                playerManager.setAutoQueueEnabled(false);
                binding.btnSimilarSongs.setColorFilter(
                        getResources().getColor(R.color.white, getTheme()));
                Toast.makeText(this, "Similar songs disabled", Toast.LENGTH_SHORT).show();
            }
        });

        binding.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    binding.txtCurrentTime.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                playerManager.seekTo(seekBar.getProgress());
            }
        });

        // Set initial play/pause button state
        updatePlayPauseButton(playerManager.isPlaying());

        // Enable marquee for long song titles
        binding.txtSongName.setSelected(true);

        // Set initial shuffle and repeat button states
        updateShuffleButton(playerManager.isShuffleEnabled().getValue() != null &&
                playerManager.isShuffleEnabled().getValue());
        updateRepeatButton(playerManager.getRepeatMode().getValue());

        // Set initial tint for similar songs button
        if (playerManager.isAutoQueueEnabled()) {
            binding.btnSimilarSongs.setColorFilter(
                    getResources().getColor(R.color.colorAccent, getTheme()));
        }

        // Set transition names for shared elements
        binding.imgAlbumArt.setTransitionName("transition_album_art");
        binding.txtSongName.setTransitionName("transition_song_title");
        binding.txtArtistName.setTransitionName("transition_artist_name");
        binding.btnPlayPause.setTransitionName("transition_play_button");

        // Setup queue button
        binding.btnShowQueue.setOnClickListener(v -> showQueue());

        // Setup close queue button
        binding.btnCloseQueue.setOnClickListener(v -> hideQueue());

        // Setup drag handle for closing
        binding.dragHandle.setOnClickListener(v -> hideQueue());
    }

    private void setupQueue() {
        queueAdapter = new QueueAdapter(this);
        binding.queueRecyclerView.setAdapter(queueAdapter);
        binding.queueRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Initial queue state
        updateQueueDisplay();
    }

    private void updateQueueDisplay() {
        List<Song> queue = playerManager.getQueue();
        queueAdapter.submitList(queue);
    }

    private void showQueue() {
        if (isQueueVisible)
            return;

        // Make sure queue is updated
        updateQueueDisplay();

        // Show the queue container
        binding.queueContainer.setVisibility(View.VISIBLE);
        binding.queueContainer.startAnimation(slideUpAnimation);

        // Hide the queue button with fade out
        binding.btnShowQueue.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> binding.btnShowQueue.setVisibility(View.GONE))
                .start();

        isQueueVisible = true;
    }

    private void hideQueue() {
        if (!isQueueVisible)
            return;

        // Start slide down animation
        slideDownAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                binding.queueContainer.setVisibility(View.GONE);

                // Show the queue button again with fade in
                binding.btnShowQueue.setVisibility(View.VISIBLE);
                binding.btnShowQueue.setAlpha(0f);
                binding.btnShowQueue.animate()
                        .alpha(1f)
                        .setDuration(200)
                        .start();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });

        binding.queueContainer.startAnimation(slideDownAnimation);
        isQueueVisible = false;
    }

    private void observePlayerState() {
        // Observe current song
        playerManager.getCurrentSongLiveData().observe(this, this::updateSongUI);

        // Observe playback state
        playerManager.getPlayingLiveData().observe(this, this::updatePlayPauseButton);

        // Observe shuffle state
        playerManager.isShuffleEnabled().observe(this, this::updateShuffleButton);

        // Observe repeat mode
        playerManager.getRepeatMode().observe(this, this::updateRepeatButton);

        // Observe player position updates
        playerManager.getCurrentPosition().observe(this, position -> {
            if (!isUserSeeking && position != null) {
                binding.seekBar.setProgress(position.intValue());
                binding.txtCurrentTime.setText(formatTime(position));
            }
        });

        // Add a listener for UI updates only â€” auto-next is handled centrally
        // by MusicPlayerManager's own STATE_ENDED handler.
        playerListener = new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                // Update UI on the main thread (no skipToNext â€” handled by MusicPlayerManager)
                runOnUiThread(() -> {
                    updatePlayPauseButton(playerManager.isPlaying());
                });
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                // Update UI on the main thread
                runOnUiThread(() -> {
                    updatePlayPauseButton(isPlaying);
                });
            }

            @Override
            public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                // Update UI on the main thread
                runOnUiThread(() -> {
                    updatePlayPauseButton(playerManager.isPlaying());
                });
            }
        };
        playerManager.getPlayer().addListener(playerListener);
    }

    private void updatePlayPauseButton(boolean isPlaying) {
        try {
            // Sync vinyl disc animation with play state
            updateVinylAnimation(isPlaying);

            // Apply animation for smoother transition
            binding.btnPlayPause.animate()
                    .scaleX(0.85f)
                    .scaleY(0.85f)
                    .setDuration(80)
                    .withEndAction(() -> {
                        try {
                            // Change the icon â€” use rounded icons
                            binding.btnPlayPause.setImageResource(
                                    isPlaying ? R.drawable.ic_pause_rounded : R.drawable.ic_play_rounded);

                            // Scale back up with overshoot
                            binding.btnPlayPause.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(150)
                                    .setInterpolator(new android.view.animation.OvershootInterpolator(2f))
                                    .start();
                        } catch (Exception e) {
                            Log.e(TAG, "Error updating play/pause button animation", e);
                        }
                    })
                    .start();
        } catch (Exception e) {
            Log.e(TAG, "Error in updatePlayPauseButton", e);
            // Fallback without animation
            binding.btnPlayPause.setImageResource(
                    isPlaying ? R.drawable.ic_pause_rounded : R.drawable.ic_play_rounded);
        }
    }

    private void updateShuffleButton(boolean isShuffleEnabled) {
        binding.btnShuffle.setImageResource(R.drawable.ic_shuffle_rounded);
        binding.btnShuffle.setImageTintList(
                android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this,
                                isShuffleEnabled ? R.color.accent : R.color.gray_light)));
    }

    private void updateRepeatButton(Integer repeatMode) {
        if (repeatMode == null)
            repeatMode = Player.REPEAT_MODE_OFF;

        switch (repeatMode) {
            case Player.REPEAT_MODE_ONE:
                binding.btnRepeat.setImageResource(R.drawable.ic_repeat_one_rounded);
                binding.btnRepeat.setImageTintList(
                        android.content.res.ColorStateList.valueOf(
                                ContextCompat.getColor(this, R.color.accent)));
                break;
            case Player.REPEAT_MODE_ALL:
                binding.btnRepeat.setImageResource(R.drawable.ic_repeat_rounded);
                binding.btnRepeat.setImageTintList(
                        android.content.res.ColorStateList.valueOf(
                                ContextCompat.getColor(this, R.color.accent)));
                break;
            default: // REPEAT_MODE_OFF
                binding.btnRepeat.setImageResource(R.drawable.ic_repeat_rounded);
                binding.btnRepeat.setImageTintList(
                        android.content.res.ColorStateList.valueOf(
                                ContextCompat.getColor(this, R.color.gray_light)));
                break;
        }
    }

    private void updateLikeButton(boolean isLiked) {
        binding.btnLike.setImageResource(
                isLiked ? R.drawable.ic_heart_filled : R.drawable.ic_favorite_border);
        binding.btnLike.setImageTintList(
                android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this,
                                isLiked ? R.color.accent : R.color.white)));
    }

    private void updateSongUI(Song song) {
        if (song == null)
            return;

        binding.txtSongName.setText(song.getSong());
        binding.txtArtistName.setText(song.getSingers());

        // Update like button
        updateLikeButton(song.isLiked());

        // Update queue (current song may have changed)
        updateQueueDisplay();

        // Load album art (no RoundedCorners needed â€” ShapeableImageView handles it)
        Glide.with(this)
                .asBitmap()
                .load(song.getImageUrl())
                .into(binding.imgAlbumArt);

        // Load blurred background
        Glide.with(this)
                .load(song.getImageUrl())
                .transform(new BlurTransformation(25, 3))
                .into(binding.backgroundImage);

        // Extract dominant color for seekbar accent tint
        Glide.with(this)
                .asBitmap()
                .load(song.getImageUrl())
                .into(new com.bumptech.glide.request.target.SimpleTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(Bitmap bitmap,
                            com.bumptech.glide.request.transition.Transition<? super Bitmap> transition) {
                        Palette.from(bitmap).generate(palette -> {
                            if (palette != null) {
                                int dominantColor = palette.getDominantColor(
                                        ContextCompat.getColor(PlayerActivity.this, R.color.white));
                                // Tint the seekbar progress layer
                                binding.seekBar
                                        .setProgressTintList(android.content.res.ColorStateList.valueOf(dominantColor));
                                binding.seekBar
                                        .setThumbTintList(android.content.res.ColorStateList.valueOf(dominantColor));
                            }
                        });
                    }
                });

        // Start vinyl disc continuous rotation
        startVinylRotation();

        // Update duration immediately if available
        long duration = playerManager.getDuration();
        if (duration > 0) {
            updateDuration(duration);
        }
    }

    private void startVinylRotation() {
        if (binding.imgVinylDisc == null) return;

        // Cancel any existing animation
        if (vinylAnimator != null) {
            vinylAnimator.cancel();
        }

        vinylAnimator = ObjectAnimator.ofFloat(binding.imgVinylDisc, "rotation", 0f, 360f);
        vinylAnimator.setDuration(8000);
        vinylAnimator.setRepeatCount(ValueAnimator.INFINITE);
        vinylAnimator.setInterpolator(new LinearInterpolator());

        if (playerManager.isPlaying()) {
            vinylAnimator.start();
        }
    }

    private void updateVinylAnimation(boolean isPlaying) {
        if (vinylAnimator == null) return;

        if (isPlaying) {
            if (vinylAnimator.isPaused()) {
                vinylAnimator.resume();
            } else if (!vinylAnimator.isRunning()) {
                vinylAnimator.start();
            }
        } else {
            if (vinylAnimator.isRunning()) {
                vinylAnimator.pause();
            }
        }
    }

    private void updateDuration(long duration) {
        binding.seekBar.setMax((int) duration);
        binding.txtTotalTime.setText(formatTime(duration));
    }

    private void startProgressUpdate() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!isUserSeeking && playerManager != null) {
                    long position = playerManager.getPlayer().getCurrentPosition();
                    binding.seekBar.setProgress((int) position);
                    binding.txtCurrentTime.setText(formatTime(position));

                    // Update duration if it wasn't available before
                    long duration = playerManager.getDuration();
                    if (duration > 0 && binding.seekBar.getMax() != duration) {
                        updateDuration(duration);
                    }
                }
                handler.postDelayed(this, UPDATE_INTERVAL);
            }
        });
    }

    private String formatTime(long timeMs) {
        long seconds = (timeMs / 1000) % 60;
        long minutes = (timeMs / (1000 * 60)) % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    @Override
    public void onQueueItemClick(Song song, int position) {
        // Jump to the selected position without resetting auto-queue
        playerManager.playAtIndex(position);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh the queue when returning to this screen
        updateQueueDisplay();

        // Sync wand button tint with auto-queue state
        if (playerManager.isAutoQueueEnabled()) {
            binding.btnSimilarSongs.setColorFilter(
                    getResources().getColor(R.color.colorAccent, getTheme()));
        } else {
            binding.btnSimilarSongs.setColorFilter(
                    getResources().getColor(R.color.white, getTheme()));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        // Stop vinyl animation
        if (vinylAnimator != null) {
            vinylAnimator.cancel();
            vinylAnimator = null;
        }
        // Remove the player listener to prevent listener accumulation
        if (playerListener != null) {
            playerManager.getPlayer().removeListener(playerListener);
            playerListener = null;
        }
        // Remove ourselves as the callback to avoid leaks
        playerManager.setCallback(null);
        // Dismiss dialog if showing
        if (currentPlaylistDialog != null && currentPlaylistDialog.isShowing()) {
            currentPlaylistDialog.dismiss();
        }
        binding = null;
    }

    @Override
    public void onBackPressed() {
        if (isQueueVisible) {
            hideQueue();
        } else {
            super.onBackPressed();
        }
    }

    // Implement PlayerCallback methods
    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        // This is called directly from the player manager
        runOnUiThread(() -> updatePlayPauseButton(isPlaying));
    }

    @Override
    public void onSongChanged(Song song) {
        // This is called directly from the player manager
        runOnUiThread(() -> {
            updateSongUI(song);
            // Make sure play state is updated when song changes
            updatePlayPauseButton(playerManager.isPlaying());
            // Update download button icon
            if (song != null && song.isDownloaded()) {
                binding.btnDownload.setImageResource(R.drawable.ic_download_done);
            } else {
                binding.btnDownload.setImageResource(R.drawable.ic_download_arrow);
            }
        });
    }

    private void showOptionsMenu(View view) {
        PopupMenu popup = new PopupMenu(this, view);
        popup.getMenuInflater().inflate(R.menu.menu_song_options, popup.getMenu());

        // Hide the "Play Now" option since we're already playing
        MenuItem playNowItem = popup.getMenu().findItem(R.id.action_play_now);
        if (playNowItem != null) {
            playNowItem.setVisible(false);
        }

        popup.setOnMenuItemClickListener(item -> {
            Song currentSong = playerManager.getCurrentSong();
            if (currentSong == null) {
                Toast.makeText(this, "No song is currently playing", Toast.LENGTH_SHORT).show();
                return true;
            }

            int itemId = item.getItemId();
            if (itemId == R.id.action_add_to_playlist) {
                showPlaylistsDialog(currentSong);
                return true;
            } else if (itemId == R.id.action_queue_next) {
                playerManager.queueNext(currentSong);
                Toast.makeText(this, "Added to queue", Toast.LENGTH_SHORT).show();
                return true;
            } else if (itemId == R.id.action_like) {
                MusicRepository.getInstance(this).toggleLikeSong(currentSong, new MusicRepository.LikeCallback() {
                    @Override
                    public void onComplete(boolean isLiked) {
                        runOnUiThread(() -> {
                            updateLikeButton(isLiked);
                            Toast.makeText(PlayerActivity.this,
                                    isLiked ? "Added to Liked Songs" : "Removed from Liked Songs",
                                    Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        runOnUiThread(() -> Toast.makeText(PlayerActivity.this,
                                "Error updating like status: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
                    }
                });
                return true;
            }
            return false;
        });

        popup.show();
    }

    private void showPlaylistsDialog(Song song) {
        // If a dialog is already showing, dismiss it first
        if (currentPlaylistDialog != null && currentPlaylistDialog.isShowing()) {
            currentPlaylistDialog.dismiss();
        }

        executor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            List<Playlist> playlists = db.playlistDao().getAllPlaylistsSync();

            runOnUiThread(() -> {
                if (playlists == null || playlists.isEmpty()) {
                    Toast.makeText(this, "No playlists available", Toast.LENGTH_SHORT).show();
                    return;
                }

                String[] playlistNames = new String[playlists.size()];
                long[] playlistIds = new long[playlists.size()];
                boolean[] checkedItems = new boolean[playlists.size()];

                executor.execute(() -> {
                    for (int i = 0; i < playlists.size(); i++) {
                        Playlist p = playlists.get(i);
                        playlistNames[i] = p.getName();
                        playlistIds[i] = p.getId();
                        checkedItems[i] = db.playlistDao().isSongInPlaylistSync(p.getId(), song.getId());
                    }

                    runOnUiThread(() -> {
                        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.PlaylistDialogStyle)
                                .setTitle(R.string.add_to_playlist)
                                .setMultiChoiceItems(playlistNames, checkedItems, (dialog, which, isChecked) -> {
                                    // Handled in positive button click
                                })
                                .setPositiveButton(R.string.save, (dialog, which) -> {
                                    com.midnight.music.data.repository.MusicRepository repo = com.midnight.music.data.repository.MusicRepository.getInstance(this);
                                    for (int i = 0; i < checkedItems.length; i++) {
                                        if (checkedItems[i]) {
                                            repo.addSongToPlaylist(playlistIds[i], song, null);
                                        } else {
                                            repo.removeSongFromPlaylist(playlistIds[i], song.getId(), null);
                                        }
                                    }
                                    dialog.dismiss();
                                    Toast.makeText(this, "Playlists updated", Toast.LENGTH_SHORT).show();
                                })
                                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss());

                        currentPlaylistDialog = builder.create();
                        currentPlaylistDialog.show();
                    });
                });
            });
        });
    }


}
