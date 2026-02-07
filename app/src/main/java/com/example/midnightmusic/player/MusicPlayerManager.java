package com.example.midnightmusic.player;

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
import com.example.midnightmusic.data.model.Song;
import com.example.midnightmusic.utils.AudioCacheManager;
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
            
            synchronized (queue) {
                queue.clear();
                queue.add(song);
                currentIndex = 0;
            }
            playCurrentSong();
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
                if (currentIndex < queue.size() - 1) {
                    currentIndex++;
                    playCurrentSong();
                }
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
                if (currentIndex > 0) {
                    currentIndex--;
                    playCurrentSong();
                }
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
            }
            
            if (song == null || song.getMediaUrl() == null) {
                Log.e(TAG, "Invalid song or media URL");
                return;
            }
            
            final String mediaUrl = song.getMediaUrl();
            final Song finalSong = song;
            
            // Check if the song is already cached
            if (audioCacheManager.isUrlCached(mediaUrl)) {
                String cachedPath = audioCacheManager.getCachedFilePath(mediaUrl);
                Log.d(TAG, "Playing from cache: " + cachedPath);
                
                MediaItem mediaItem = MediaItem.fromUri(Uri.parse("file://" + cachedPath));
                player.setMediaItem(mediaItem);
                player.prepare();
                player.play();
                
                // Ensure UI is updated immediately
                mainHandler.post(() -> {
                    currentSongLiveData.setValue(finalSong);
                    isPlayingLiveData.setValue(true);
                });
            } else {
                // Not cached, start playing from URL and cache in background
                Log.d(TAG, "Playing from network: " + mediaUrl);
                
                MediaItem mediaItem = MediaItem.fromUri(Uri.parse(mediaUrl));
                player.setMediaItem(mediaItem);
                player.prepare();
                player.play();
                
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
            player.setRepeatMode(repeatMode);
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
    public LiveData<Boolean> getPlayingLiveData() { return isPlayingLiveData; }
    public LiveData<Song> getCurrentSongLiveData() { return currentSongLiveData; }
    public LiveData<Long> getCurrentPosition() { return currentPosition; }
    public LiveData<Boolean> isShuffleEnabled() { return isShuffleEnabled; }
    public LiveData<Integer> getRepeatMode() { return repeatModeState; }
    
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
} 