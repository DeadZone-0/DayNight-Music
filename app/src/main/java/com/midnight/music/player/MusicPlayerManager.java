package com.midnight.music.player;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.midnight.music.data.model.Song;
import com.midnight.music.service.MusicService;
import com.midnight.music.utils.AudioCacheManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MusicPlayerManager {
    private static final String TAG = "MusicPlayerManager";
    private static volatile MusicPlayerManager instance;
    private final ExoPlayer player;
    private final List<Song> queue;
    private int currentIndex;
    private PlayerCallback callback;
    private Context context;
    private boolean isShuffleOn = false;
    private int repeatMode = Player.REPEAT_MODE_OFF;
    private AudioCacheManager audioCacheManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean isProcessingAction = new AtomicBoolean(false);

    // Auto-queue recommendations (only for search-sourced songs)
    private boolean autoQueueEnabled = false;
    private volatile boolean isFetchingRecommendations = false;
    private static final String LASTFM_API_KEY = com.midnight.music.BuildConfig.LASTFM_API_KEY;

    private final MutableLiveData<Boolean> isPlayingLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Song> currentSongLiveData = new MutableLiveData<>();
    private final MutableLiveData<Long> currentPosition = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isShuffleEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> repeatModeState = new MutableLiveData<>(Player.REPEAT_MODE_OFF);

    public interface PlayerCallback {
        void onPlaybackStateChanged(boolean isPlaying);

        void onSongChanged(Song song);
    }

    private MusicPlayerManager(Context context) {
        this.context = context.getApplicationContext();
        player = new ExoPlayer.Builder(context).build();
        queue = new ArrayList<>();
        currentIndex = -1;
        audioCacheManager = AudioCacheManager.getInstance(context);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                try {
                    boolean isPlaying = player.isPlaying();
                    if (callback != null) {
                        mainHandler.post(() -> {
                            try {
                                callback.onPlaybackStateChanged(isPlaying);
                            } catch (Exception e) {
                                Log.e(TAG, "Error in onPlaybackStateChanged callback", e);
                            }
                        });
                    }
                    isPlayingLiveData.postValue(isPlaying);

                    // Handle song end: auto-advance based on repeat mode
                    if (playbackState == Player.STATE_ENDED) {
                        // Sleep Timer: if "end of track" mode is active, pause here
                        com.midnight.music.utils.SleepTimerManager sleepTimer =
                                com.midnight.music.utils.SleepTimerManager.getInstance();
                        if (sleepTimer.shouldPauseAtEndOfTrack()) {
                            // Sleep timer intercepted — don't advance
                            return;
                        }

                        synchronized (queue) {
                            if (repeatMode == Player.REPEAT_MODE_ONE) {
                                // Replay the same song
                                player.seekTo(0);
                                player.play();
                            } else if (currentIndex < queue.size() - 1) {
                                // More songs in queue, advance
                                currentIndex++;
                                mainHandler.post(() -> playCurrentSong());
                            } else if (repeatMode == Player.REPEAT_MODE_ALL && !queue.isEmpty()) {
                                // End of queue + repeat all: wrap to start
                                currentIndex = 0;
                                mainHandler.post(() -> playCurrentSong());
                            } else if (autoQueueEnabled && !isFetchingRecommendations && !queue.isEmpty()) {
                                // REPEAT_MODE_OFF at end + auto-queue: fetch recommendations
                                Song currentSong = queue.get(currentIndex);
                                fetchAutoQueueRecommendations(currentSong);
                            }
                            // else: REPEAT_MODE_OFF, no auto-queue — stop naturally
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in onPlaybackStateChanged", e);
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                try {
                    // Ensure UI is updated when play state changes
                    if (callback != null) {
                        mainHandler.post(() -> {
                            try {
                                callback.onPlaybackStateChanged(isPlaying);
                            } catch (Exception e) {
                                Log.e(TAG, "Error in onIsPlayingChanged callback", e);
                            }
                        });
                    }
                    isPlayingLiveData.postValue(isPlaying);

                    // Update the notification to reflect new play/pause state
                    MusicService.updateNotification(context);
                } catch (Exception e) {
                    Log.e(TAG, "Error in onIsPlayingChanged", e);
                }
            }

            @Override
            public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                try {
                    if (currentIndex >= 0 && currentIndex < queue.size()) {
                        Song song = queue.get(currentIndex);
                        if (callback != null) {
                            mainHandler.post(() -> {
                                try {
                                    callback.onSongChanged(song);
                                    // Also update playback state when song changes
                                    callback.onPlaybackStateChanged(player.isPlaying());
                                } catch (Exception e) {
                                    Log.e(TAG, "Error in onSongChanged callback", e);
                                }
                            });
                        }
                        currentSongLiveData.postValue(song);
                        // Always update play state when song changes
                        isPlayingLiveData.postValue(player.isPlaying());

                        // Update the notification with new song info
                        MusicService.updateNotification(context);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in onMediaItemTransition", e);
                }
            }

            @Override
            public void onPlayerError(@NonNull androidx.media3.common.PlaybackException error) {
                Log.e(TAG, "Player error: " + error.getMessage(), error);
                // Try to recover by skipping to the next song if possible
                mainHandler.postDelayed(() -> {
                    try {
                        if (!queue.isEmpty() && currentIndex < queue.size() - 1) {
                            skipToNext();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error recovering from player error", e);
                    }
                }, 1000);
            }
        });

        // Start a periodic position update
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (player != null && player.isPlaying()) {
                        currentPosition.postValue(player.getCurrentPosition());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error updating position", e);
                }
                mainHandler.postDelayed(this, 500);
            }
        });
    }

    public static MusicPlayerManager getInstance(Context context) {
        if (instance == null) {
            synchronized (MusicPlayerManager.class) {
                if (instance == null) {
                    instance = new MusicPlayerManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public void setCallback(PlayerCallback callback) {
        this.callback = callback;
    }

    public ExoPlayer getPlayer() {
        return player;
    }

    public void playSong(Song song) {
        try {
            if (song == null) {
                Log.e(TAG, "Cannot play null song");
                return;
            }

            // Reset stale fetch flag â€” any in-flight fetch from old song is irrelevant
            isFetchingRecommendations = false;

            synchronized (queue) {
                queue.clear();
                queue.add(song);
                currentIndex = 0;
            }
            playCurrentSong();
            // Pre-fetch is now handled centrally inside playCurrentSong()
        } catch (Exception e) {
            Log.e(TAG, "Error in playSong", e);
        }
    }

    public void playQueue(List<Song> songs, int startIndex) {
        try {
            if (songs == null || songs.isEmpty()) {
                Log.e(TAG, "Cannot play empty song list");
                return;
            }

            if (startIndex < 0 || startIndex >= songs.size()) {
                Log.e(TAG, "Invalid start index: " + startIndex);
                startIndex = 0;
            }

            // Disable auto-queue for playlist/queue sources
            autoQueueEnabled = false;

            synchronized (queue) {
                queue.clear();
                queue.addAll(songs);
                currentIndex = startIndex;
            }
            playCurrentSong();
        } catch (Exception e) {
            Log.e(TAG, "Error in playQueue", e);
        }
    }

    /**
     * Jump to a specific index in the current queue without resetting auto-queue.
     * Used when tapping a song in the queue UI.
     */
    public void playAtIndex(int index) {
        synchronized (queue) {
            if (index >= 0 && index < queue.size()) {
                currentIndex = index;
                playCurrentSong();
            }
        }
    }

    public void addToQueue(Song song) {
        try {
            if (song == null) {
                Log.e(TAG, "Cannot add null song to queue");
                return;
            }

            synchronized (queue) {
                queue.add(song);
                if (queue.size() == 1) {
                    currentIndex = 0;
                    playCurrentSong();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in addToQueue", e);
        }
    }

    public void queueNext(Song song) {
        try {
            if (song == null) {
                Log.e(TAG, "Cannot queue null song");
                return;
            }

            synchronized (queue) {
                if (currentIndex >= 0) {
                    queue.add(currentIndex + 1, song);
                } else {
                    queue.add(song);
                    currentIndex = 0;
                    playCurrentSong();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in queueNext", e);
        }
    }

    public void skipToNext() {
        try {
            if (isProcessingAction.getAndSet(true)) {
                Log.d(TAG, "Already processing an action, ignoring skipToNext");
                return;
            }

            synchronized (queue) {
                if (queue.isEmpty()) {
                    isProcessingAction.set(false);
                    return;
                }

                if (repeatMode == Player.REPEAT_MODE_ONE) {
                    // Replay current song
                    playCurrentSong();
                } else if (currentIndex < queue.size() - 1) {
                    // Normal: advance to next
                    currentIndex++;
                    playCurrentSong();
                } else if (repeatMode == Player.REPEAT_MODE_ALL) {
                    // At end of queue + repeat ALL: wrap to start
                    currentIndex = 0;
                    playCurrentSong();
                } else if (autoQueueEnabled && !isFetchingRecommendations && !queue.isEmpty()) {
                    // At end of queue + auto-queue: fetch more songs
                    fetchAutoQueueRecommendations(queue.get(currentIndex));
                }
                // REPEAT_MODE_OFF at end without auto-queue: do nothing
            }

            isProcessingAction.set(false);
        } catch (Exception e) {
            Log.e(TAG, "Error in skipToNext", e);
            isProcessingAction.set(false);
        }
    }

    public void skipToPrevious() {
        try {
            if (isProcessingAction.getAndSet(true)) {
                Log.d(TAG, "Already processing an action, ignoring skipToPrevious");
                return;
            }

            synchronized (queue) {
                if (queue.isEmpty()) {
                    isProcessingAction.set(false);
                    return;
                }

                if (repeatMode == Player.REPEAT_MODE_ONE) {
                    // Replay current song
                    playCurrentSong();
                } else if (currentIndex > 0) {
                    // Normal: go to previous
                    currentIndex--;
                    playCurrentSong();
                } else if (repeatMode == Player.REPEAT_MODE_ALL) {
                    // At start of queue + repeat ALL: wrap to end
                    currentIndex = queue.size() - 1;
                    playCurrentSong();
                }
                // REPEAT_MODE_OFF at start: do nothing
            }

            isProcessingAction.set(false);
        } catch (Exception e) {
            Log.e(TAG, "Error in skipToPrevious", e);
            isProcessingAction.set(false);
        }
    }

    private void playCurrentSong() {
        try {
            Song song = null;
            synchronized (queue) {
                if (currentIndex >= 0 && currentIndex < queue.size()) {
                    song = queue.get(currentIndex);
                }

                // Pre-fetch recommendations when we reach the second-to-last song
                // This covers ALL play pathways: skipToNext, STATE_ENDED, queue tap, etc.
                Log.d(TAG, "playCurrentSong: idx=" + currentIndex + ", size=" + queue.size()
                        + ", autoQ=" + autoQueueEnabled + ", fetching=" + isFetchingRecommendations);
                if (autoQueueEnabled && !isFetchingRecommendations
                        && currentIndex >= queue.size() - 2 && song != null) {
                    Log.d(TAG, "Pre-fetch triggered: currentIndex=" + currentIndex
                            + ", queueSize=" + queue.size());
                    fetchAutoQueueRecommendations(song);
                }
            }

            if (song == null || song.getMediaUrl() == null) {
                Log.e(TAG, "Invalid song or media URL");
                return;
            }

            final String mediaUrl = song.getMediaUrl();
            final Song finalSong = song;

            // Build MediaMetadata
            androidx.media3.common.MediaMetadata metadata = new androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(song.getSong())
                    .setArtist(song.getSingers())
                    .setAlbumTitle(song.getAlbum())
                    .setArtworkUri(Uri.parse(song.getImageUrl()))
                    .build();

            // 1. Check if the song is permanently downloaded
            if (song.isDownloaded() && song.getLocalPath() != null) {
                java.io.File localFile = new java.io.File(song.getLocalPath());
                if (localFile.exists()) {
                    Log.d(TAG, "Playing from download: " + song.getLocalPath());

                    MediaItem mediaItem = new MediaItem.Builder()
                            .setUri(Uri.fromFile(localFile))
                            .setMediaMetadata(metadata)
                            .setMediaId(song.getId())
                            .build();

                    player.setMediaItem(mediaItem);
                    player.prepare();
                    player.play();

                    MusicService.startService(context);

                    mainHandler.post(() -> {
                        currentSongLiveData.setValue(finalSong);
                        isPlayingLiveData.setValue(true);
                    });
                    return;
                }
            }

            // 2. Check if the song is in the streaming cache
            if (audioCacheManager.isUrlCached(mediaUrl)) {
                String cachedPath = audioCacheManager.getCachedFilePath(mediaUrl);
                Log.d(TAG, "Playing from cache: " + cachedPath);

                MediaItem mediaItem = new MediaItem.Builder()
                        .setUri(Uri.parse("file://" + cachedPath))
                        .setMediaMetadata(metadata)
                        .setMediaId(song.getId())
                        .build();

                player.setMediaItem(mediaItem);
                player.prepare();
                player.play();

                // Start foreground service for media notification
                MusicService.startService(context);

                // Ensure UI is updated immediately
                mainHandler.post(() -> {
                    currentSongLiveData.setValue(finalSong);
                    isPlayingLiveData.setValue(true);
                });
            } else {
                // Not cached, start playing from URL and cache in background
                Log.d(TAG, "Playing from network: " + mediaUrl);

                MediaItem mediaItem = new MediaItem.Builder()
                        .setUri(Uri.parse(mediaUrl))
                        .setMediaMetadata(metadata)
                        .setMediaId(song.getId())
                        .build();

                player.setMediaItem(mediaItem);
                player.prepare();
                player.play();

                // Start foreground service for media notification
                MusicService.startService(context);

                // Ensure UI is updated immediately
                mainHandler.post(() -> {
                    currentSongLiveData.setValue(finalSong);
                    isPlayingLiveData.setValue(true);
                });

                // Start caching in background for future use
                audioCacheManager.cacheAudioFile(mediaUrl, new AudioCacheManager.CacheListener() {
                    @Override
                    public void onCacheComplete(String filePath) {
                        Log.d(TAG, "Cached audio successfully: " + filePath);
                    }

                    @Override
                    public void onCacheError(Exception e) {
                        Log.e(TAG, "Failed to cache audio: " + e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in playCurrentSong", e);
        }
    }

    public void togglePlayPause() {
        try {
            if (isProcessingAction.getAndSet(true)) {
                Log.d(TAG, "Already processing an action, ignoring togglePlayPause");
                return;
            }

            if (player.isPlaying()) {
                player.pause();
            } else {
                player.play();
            }

            isProcessingAction.set(false);
        } catch (Exception e) {
            Log.e(TAG, "Error in togglePlayPause", e);
            isProcessingAction.set(false);
        }
    }

    public boolean isPlaying() {
        try {
            return player != null && player.isPlaying();
        } catch (Exception e) {
            Log.e(TAG, "Error in isPlaying", e);
            return false;
        }
    }

    public Song getCurrentSong() {
        try {
            synchronized (queue) {
                return currentIndex >= 0 && currentIndex < queue.size() ? queue.get(currentIndex) : null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in getCurrentSong", e);
            return null;
        }
    }

    public List<Song> getQueue() {
        synchronized (queue) {
            return new ArrayList<>(queue);
        }
    }

    public void clearQueue() {
        try {
            synchronized (queue) {
                queue.clear();
                currentIndex = -1;
            }
            player.stop();
            player.clearMediaItems();

            // Stop the foreground service / dismiss notification
            MusicService.stopService(context);
        } catch (Exception e) {
            Log.e(TAG, "Error in clearQueue", e);
        }
    }

    public void release() {
        try {
            mainHandler.removeCallbacksAndMessages(null);
            player.release();
        } catch (Exception e) {
            Log.e(TAG, "Error in release", e);
        }
    }

    public void toggleShuffle() {
        try {
            isShuffleOn = !isShuffleOn;
            isShuffleEnabled.postValue(isShuffleOn);
            if (isShuffleOn && !queue.isEmpty()) {
                shuffleQueue(currentIndex);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in toggleShuffle", e);
        }
    }

    public void toggleRepeatMode() {
        try {
            switch (repeatMode) {
                case Player.REPEAT_MODE_OFF:
                    repeatMode = Player.REPEAT_MODE_ONE;
                    break;
                case Player.REPEAT_MODE_ONE:
                    repeatMode = Player.REPEAT_MODE_ALL;
                    break;
                default:
                    repeatMode = Player.REPEAT_MODE_OFF;
                    break;
            }
            // Do NOT set player.setRepeatMode() â€” ExoPlayer with a single media item
            // treats REPEAT_MODE_ALL the same as ONE (loops the same track).
            // We handle all repeat logic ourselves in onPlaybackStateChanged STATE_ENDED.
            repeatModeState.postValue(repeatMode);
        } catch (Exception e) {
            Log.e(TAG, "Error in toggleRepeatMode", e);
        }
    }

    private void shuffleQueue(int startIndex) {
        try {
            synchronized (queue) {
                if (startIndex >= 0 && startIndex < queue.size()) {
                    Song currentSong = queue.remove(startIndex);
                    Collections.shuffle(queue);
                    queue.add(0, currentSong);
                    currentIndex = 0;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in shuffleQueue", e);
        }
    }

    public void seekTo(long position) {
        try {
            player.seekTo(position);
        } catch (Exception e) {
            Log.e(TAG, "Error in seekTo", e);
        }
    }

    // LiveData getters with renamed methods
    public LiveData<Boolean> getPlayingLiveData() {
        return isPlayingLiveData;
    }

    public LiveData<Song> getCurrentSongLiveData() {
        return currentSongLiveData;
    }

    public LiveData<Long> getCurrentPosition() {
        return currentPosition;
    }

    public LiveData<Boolean> isShuffleEnabled() {
        return isShuffleEnabled;
    }

    public LiveData<Integer> getRepeatMode() {
        return repeatModeState;
    }

    public long getDuration() {
        try {
            return player != null ? player.getDuration() : 0;
        } catch (Exception e) {
            Log.e(TAG, "Error in getDuration", e);
            return 0;
        }
    }

    /**
     * Force an update of the play state to all observers
     * This is useful when UI components get out of sync
     */
    public void forcePlayStateUpdate() {
        try {
            final boolean isPlaying = player != null && player.isPlaying();

            // Update on main thread
            mainHandler.post(() -> {
                // Update LiveData
                isPlayingLiveData.setValue(isPlaying);

                // Notify callback if available
                if (callback != null) {
                    try {
                        callback.onPlaybackStateChanged(isPlaying);
                    } catch (Exception e) {
                        Log.e(TAG, "Error in forcePlayStateUpdate callback", e);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in forcePlayStateUpdate", e);
        }
    }

    /**
     * Enable or disable auto-queue recommendations.
     * When enabled and the queue ends (repeat OFF),
     * similar tracks are auto-fetched and appended.
     * Should be enabled for search-sourced songs, disabled for playlists.
     */
    public void setAutoQueueEnabled(boolean enabled) {
        this.autoQueueEnabled = enabled;
        Log.d(TAG, "Auto-queue " + (enabled ? "enabled" : "disabled"));
    }

    public boolean isAutoQueueEnabled() {
        return autoQueueEnabled;
    }

    /**
     * Public API for the Similar Songs button in PlayerActivity.
     * Immediately fetches recommendations for the given song and appends them to
     * the queue.
     */
    public void fetchSimilarForQueue(Song song) {
        fetchAutoQueueRecommendations(song);
    }

    /**
     * Fetch similar tracks from Last.fm â†’ JioSaavn and add to queue.
     * Called automatically when the queue reaches its end.
     */
    private int fetchRetryCount = 0;
    private static final int MAX_FETCH_RETRIES = 3;

    private void fetchAutoQueueRecommendations(Song baseSong) {
        if (baseSong == null || baseSong.getSong() == null)
            return;

        isFetchingRecommendations = true;
        Log.d(TAG, "Auto-queue: fetching recommendations for " + baseSong.getSong());

        com.midnight.music.data.repository.RecommendationManager
                .getInstance(LASTFM_API_KEY)
                .getSimilarTracks(baseSong.getSong(),
                        baseSong.getSingers() != null ? baseSong.getSingers() : "",
                        10,
                        new com.midnight.music.data.repository.RecommendationManager.RecommendationCallback() {
                            @Override
                            public void onSuccess(List<Song> recommendations) {
                                if (recommendations == null || recommendations.isEmpty()) {
                                    // No results â€” try fallback
                                    retryWithFallbackSong();
                                    return;
                                }

                                fetchRetryCount = 0; // Reset on success
                                Log.d(TAG, "Auto-queue: got " + recommendations.size() + " recommendations");

                                // Must run on main thread â€” ExoPlayer requires it
                                mainHandler.post(() -> {
                                    isFetchingRecommendations = false;
                                    synchronized (queue) {
                                        // Filter out songs already in queue
                                        java.util.Set<String> existingIds = new java.util.HashSet<>();
                                        for (Song s : queue)
                                            existingIds.add(s.getId());

                                        int added = 0;
                                        for (Song rec : recommendations) {
                                            if (!existingIds.contains(rec.getId()) && rec.getMediaUrl() != null) {
                                                queue.add(rec);
                                                added++;
                                            }
                                        }
                                        Log.d(TAG, "Auto-queue: added " + added + " songs to queue");

                                        // Only auto-advance if player was idle (ended/waiting)
                                        if (added > 0 && player.getPlaybackState() == Player.STATE_ENDED
                                                && currentIndex < queue.size() - 1) {
                                            currentIndex++;
                                            playCurrentSong();
                                        }
                                    }
                                });
                            }

                            @Override
                            public void onError(Exception e) {
                                Log.e(TAG, "Auto-queue: recommendation fetch failed", e);
                                // Try fallback song instead of giving up
                                retryWithFallbackSong();
                            }
                        });
    }

    /**
     * When a recommendation fetch fails, try a different song from the queue.
     * Picks songs progressively earlier in the queue as fallback seeds.
     */
    private void retryWithFallbackSong() {
        fetchRetryCount++;
        if (fetchRetryCount > MAX_FETCH_RETRIES) {
            Log.d(TAG, "Auto-queue: exhausted all retries, giving up");
            isFetchingRecommendations = false;
            fetchRetryCount = 0;
            return;
        }

        synchronized (queue) {
            // Try a song from earlier in the queue
            int fallbackIdx = Math.max(0, currentIndex - fetchRetryCount);
            if (fallbackIdx >= 0 && fallbackIdx < queue.size()) {
                Song fallback = queue.get(fallbackIdx);
                Log.d(TAG, "Auto-queue: retrying with fallback song '" + fallback.getSong()
                        + "' (attempt " + fetchRetryCount + ")");
                isFetchingRecommendations = false; // Reset so fetch can proceed
                fetchAutoQueueRecommendations(fallback);
            } else {
                isFetchingRecommendations = false;
                fetchRetryCount = 0;
            }
        }
    }
}
