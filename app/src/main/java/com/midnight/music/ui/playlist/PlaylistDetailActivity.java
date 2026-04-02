package com.midnight.music.ui.playlist;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Toast;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.palette.graphics.Palette;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.midnight.music.R;
import com.midnight.music.data.db.AppDatabase;
import com.midnight.music.data.model.PlaylistSongCrossRef;
import com.midnight.music.data.model.PlaylistWithSongs;
import com.midnight.music.data.model.Song;
import com.midnight.music.databinding.ActivityPlaylistDetailBinding;
import com.midnight.music.player.MusicPlayerManager;
import com.midnight.music.ui.player.PlayerActivity;
import com.midnight.music.ui.search.SearchAdapter;
import android.app.ActivityOptions;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import android.content.res.ColorStateList;
import com.midnight.music.utils.AccentManager;

public class PlaylistDetailActivity extends AppCompatActivity implements SearchAdapter.SearchAdapterListener {
    private static final String TAG = "PlaylistDetailActivity";
    private ActivityPlaylistDetailBinding binding;
    private PlaylistDetailViewModel viewModel;
    private SearchAdapter adapter;
    private long playlistId;
    private PlaylistWithSongs currentPlaylist;
    private MusicPlayerManager playerManager;
    private AlertDialog currentPlaylistDialog = null;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlaylistDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Get playlist ID from intent
        playlistId = getIntent().getLongExtra("playlist_id", -1);
        if (playlistId == -1) {
            Toast.makeText(this, "Error: Invalid playlist", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupToolbar();
        setupRecyclerView();
        setupViewModel();
        setupMiniPlayer();
        setupPlayAllButton();
        setupActionButtons();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
    }

    private void setupRecyclerView() {
        adapter = new SearchAdapter(this);
        adapter.setShowTrackNumbers(true); // Midnight Pulse: Show track numbers
        binding.songList.setAdapter(adapter);
        binding.songList.setLayoutManager(new LinearLayoutManager(this));
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this, 
                new PlaylistDetailViewModelFactory(getApplication(), playlistId))
                .get(PlaylistDetailViewModel.class);

        viewModel.getPlaylist().observe(this, this::updateUI);
    }

    private void setupMiniPlayer() {
        playerManager = MusicPlayerManager.getInstance(this);

        // Enable hardware acceleration
        binding.miniPlayer.getRoot().setLayerType(View.LAYER_TYPE_HARDWARE, null);

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

        // Enable marquee scrolling for long song titles
        binding.miniPlayer.txtMiniTitle.setSelected(true);

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
                v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80)
                        .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(80).start())
                        .start();
            } catch (Exception e) {
                Log.e(TAG, "Error toggling play/pause state", e);
                // Revert to correct state if there was an error
                updatePlayPauseButton(playerManager.isPlaying());
            }
        });

        // Heart / Favorite button — toggles liked status in Room DB
        binding.miniPlayer.btnMiniHeart.setOnClickListener(v -> {
            try {
                Song currentSong = playerManager.getCurrentSongLiveData().getValue();
                if (currentSong == null) return;

                v.animate().scaleX(1.3f).scaleY(1.3f).setDuration(100)
                        .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start())
                        .start();

                Executors.newSingleThreadExecutor().execute(() -> {
                    AppDatabase db = AppDatabase.getInstance(this);
                    boolean isCurrentlyLiked = db.songDao().isSongLiked(currentSong.getId());
                    boolean newLikedState = !isCurrentlyLiked;

                    currentSong.setLiked(newLikedState);
                    currentSong.setTimestamp(System.currentTimeMillis());
                    db.songDao().insert(currentSong);

                    mainHandler.post(() -> updateHeartButton(newLikedState));
                });
            } catch (Exception e) {
                Log.e(TAG, "Error toggling favorite", e);
            }
        });

        // Handle skip next button with error handling
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

        // Observe progress with error handling
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

        // Show/hide mini player based on current song
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
        
        // Add direct listener to player for immediate updates
        playerManager.getPlayer().addListener(new androidx.media3.common.Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                // Update on main thread
                mainHandler.post(() -> updatePlayPauseButton(isPlaying));
            }
        });
    }

    private void setupPlayAllButton() {
        binding.fabPlay.setOnClickListener(v -> {
            if (currentPlaylist != null && currentPlaylist.songs != null && !currentPlaylist.songs.isEmpty()) {
                // Play all songs from the playlist from the beginning
                playerManager.playQueue(currentPlaylist.songs, 0);
            } else {
                Toast.makeText(this, "No songs in playlist", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupActionButtons() {
        // ──── Shuffle Button ────
        if (binding.btnShuffle != null) {
            binding.btnShuffle.setOnClickListener(v -> {
                if (currentPlaylist != null && currentPlaylist.songs != null && !currentPlaylist.songs.isEmpty()) {
                    List<Song> shuffled = new ArrayList<>(currentPlaylist.songs);
                    java.util.Collections.shuffle(shuffled);
                    playerManager.playQueue(shuffled, 0);
                } else {
                    Toast.makeText(this, "No songs to shuffle", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // ──── Download All Button ────
        if (binding.btnDownload != null) {
            binding.btnDownload.setOnClickListener(v -> {
                if (currentPlaylist == null || currentPlaylist.songs == null || currentPlaylist.songs.isEmpty()) {
                    Toast.makeText(this, "No songs to download", Toast.LENGTH_SHORT).show();
                    return;
                }

                com.midnight.music.utils.DownloadManager dlManager =
                        com.midnight.music.utils.DownloadManager.getInstance(this);

                // Count how many songs still need downloading
                int alreadyDownloaded = 0;
                List<Song> toDownload = new ArrayList<>();
                for (Song song : currentPlaylist.songs) {
                    if (song.isDownloaded()) {
                        alreadyDownloaded++;
                    } else {
                        toDownload.add(song);
                    }
                }

                if (toDownload.isEmpty()) {
                    return;
                }

                binding.btnDownload.setEnabled(false);
                binding.btnDownload.setVisibility(View.INVISIBLE);
                if (binding.progressDownload != null) {
                    binding.progressDownload.setVisibility(View.VISIBLE);
                }

                final int[] completed = {0};
                final int total = toDownload.size();

                for (Song song : toDownload) {
                    dlManager.downloadSong(song, new com.midnight.music.utils.DownloadManager.DownloadListener() {
                        @Override
                        public void onProgress(int percent) { /* Ignored for batch */ }

                        @Override
                        public void onComplete(String filePath) {
                            completed[0]++;
                            if (completed[0] >= total) {
                                mainHandler.post(() -> {
                                    if (binding.progressDownload != null) {
                                        binding.progressDownload.setVisibility(View.GONE);
                                    }
                                    binding.btnDownload.setVisibility(View.VISIBLE);
                                    binding.btnDownload.setEnabled(true);
                                    Toast.makeText(PlaylistDetailActivity.this,
                                            "All songs downloaded!", Toast.LENGTH_SHORT).show();
                                });
                            }
                        }

                        @Override
                        public void onError(Exception e) {
                            completed[0]++;
                            Log.e(TAG, "Failed to download: " + song.getSong(), e);
                            if (completed[0] >= total) {
                                mainHandler.post(() -> {
                                    if (binding.progressDownload != null) {
                                        binding.progressDownload.setVisibility(View.GONE);
                                    }
                                    binding.btnDownload.setVisibility(View.VISIBLE);
                                    binding.btnDownload.setEnabled(true);
                                    Toast.makeText(PlaylistDetailActivity.this,
                                            "Downloads finished (some may have failed)", Toast.LENGTH_SHORT).show();
                                });
                            }
                        }
                    });
                }
            });
        }

        // ──── More Options (Popup) ────
        if (binding.btnMore != null) {
            binding.btnMore.setOnClickListener(v -> {
                android.widget.PopupMenu popup = new android.widget.PopupMenu(this, v);
                popup.inflate(R.menu.menu_playlist_options);
                popup.setOnMenuItemClickListener(item -> {
                    int id = item.getItemId();
                    if (id == R.id.action_rename_playlist) {
                        showRenameDialog();
                        return true;
                    } else if (id == R.id.action_delete_playlist) {
                        showDeleteConfirmation();
                        return true;
                    }
                    return false;
                });
                popup.show();
            });
        }
    }

    private void showRenameDialog() {
        if (currentPlaylist == null) return;

        android.widget.EditText input = new android.widget.EditText(this);
        input.setText(currentPlaylist.playlist.getName());
        input.setSelectAllOnFocus(true);
        input.setPadding(48, 32, 48, 16);

        new AlertDialog.Builder(this)
                .setTitle("Rename Playlist")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty()) {
                        Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Executors.newSingleThreadExecutor().execute(() -> {
                        AppDatabase.getInstance(this).playlistDao()
                                .updatePlaylistName(playlistId, newName);
                        mainHandler.post(() -> {
                            binding.collapsingToolbar.setTitle(newName);
                            Toast.makeText(this, "Playlist renamed", Toast.LENGTH_SHORT).show();
                        });
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteConfirmation() {
        if (currentPlaylist == null) return;

        new AlertDialog.Builder(this)
                .setTitle("Delete Playlist")
                .setMessage("Are you sure you want to delete \"" + currentPlaylist.playlist.getName() + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        AppDatabase.getInstance(this).playlistDao()
                                .delete(currentPlaylist.playlist);
                        mainHandler.post(() -> {
                            Toast.makeText(this, "Playlist deleted", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateMiniPlayer(Song song) {
        try {
            if (song == null || binding == null || binding.miniPlayer == null) return;

            binding.miniPlayer.txtMiniTitle.setText(song.getSong());
            binding.miniPlayer.txtMiniArtist.setText(song.getSingers());
            
            // Re-enable marquee after text change
            binding.miniPlayer.txtMiniTitle.setSelected(true);

            Glide.with(this)
                .load(song.getImageUrl())
                .placeholder(R.drawable.placeholder_song)
                .into(binding.miniPlayer.imgMiniArt);
                
            // Extract dominant color from album art for mini player tinting
            Glide.with(this)
                    .asBitmap()
                    .load(song.getImageUrl())
                    .into(new SimpleTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(Bitmap bitmap, Transition<? super Bitmap> transition) {
                            try {
                                Palette.from(bitmap).generate(palette -> {
                                    if (palette != null && binding != null && binding.miniPlayer != null) {
                                        // Forward palette to AccentManager for dynamic accent
                                        AccentManager.getInstance(PlaylistDetailActivity.this).updateFromPalette(palette);

                                        int accentColor = AccentManager.getInstance(PlaylistDetailActivity.this).getAccentColorValue();
                                        int mutedColor = palette.getMutedColor(accentColor);

                                        // Tint progress bar with accent color
                                        binding.miniPlayer.progressMini.setIndicatorColor(accentColor);

                                        // Create a frosted glass background blended with album color
                                        int glassBase = Color.parseColor("#141414");
                                        // A simple internal color blend function
                                        int r = (int) (Color.red(glassBase) * 0.88f + Color.red(mutedColor) * 0.12f);
                                        int g = (int) (Color.green(glassBase) * 0.88f + Color.green(mutedColor) * 0.12f);
                                        int b = (int) (Color.blue(glassBase) * 0.88f + Color.blue(mutedColor) * 0.12f);
                                        int bgColor = Color.rgb(r, g, b);

                                        // Apply with alpha for translucency
                                        int glassBg = Color.argb(230, Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor));

                                        GradientDrawable bg = new GradientDrawable();
                                        bg.setShape(GradientDrawable.RECTANGLE);
                                        bg.setCornerRadius(16 * getResources().getDisplayMetrics().density);
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

            // Update play/pause button state
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

    private void updatePlayPauseButton(boolean isPlaying) {
        try {
            // Always run on main thread
            if (Looper.myLooper() != Looper.getMainLooper()) {
                mainHandler.post(() -> updatePlayPauseButton(isPlaying));
                return;
            }
            
            if (binding != null && binding.miniPlayer != null && binding.miniPlayer.btnMiniPlayPause != null) {
                binding.miniPlayer.btnMiniPlayPause.setImageResource(
                    isPlaying ? R.drawable.ic_pause_rounded : R.drawable.ic_play_rounded
                );
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
                int accent = AccentManager.getInstance(this).getAccentColorValue();
                binding.miniPlayer.btnMiniHeart.setImageTintList(
                        ColorStateList.valueOf(
                                isLiked ? accent : Color.parseColor("#88FFFFFF")));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating heart button", e);
        }
    }

    private void updateUI(PlaylistWithSongs playlist) {
        if (playlist == null) return;

        // Store current playlist for later use
        this.currentPlaylist = playlist;

        // Hide More Options (rename/delete) for the special "Liked Songs" playlist
        boolean isLikedSongs = getString(R.string.liked_songs).equals(playlist.playlist.getName());
        if (binding.btnMore != null) {
            binding.btnMore.setVisibility(isLikedSongs ? View.GONE : View.VISIBLE);
        }

        binding.collapsingToolbar.setTitle(playlist.playlist.getName());
        binding.songCount.setText(playlist.getFormattedSongCount());

        // Update songs list
        adapter.submitList(playlist.songs);

        // Show empty state if needed
        if (playlist.songs == null || playlist.songs.isEmpty()) {
            binding.emptyView.setVisibility(View.VISIBLE);
            binding.songList.setVisibility(View.GONE);
        } else {
            binding.emptyView.setVisibility(View.GONE);
            binding.songList.setVisibility(View.VISIBLE);
        }

        // Load playlist image if available
        if (playlist.songs != null && !playlist.songs.isEmpty()) {
            Song firstSong = playlist.songs.get(0);
            Glide.with(this)
                    .load(firstSong.getImageUrl())
                    .placeholder(R.drawable.placeholder_album)
                    .into(binding.playlistImage);
        } else if (getString(R.string.liked_songs).equals(playlist.playlist.getName())) {
            // Special image for Liked Songs
            binding.playlistImage.setImageResource(R.drawable.placeholder_album);
            binding.playlistImage.setBackgroundColor(getResources().getColor(R.color.accent, getTheme()));
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSongClick(Song song) {
        playFromPlaylist(song);
    }

    @Override
    public void onPlayNow(Song song) {
        playFromPlaylist(song);
    }

    /**
     * Play a song and add all other songs from the playlist to the queue
     * @param selectedSong The song that was clicked
     */
    private void playFromPlaylist(Song selectedSong) {
        if (currentPlaylist == null || currentPlaylist.songs == null || currentPlaylist.songs.isEmpty()) {
            // Fallback to just playing the selected song if the playlist is empty or null
            MusicPlayerManager.getInstance(this).playSong(selectedSong);
            return;
        }

        // Find the index of the selected song
        int startIndex = 0;
        for (int i = 0; i < currentPlaylist.songs.size(); i++) {
            if (currentPlaylist.songs.get(i).getId().equals(selectedSong.getId())) {
                startIndex = i;
                break;
            }
        }

        // Play all songs in the playlist, starting from the selected song
        MusicPlayerManager.getInstance(this).playQueue(currentPlaylist.songs, startIndex);
    }

    @Override
    public void onAddToPlaylist(Song song) {
        showPlaylistsDialog(song);
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
        
        // Make sure to remove any existing observers
        playlistsLiveData.removeObservers(this);
        
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
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss());
            
            // Store the dialog and show it
            currentPlaylistDialog = builder.create();
            currentPlaylistDialog.show();
        });
    }

    private void removeSongFromPlaylist(long playlistId, Song song) {
        if (song == null || song.getId() == null) {
            return;
        }
        
        // Use MusicRepository which correctly resets isLiked when removing from Liked Songs
        com.midnight.music.data.repository.MusicRepository.getInstance(this)
                .removeSongFromPlaylist(playlistId, song.getId(), null);
    }

    private void addSongToPlaylist(long playlistId, Song song) {
        if (song == null || song.getId() == null) {
            Toast.makeText(this, "Error: Invalid song data", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Use MusicRepository which preserves isLiked/isDownloaded flags
        com.midnight.music.data.repository.MusicRepository.getInstance(this)
                .addSongToPlaylist(playlistId, song, null);
    }

    @Override
    public void onQueueNext(Song song) {
        MusicPlayerManager.getInstance(this).addToQueue(song);
    }

    @Override
    public void onToggleLike(Song song) {
        if (song == null || song.getId() == null) {
            Toast.makeText(this, "Error: Invalid song data", Toast.LENGTH_SHORT).show();
            return;
        }

        // Toggle the like status in memory
        final boolean newIsLiked = !song.isLiked();
        song.setLiked(newIsLiked);
        
        // Immediately update the UI
        adapter.notifyDataSetChanged();
        
        // Show feedback message
        String message = newIsLiked ? "Added to Liked Songs" : "Removed from Liked Songs";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        
        // Save the change to the database
        viewModel.toggleLike(song);
        
        // If we're currently viewing the special "Liked Songs" collection and 
        // unliking a song, we need to refresh the view to remove that song
        if (!newIsLiked && currentPlaylist != null && 
            currentPlaylist.playlist != null &&
            getString(R.string.liked_songs).equals(currentPlaylist.playlist.getName())) {
                
            // Wait a moment for the database to update then refresh
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                viewModel.loadPlaylist(currentPlaylist.playlist.getId());
            }, 300);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentPlaylistDialog != null && currentPlaylistDialog.isShowing()) {
            currentPlaylistDialog.dismiss();
        }
        mainHandler.removeCallbacksAndMessages(null);
        binding = null;
    }
} 
