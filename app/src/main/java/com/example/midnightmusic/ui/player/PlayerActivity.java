package com.example.midnightmusic.ui.player;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
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
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.example.midnightmusic.R;
import com.example.midnightmusic.data.model.Song;
import com.example.midnightmusic.data.model.Playlist;
import com.example.midnightmusic.data.model.PlaylistWithSongs;
import com.example.midnightmusic.data.model.PlaylistSongCrossRef;
import com.example.midnightmusic.databinding.ActivityPlayerBinding;
import com.example.midnightmusic.player.MusicPlayerManager;
import com.example.midnightmusic.data.db.AppDatabase;
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

public class PlayerActivity extends AppCompatActivity implements QueueAdapter.QueueItemClickListener, MusicPlayerManager.PlayerCallback {
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
    private AlertDialog currentPlaylistDialog = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Set up transitions
        getWindow().setSharedElementEnterTransition(
            TransitionInflater.from(this).inflateTransition(android.R.transition.move)
        );
        getWindow().setSharedElementReturnTransition(
            TransitionInflater.from(this).inflateTransition(android.R.transition.move)
        );
        
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
                // Toggle liked state
                final boolean newLikedStatus = !currentSong.isLiked();
                currentSong.setLiked(newLikedStatus);
                
                // Update UI immediately
                updateLikeButton(newLikedStatus);
                
                // Show feedback
                Toast.makeText(
                    this, 
                    newLikedStatus ? "Added to Liked Songs" : "Removed from Liked Songs", 
                    Toast.LENGTH_SHORT
                ).show();
                
                // Update in database
                AppDatabase db = AppDatabase.getInstance(this);
                
                executor.execute(() -> {
                    try {
                        // Get the Liked Songs playlist
                        Playlist likedSongsPlaylist = db.playlistDao().getPlaylistByNameSync(getString(R.string.liked_songs));
                        
                        // Create it if it doesn't exist
                        if (likedSongsPlaylist == null) {
                            likedSongsPlaylist = new Playlist(getString(R.string.liked_songs));
                            likedSongsPlaylist.setCreatedAt(System.currentTimeMillis());
                            long newPlaylistId = db.playlistDao().insert(likedSongsPlaylist);
                            likedSongsPlaylist.setId(newPlaylistId);
                        }
                        
                        // Update song liked status in the database
                        Song existingSong = db.songDao().getSongByIdSync(currentSong.getId());
                        if (existingSong != null) {
                            // Update the existing song's liked status
                            existingSong.setLiked(newLikedStatus);
                            db.songDao().updateLikedStatus(existingSong.getId(), newLikedStatus);
                        } else {
                            // First time seeing this song, insert it
                            db.songDao().insert(currentSong);
                        }
                        
                        // Add to or remove from Liked Songs playlist
                        if (newLikedStatus) {
                            // Add to Liked Songs playlist
                            PlaylistSongCrossRef crossRef = new PlaylistSongCrossRef(likedSongsPlaylist.getId(), currentSong.getId());
                            db.playlistDao().insert(crossRef);
                        } else {
                            // Remove from Liked Songs playlist
                            db.playlistDao().removeSongFromPlaylist(likedSongsPlaylist.getId(), currentSong.getId());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        new Handler(Looper.getMainLooper()).post(() -> 
                            Toast.makeText(this, 
                                "Error updating like status: " + e.getMessage(), 
                                Toast.LENGTH_SHORT).show()
                        );
                    }
                });
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
        
        // Set initial shuffle and repeat button states
        updateShuffleButton(playerManager.isShuffleEnabled().getValue() != null && 
                           playerManager.isShuffleEnabled().getValue());
        updateRepeatButton(playerManager.getRepeatMode().getValue());
        
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
        if (isQueueVisible) return;
        
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
        if (!isQueueVisible) return;
        
        // Start slide down animation
        slideDownAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}
            
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
            public void onAnimationRepeat(Animation animation) {}
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
        
        // Add a listener to handle playback completion and state changes
        playerManager.getPlayer().addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                // Update UI on the main thread
                runOnUiThread(() -> {
                    // Update play/pause button based on current playing state
                    updatePlayPauseButton(playerManager.isPlaying());
                    
                    // Handle playback completion
                    if (playbackState == Player.STATE_ENDED) {
                        // Automatically play next song when current one ends
                        playerManager.skipToNext();
                    }
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
                    // Make sure play/pause button is updated when song changes
                    updatePlayPauseButton(playerManager.isPlaying());
                });
            }
        });
    }

    private void updatePlayPauseButton(boolean isPlaying) {
        try {
            // Apply animation for smoother transition
            binding.btnPlayPause.animate()
                .alpha(0.5f)
                .setDuration(100)
                .withEndAction(() -> {
                    try {
                        // Change the icon
                        binding.btnPlayPause.setImageResource(
                            isPlaying ? R.drawable.ic_pause : R.drawable.ic_play
                        );
                        
                        // Fade back in
                        binding.btnPlayPause.animate()
                            .alpha(1f)
                            .setDuration(100)
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
                isPlaying ? R.drawable.ic_pause : R.drawable.ic_play
            );
        }
    }
    
    private void updateShuffleButton(boolean isShuffleEnabled) {
        binding.btnShuffle.setImageResource(R.drawable.ic_shuffle);
        binding.btnShuffle.setImageTintList(
            android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this,
                    isShuffleEnabled ? R.color.accent : R.color.gray_light
                )
            )
        );
    }
    
    private void updateRepeatButton(Integer repeatMode) {
        if (repeatMode == null) repeatMode = Player.REPEAT_MODE_OFF;
        
        switch (repeatMode) {
            case Player.REPEAT_MODE_ONE:
                binding.btnRepeat.setImageResource(R.drawable.ic_repeat_one);
                binding.btnRepeat.setImageTintList(
                    android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.accent)
                    )
                );
                break;
            case Player.REPEAT_MODE_ALL:
                binding.btnRepeat.setImageResource(R.drawable.ic_repeat);
                binding.btnRepeat.setImageTintList(
                    android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.accent)
                    )
                );
                break;
            default: // REPEAT_MODE_OFF
                binding.btnRepeat.setImageResource(R.drawable.ic_repeat);
                binding.btnRepeat.setImageTintList(
                    android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.gray_light)
                    )
                );
                break;
        }
    }
    
    private void updateLikeButton(boolean isLiked) {
        binding.btnLike.setImageResource(
            isLiked ? R.drawable.ic_heart_filled : R.drawable.ic_favorite_border
        );
        binding.btnLike.setImageTintList(
            android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this,
                    isLiked ? R.color.accent : R.color.white
                )
            )
        );
    }

    private void updateSongUI(Song song) {
        if (song == null) return;
        
        binding.txtSongName.setText(song.getSong());
        binding.txtArtistName.setText(song.getSingers());
        
        // Update like button
        updateLikeButton(song.isLiked());
        
        // Update queue (current song may have changed)
        updateQueueDisplay();
        
        // Load album art
        Glide.with(this)
            .asBitmap()
            .load(song.getImageUrl())
            .apply(RequestOptions.bitmapTransform(new RoundedCorners(16)))
            .into(binding.imgAlbumArt);

        // Load blurred background
        Glide.with(this)
            .load(song.getImageUrl())
            .transform(new BlurTransformation(25, 3))
            .into(binding.backgroundImage);

        // Extract dominant color for UI accents
        Glide.with(this)
            .asBitmap()
            .load(song.getImageUrl())
            .into(new com.bumptech.glide.request.target.SimpleTarget<Bitmap>() {
                @Override
                public void onResourceReady(Bitmap bitmap, com.bumptech.glide.request.transition.Transition<? super Bitmap> transition) {
                    Palette.from(bitmap).generate(palette -> {
                        if (palette != null) {
                            int dominantColor = palette.getDominantColor(
                                ContextCompat.getColor(PlayerActivity.this, R.color.accent)
                            );
                            binding.seekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(dominantColor));
                            binding.seekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(dominantColor));
                        }
                    });
                }
            });

        // Update duration immediately if available
        long duration = playerManager.getDuration();
        if (duration > 0) {
            updateDuration(duration);
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
        // Play the selected song from the queue
        playerManager.playQueue(playerManager.getQueue(), position);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh the queue when returning to this screen
        updateQueueDisplay();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
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
                // Toggle liked status
                boolean newIsLiked = !currentSong.isLiked();
                currentSong.setLiked(newIsLiked);
                updateLikeButton(newIsLiked);
                
                // Show feedback
                String message = newIsLiked ? "Added to Liked Songs" : "Removed from Liked Songs";
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                
                // Save to database
                toggleLike(currentSong);
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

        // Get the playlists from the database
        AppDatabase db = AppDatabase.getInstance(this);
        
        // Create a new LiveData instance to avoid multiple observers
        LiveData<List<PlaylistWithSongs>> playlistsLiveData = db.playlistDao().getAllPlaylistsWithSongs();
        
        // Use a flag to ensure callback only executes once
        final boolean[] observerCalled = {false};
        
        // Load all playlists
        playlistsLiveData.observe(this, playlists -> {
            // Prevent multiple callbacks
            if (observerCalled[0]) {
                return;
            }
            observerCalled[0] = true;
            
            // Remove observer immediately
            playlistsLiveData.removeObservers(this);
            
            if (playlists == null || playlists.isEmpty()) {
                Toast.makeText(this, "No playlists available", Toast.LENGTH_SHORT).show();
                return;
            }

            String[] playlistNames = new String[playlists.size()];
            long[] playlistIds = new long[playlists.size()];
            boolean[] checkedItems = new boolean[playlists.size()];
            
            for (int i = 0; i < playlists.size(); i++) {
                playlistNames[i] = playlists.get(i).playlist.getName();
                playlistIds[i] = playlists.get(i).playlist.getId();
                
                // Check if the song is already in this playlist
                if (playlists.get(i).songs != null) {
                    for (Song playlistSong : playlists.get(i).songs) {
                        if (playlistSong.getId().equals(song.getId())) {
                            checkedItems[i] = true;
                            break;
                        }
                    }
                }
            }
            
            // Use our custom dialog style with multi-choice items
            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.PlaylistDialogStyle)
                .setTitle(R.string.add_to_playlist)
                .setMultiChoiceItems(playlistNames, checkedItems, (dialog, which, isChecked) -> {
                    // This will be handled in the positive button click
                })
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    // Add the song to all selected playlists
                    for (int i = 0; i < checkedItems.length; i++) {
                        if (checkedItems[i]) {
                            addSongToPlaylist(playlistIds[i], song);
                        } else {
                            // If it was unchecked, remove from this playlist
                            removeSongFromPlaylist(playlistIds[i], song);
                        }
                    }
                    dialog.dismiss();
                    Toast.makeText(this, "Playlists updated", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss());
            
            // Store the dialog and show it
            currentPlaylistDialog = builder.create();
            currentPlaylistDialog.show();
        });
    }
    
    private void addSongToPlaylist(long playlistId, Song song) {
        if (song == null || song.getId() == null) {
            Toast.makeText(this, "Error: Invalid song data", Toast.LENGTH_SHORT).show();
            return;
        }
        
        AppDatabase db = AppDatabase.getInstance(this);
        Executor executor = Executors.newSingleThreadExecutor();
        
        executor.execute(() -> {
            try {
                // Make sure song exists in database with a valid ID
                db.songDao().insert(song);
                
                // Add to playlist with additional null check
                String songId = song.getId();
                if (songId != null && !songId.isEmpty()) {
                    PlaylistSongCrossRef crossRef = new PlaylistSongCrossRef(playlistId, songId);
                    db.playlistDao().insert(crossRef);
                }
            } catch (Exception e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() -> 
                    Toast.makeText(this, 
                        "Error adding to playlist: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show()
                );
            }
        });
    }
    
    private void removeSongFromPlaylist(long playlistId, Song song) {
        if (song == null || song.getId() == null) {
            return;
        }
        
        AppDatabase db = AppDatabase.getInstance(this);
        Executor executor = Executors.newSingleThreadExecutor();
        
        executor.execute(() -> {
            try {
                // Remove from playlist with additional null check
                String songId = song.getId();
                if (songId != null && !songId.isEmpty()) {
                    db.playlistDao().removeSongFromPlaylist(playlistId, songId);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    private void toggleLike(Song song) {
        if (song == null || song.getId() == null) {
            Toast.makeText(this, "Error: Invalid song data", Toast.LENGTH_SHORT).show();
            return;
        }

        final boolean newIsLiked = song.isLiked();
        
        // Update in database
        AppDatabase db = AppDatabase.getInstance(this);
        
        executor.execute(() -> {
            try {
                // Get the Liked Songs playlist
                Playlist likedSongsPlaylist = db.playlistDao().getPlaylistByNameSync(getString(R.string.liked_songs));
                
                // Create it if it doesn't exist
                if (likedSongsPlaylist == null) {
                    likedSongsPlaylist = new Playlist(getString(R.string.liked_songs));
                    likedSongsPlaylist.setCreatedAt(System.currentTimeMillis());
                    long newPlaylistId = db.playlistDao().insert(likedSongsPlaylist);
                    likedSongsPlaylist.setId(newPlaylistId);
                }
                
                // Update song liked status in the database
                Song existingSong = db.songDao().getSongByIdSync(song.getId());
                if (existingSong != null) {
                    // Update the existing song's liked status
                    existingSong.setLiked(newIsLiked);
                    db.songDao().updateLikedStatus(existingSong.getId(), newIsLiked);
                } else {
                    // First time seeing this song, insert it
                    db.songDao().insert(song);
                }
                
                // Add to or remove from Liked Songs playlist
                if (newIsLiked) {
                    // Add to Liked Songs playlist
                    PlaylistSongCrossRef crossRef = new PlaylistSongCrossRef(likedSongsPlaylist.getId(), song.getId());
                    db.playlistDao().insert(crossRef);
                } else {
                    // Remove from Liked Songs playlist
                    db.playlistDao().removeSongFromPlaylist(likedSongsPlaylist.getId(), song.getId());
                }
            } catch (Exception e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() -> 
                    Toast.makeText(this, 
                        "Error updating like status: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show()
                );
            }
        });
    }
} 