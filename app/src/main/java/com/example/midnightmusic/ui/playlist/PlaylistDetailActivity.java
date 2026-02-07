package com.example.midnightmusic.ui.playlist;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.midnightmusic.R;
import com.example.midnightmusic.data.db.AppDatabase;
import com.example.midnightmusic.data.model.PlaylistSongCrossRef;
import com.example.midnightmusic.data.model.PlaylistWithSongs;
import com.example.midnightmusic.data.model.Song;
import com.example.midnightmusic.databinding.ActivityPlaylistDetailBinding;
import com.example.midnightmusic.player.MusicPlayerManager;
import com.example.midnightmusic.ui.player.PlayerActivity;
import com.example.midnightmusic.ui.search.SearchAdapter;
import android.app.ActivityOptions;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

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

    private void setupPlayAllButton() {
        binding.fabPlay.setOnClickListener(v -> {
            if (currentPlaylist != null && currentPlaylist.songs != null && !currentPlaylist.songs.isEmpty()) {
                // Play all songs from the playlist from the beginning
                playerManager.playQueue(currentPlaylist.songs, 0);
                Toast.makeText(this, "Playing all songs from " + currentPlaylist.playlist.getName(), 
                               Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "No songs in playlist", Toast.LENGTH_SHORT).show();
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

    private void updateUI(PlaylistWithSongs playlist) {
        if (playlist == null) return;

        // Store current playlist for later use
        this.currentPlaylist = playlist;

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
        } else if ("Liked Songs".equals(playlist.playlist.getName())) {
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
        Toast.makeText(this, "Playing from " + currentPlaylist.playlist.getName(), Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(this, "Playlists updated", Toast.LENGTH_SHORT).show();
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

    @Override
    public void onQueueNext(Song song) {
        MusicPlayerManager.getInstance(this).addToQueue(song);
        Toast.makeText(this, "Added to queue", Toast.LENGTH_SHORT).show();
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