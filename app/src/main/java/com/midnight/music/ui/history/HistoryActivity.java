package com.midnight.music.ui.history;

import android.app.ActivityOptions;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.LiveData;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.midnight.music.R;
import com.midnight.music.data.db.AppDatabase;
import com.midnight.music.data.model.PlaylistWithSongs;
import com.midnight.music.data.model.Song;
import com.midnight.music.data.repository.MusicRepository;
import com.midnight.music.databinding.ActivityHistoryBinding;
import com.midnight.music.player.MusicPlayerManager;
import com.midnight.music.ui.player.PlayerActivity;
import com.midnight.music.ui.search.SearchAdapter;
import com.midnight.music.utils.FloatingDownloadHelper;
import com.midnight.music.utils.ImageLoader;
import com.midnight.music.utils.ThemeManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HistoryActivity extends AppCompatActivity implements SearchAdapter.SearchAdapterListener {
    private static final String TAG = "HistoryActivity";

    private ActivityHistoryBinding binding;
    private SearchAdapter adapter;
    private MusicPlayerManager playerManager;
    private MusicRepository repository;
    private final List<Song> currentHistorySongs = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AlertDialog currentPlaylistDialog = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = MusicRepository.getInstance(this);
        playerManager = MusicPlayerManager.getInstance(this);

        setupWindowInsets();
        setupToolbar();
        setupRecyclerView();
        setupActionButtons();
        setupMiniPlayer();
        setupFloatingDownload();
        observeHistory();
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            androidx.core.graphics.Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.appbar.setPadding(0, sysBars.top, 0, 0);

            if (binding.miniPlayer != null) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) binding.miniPlayer.getRoot().getLayoutParams();
                int baseMargin = (int) (16 * getResources().getDisplayMetrics().density);
                lp.bottomMargin = sysBars.bottom + baseMargin;
                binding.miniPlayer.getRoot().setLayoutParams(lp);
            }
            return insets;
        });
    }

    private void setupToolbar() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnClearHistory.setOnClickListener(v -> showClearHistoryConfirmation());
    }

    private void setupRecyclerView() {
        adapter = new SearchAdapter(this);
        adapter.setShowTrackNumbers(true);
        binding.recyclerHistory.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerHistory.setAdapter(adapter);
    }

    private void setupActionButtons() {
        binding.btnPlayAll.setOnClickListener(v -> {
            if (!currentHistorySongs.isEmpty()) {
                playerManager.playQueue(new ArrayList<>(currentHistorySongs), 0);
            } else {
                Toast.makeText(this, "No tracks to play", Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnShuffle.setOnClickListener(v -> {
            if (!currentHistorySongs.isEmpty()) {
                List<Song> shuffled = new ArrayList<>(currentHistorySongs);
                Collections.shuffle(shuffled);
                playerManager.playQueue(shuffled, 0);
            } else {
                Toast.makeText(this, "No tracks to shuffle", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void observeHistory() {
        repository.getAllListeningHistory().observe(this, songs -> {
            currentHistorySongs.clear();
            if (songs != null) {
                currentHistorySongs.addAll(songs);
            }

            adapter.submitList(new ArrayList<>(currentHistorySongs));

            if (currentHistorySongs.isEmpty()) {
                binding.emptyStateHistory.setVisibility(View.VISIBLE);
                binding.actionControls.setVisibility(View.GONE);
                binding.txtHistoryCount.setText("0 tracks");
            } else {
                binding.emptyStateHistory.setVisibility(View.GONE);
                binding.actionControls.setVisibility(View.VISIBLE);
                int count = currentHistorySongs.size();
                binding.txtHistoryCount.setText(count == 1 ? "1 track" : count + " tracks");
            }
        });
    }

    private void showClearHistoryConfirmation() {
        if (currentHistorySongs.isEmpty()) {
            Toast.makeText(this, "Listening history is already empty", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this, R.style.PlaylistDialogStyle)
                .setTitle("Clear History")
                .setMessage("Are you sure you want to clear your listening history?")
                .setPositiveButton("Clear", (dialog, which) -> {
                    repository.clearListeningHistory(() -> {
                        Toast.makeText(HistoryActivity.this, "Listening history cleared", Toast.LENGTH_SHORT).show();
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void setupFloatingDownload() {
        View floatingDownloadView = findViewById(R.id.floatingDownloadView);
        FloatingDownloadHelper.setup(this, floatingDownloadView, mainHandler);
    }

    private void setupMiniPlayer() {
        // Enable hardware acceleration
        binding.miniPlayer.getRoot().setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // Open full player on tap with shared element transition
        binding.miniPlayer.miniPlayerContainer.setOnClickListener(v -> {
            Intent intent = new Intent(this, PlayerActivity.class);
            Pair<View, String> albumArt = Pair.create(binding.miniPlayer.imgMiniArt, "transition_album_art");
            Pair<View, String> songTitle = Pair.create(binding.miniPlayer.txtMiniTitle, "transition_song_title");
            Pair<View, String> artistName = Pair.create(binding.miniPlayer.txtMiniArtist, "transition_artist_name");
            Pair<View, String> playButton = Pair.create(binding.miniPlayer.btnMiniPlayPause, "transition_play_button");

            ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(
                    this, albumArt, songTitle, artistName, playButton
            );
            startActivity(intent, options.toBundle());
            overridePendingTransition(R.anim.slide_up, R.anim.stay);
        });

        binding.miniPlayer.txtMiniTitle.setSelected(true);
        updatePlayPauseButton(playerManager.isPlaying());

        // Play/Pause button
        binding.miniPlayer.btnMiniPlayPause.setOnClickListener(v -> {
            v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(80).start())
                    .start();
            playerManager.togglePlayPause();
        });

        // Heart button
        binding.miniPlayer.btnMiniHeart.setOnClickListener(v -> {
            try {
                Song currentSong = playerManager.getCurrentSongLiveData().getValue();
                if (currentSong == null) return;

                v.animate().scaleX(1.3f).scaleY(1.3f).setDuration(100)
                        .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start())
                        .start();

                repository.toggleLikeSong(currentSong, new MusicRepository.LikeCallback() {
                    @Override
                    public void onComplete(boolean isLiked) {
                        mainHandler.post(() -> {
                            updateHeartButton(isLiked);
                            Toast.makeText(HistoryActivity.this,
                                    isLiked ? "Added to Liked Songs" : "Removed from Liked Songs",
                                    Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Error toggling favorite", e);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error toggling favorite", e);
            }
        });

        // Skip next button
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

        // Observe progress
        playerManager.getCurrentPosition().observe(this, position -> {
            try {
                if (!isDestroyed() && position != null && playerManager.getDuration() > 0) {
                    int progress = (int) ((position * 100) / playerManager.getDuration());
                    if (binding != null && binding.miniPlayer != null && binding.miniPlayer.progressMini != null) {
                        binding.miniPlayer.progressMini.setProgressCompat(progress, true);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating progress", e);
            }
        });

        // Animate mini player visibility
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
    }

    private void updateMiniPlayer(Song song) {
        try {
            if (song == null || binding == null || binding.miniPlayer == null) return;

            binding.miniPlayer.txtMiniTitle.setText(song.getSong());
            binding.miniPlayer.txtMiniArtist.setText(song.getSingers());
            binding.miniPlayer.txtMiniTitle.setSelected(true);

            ImageLoader.loadImage(binding.miniPlayer.imgMiniArt, song.getImageUrl());

            // Extract dominant color from album art for mini player tinting
            Glide.with(this)
                    .asBitmap()
                    .load(song.getImageUrl())
                    .into(new SimpleTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
                            if (isFinishing() || isDestroyed()) return;
                            Palette.from(resource).generate(palette -> {
                                if (palette != null && binding != null && binding.miniPlayer != null) {
                                    ThemeManager.getInstance(HistoryActivity.this).updateFromPalette(palette);
                                    int accentColor = ThemeManager.getInstance(HistoryActivity.this).getAccentColorValue();
                                    int mutedColor = palette.getMutedColor(accentColor);

                                    // Tint progress bar with accent color
                                    binding.miniPlayer.progressMini.setIndicatorColor(accentColor);

                                    // Create a solid opaque dark background subtly tinted with album color — prevents text bleed-through
                                    int surfaceBase = Color.parseColor("#15151A");
                                    int r = (int) (Color.red(surfaceBase) * 0.85f + Color.red(mutedColor) * 0.15f);
                                    int g = (int) (Color.green(surfaceBase) * 0.85f + Color.green(mutedColor) * 0.15f);
                                    int b = (int) (Color.blue(surfaceBase) * 0.85f + Color.blue(mutedColor) * 0.15f);
                                    int solidBg = Color.rgb(r, g, b);

                                    GradientDrawable bg = new GradientDrawable();
                                    bg.setShape(GradientDrawable.RECTANGLE);
                                    bg.setCornerRadius(18 * getResources().getDisplayMetrics().density);
                                    bg.setColor(solidBg);
                                    bg.setStroke((int) (1 * getResources().getDisplayMetrics().density), Color.argb(46, 255, 255, 255));
                                    binding.miniPlayer.miniPlayerContainer.setBackground(bg);
                                }
                            });
                        }
                    });

            updateHeartButton(song.isLiked());
        } catch (Exception e) {
            Log.e(TAG, "Error updating mini player", e);
        }
    }

    private void updateHeartButton(boolean isLiked) {
        if (binding == null || binding.miniPlayer == null) return;
        binding.miniPlayer.btnMiniHeart.setImageResource(
                isLiked ? R.drawable.ic_heart_filled : R.drawable.ic_favorite_border);
        int accent = ThemeManager.getInstance(this).getAccentColorValue();
        binding.miniPlayer.btnMiniHeart.setImageTintList(
                ColorStateList.valueOf(isLiked ? accent : Color.parseColor("#88FFFFFF")));
    }

    private void updatePlayPauseButton(boolean isPlaying) {
        if (binding == null || binding.miniPlayer == null) return;
        binding.miniPlayer.btnMiniPlayPause.setImageResource(
                isPlaying ? R.drawable.ic_pause_rounded : R.drawable.ic_play_rounded
        );
    }

    // ============ SearchAdapter.SearchAdapterListener Implementation ============

    @Override
    public void onSongClick(Song song) {
        if (currentHistorySongs.isEmpty()) return;

        int startIndex = 0;
        for (int i = 0; i < currentHistorySongs.size(); i++) {
            if (currentHistorySongs.get(i).getId().equals(song.getId())) {
                startIndex = i;
                break;
            }
        }
        playerManager.playQueue(new ArrayList<>(currentHistorySongs), startIndex);
    }

    @Override
    public void onPlayNow(Song song) {
        playerManager.playSong(song);
    }

    @Override
    public void onAddToPlaylist(Song song) {
        showPlaylistsDialog(song);
    }

    private void showPlaylistsDialog(Song song) {
        if (song == null || isFinishing() || isDestroyed()) return;

        if (currentPlaylistDialog != null && currentPlaylistDialog.isShowing()) {
            currentPlaylistDialog.dismiss();
        }

        AppDatabase db = AppDatabase.getInstance(this);
        LiveData<List<PlaylistWithSongs>> playlistsLiveData = db.playlistDao().getAllPlaylistsWithSongs();

        final boolean[] observerCalled = {false};
        playlistsLiveData.observe(this, playlists -> {
            if (observerCalled[0]) return;
            observerCalled[0] = true;
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
                if (playlists.get(i).songs != null) {
                    for (Song s : playlists.get(i).songs) {
                        if (s.getId().equals(song.getId())) {
                            checkedItems[i] = true;
                            break;
                        }
                    }
                }
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.PlaylistDialogStyle)
                    .setTitle(R.string.add_to_playlist)
                    .setMultiChoiceItems(playlistNames, checkedItems, (dialog, which, isChecked) -> {})
                    .setPositiveButton(R.string.save, (dialog, which) -> {
                        for (int i = 0; i < checkedItems.length; i++) {
                            if (checkedItems[i]) {
                                repository.addSongToPlaylist(playlistIds[i], song, null);
                            } else {
                                repository.removeSongFromPlaylist(playlistIds[i], song.getId(), null);
                            }
                        }
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss());

            currentPlaylistDialog = builder.create();
            currentPlaylistDialog.show();
        });
    }

    @Override
    public void onQueueNext(Song song) {
        playerManager.addToQueue(song);
        Toast.makeText(this, "Added to queue", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDownload(Song song) {
        com.midnight.music.utils.DownloadManager.getInstance(this).downloadSong(song, null);
    }

    @Override
    public void onToggleLike(Song song) {
        if (song == null || song.getId() == null) return;

        final boolean newIsLiked = !song.isLiked();
        song.setLiked(newIsLiked);
        adapter.notifyDataSetChanged();

        String message = newIsLiked ? "Added to Liked Songs" : "Removed from Liked Songs";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

        repository.toggleLikeSong(song, new MusicRepository.LikeCallback() {
            @Override
            public void onComplete(boolean isLiked) {}

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Error toggling like", e);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (playerManager != null) {
            playerManager.forcePlayStateUpdate();
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
