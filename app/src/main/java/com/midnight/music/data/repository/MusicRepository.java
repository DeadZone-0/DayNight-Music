package com.midnight.music.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.midnight.music.data.db.AppDatabase;
import com.midnight.music.data.db.PlaylistDao;
import com.midnight.music.data.db.SongDao;
import com.midnight.music.data.model.Playlist;
import com.midnight.music.data.model.PlaylistSongCrossRef;
import com.midnight.music.data.model.PlaylistWithSongs;
import com.midnight.music.data.model.Song;
import com.midnight.music.data.network.JioSaavnService;
import com.midnight.music.data.network.SongResponse;

import com.midnight.music.data.auth.SessionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import com.midnight.music.utils.DownloadManager;

import com.midnight.music.data.repository.RecommendationManager;

/**
 * Repository class that abstracts data access from multiple sources.
 * Combines local database (Room) and remote API (Retrofit) access.
 */
public class MusicRepository {
    private static final String TAG = "MusicRepository";
    private static volatile MusicRepository instance;

    private final SongDao songDao;
    private final PlaylistDao playlistDao;
    private final JioSaavnService apiService;
    private final Executor diskIO;
    private final Context appContext;
    private RecommendationManager recommendationManager;

    private MusicRepository(Context context) {
        this.appContext = context.getApplicationContext();
        AppDatabase database = AppDatabase.getInstance(context);
        this.songDao = database.songDao();
        this.playlistDao = database.playlistDao();
        this.diskIO = Executors.newSingleThreadExecutor();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(JioSaavnService.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        this.apiService = retrofit.create(JioSaavnService.class);
    }

    public static MusicRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (MusicRepository.class) {
                if (instance == null) {
                    instance = new MusicRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    // ============ Song Operations ============

    /**
     * Search for songs from the API
     */
    public void searchSongs(String query, SearchCallback callback) {
        apiService.searchSongs(query, false).enqueue(new Callback<List<SongResponse>>() {
            @Override
            public void onResponse(@NonNull Call<List<SongResponse>> call,
                    @NonNull Response<List<SongResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Song> songs = new ArrayList<>();
                    for (SongResponse songResponse : response.body()) {
                        songs.add(songResponse.toSong());
                    }
                    callback.onSuccess(songs);
                } else {
                    callback.onError(new Exception("Failed to search songs: " + response.code()));
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<SongResponse>> call, @NonNull Throwable t) {
                Log.e(TAG, "Search failed", t);
                callback.onError(new Exception("Network error: " + t.getMessage()));
            }
        });
    }

    /**
     * Get all liked songs from local database
     */
    public LiveData<List<Song>> getLikedSongs() {
        return songDao.getLikedSongs();
    }

    /**
     * Get recently played songs
     */
    public LiveData<List<Song>> getRecentlyPlayedSongs() {
        return songDao.getRecentSongs(20);
    }

    /**
     * Get count of liked songs
     */
    public LiveData<Integer> getLikedSongsCount() {
        return songDao.getLikedSongsCount();
    }

    /**
     * Toggle like status for a song and sync with "Liked Songs" playlist
     */
    public void toggleLikeSong(Song song, LikeCallback callback) {
        diskIO.execute(() -> {
            try {
                boolean newLikeStatus = !song.isLiked();
                song.setLiked(newLikeStatus);
                
                // Update song in database
                Song existingSong = songDao.getSongByIdSync(song.getId());
                if (existingSong != null) {
                    existingSong.setLiked(newLikeStatus);
                    songDao.updateLikedStatus(existingSong.getId(), newLikeStatus);
                } else {
                    songDao.insert(song);
                }

                // Get or create Liked Songs playlist
                String likedSongsName = appContext.getString(com.midnight.music.R.string.liked_songs);
                Playlist likedPlaylist = playlistDao.getPlaylistByNameSync(likedSongsName);
                if (likedPlaylist == null) {
                    likedPlaylist = new Playlist(likedSongsName);
                    likedPlaylist.setCreatedAt(System.currentTimeMillis());
                    long id = playlistDao.insert(likedPlaylist);
                    likedPlaylist.setId(id);
                }

                // Sync with playlist
                if (newLikeStatus) {
                    PlaylistSongCrossRef crossRef = new PlaylistSongCrossRef(likedPlaylist.getId(), song.getId());
                    playlistDao.insert(crossRef);
                } else {
                    playlistDao.removeSongFromPlaylist(likedPlaylist.getId(), song.getId());
                }

                triggerSync();
                callback.onComplete(newLikeStatus);
            } catch (Exception e) {
                Log.e(TAG, "Error toggling like", e);
                callback.onError(e);
            }
        });
    }

    /**
     * Save song to database (for history/cache)
     */
    public void saveSong(Song song) {
        diskIO.execute(() -> {
            try {
                song.setTimestamp(System.currentTimeMillis());
                // Preserve existing flags before REPLACE overwrites them
                Song existing = songDao.getSongByIdSync(song.getId());
                if (existing != null) {
                    song.setLiked(existing.isLiked());
                    song.setDownloaded(existing.isDownloaded());
                    song.setLocalPath(existing.getLocalPath());
                    songDao.update(song);
                } else {
                    songDao.insert(song);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error saving song", e);
            }
        });
    }

    // ============ Playlist Operations ============

    /**
     * Get all playlists with their songs
     */
    public LiveData<List<PlaylistWithSongs>> getAllPlaylists() {
        return playlistDao.getAllPlaylistsWithSongs();
    }

    /**
     * Get a specific playlist by ID
     */
    public LiveData<PlaylistWithSongs> getPlaylist(long playlistId) {
        return playlistDao.getPlaylistWithSongs(playlistId);
    }

    /**
     * Create a new playlist
     */
    public void createPlaylist(String name, PlaylistCallback callback) {
        diskIO.execute(() -> {
            try {
                if (playlistDao.isPlaylistExists(name)) {
                    callback.onError(new Exception("Playlist already exists"));
                    return;
                }
                Playlist playlist = new Playlist(name);
                long id = playlistDao.insert(playlist);
                triggerSync();
                callback.onSuccess(id);
            } catch (Exception e) {
                Log.e(TAG, "Error creating playlist", e);
                callback.onError(e);
            }
        });
    }

    /**
     * Delete a playlist
     */
    public void deletePlaylist(Playlist playlist, Runnable onComplete) {
        diskIO.execute(() -> {
            try {
                playlistDao.delete(playlist);
                triggerSync();
                if (onComplete != null)
                    onComplete.run();
            } catch (Exception e) {
                Log.e(TAG, "Error deleting playlist", e);
            }
        });
    }

    /**
     * Add a song to a playlist
     */
    public void addSongToPlaylist(long playlistId, Song song, Runnable onComplete) {
        diskIO.execute(() -> {
            try {
                // Check if the target playlist is "Liked Songs"
                Playlist targetPlaylist = playlistDao.getPlaylistByIdSync(playlistId);
                String likedSongsName = appContext.getString(com.midnight.music.R.string.liked_songs);
                if (targetPlaylist != null && likedSongsName.equals(targetPlaylist.getName())) {
                    song.setLiked(true);
                    Song existingSong = songDao.getSongByIdSync(song.getId());
                    if (existingSong != null) {
                        existingSong.setLiked(true);
                        songDao.updateLikedStatus(existingSong.getId(), true);
                    } else {
                        songDao.insert(song);
                    }
                } else {
                    // First ensure the song exists in the database
                    Song existingSong = songDao.getSongByIdSync(song.getId());
                    if (existingSong == null) {
                        songDao.insert(song);
                    }
                }

                // Then create the cross-reference
                PlaylistSongCrossRef crossRef = new PlaylistSongCrossRef(playlistId, song.getId());
                playlistDao.insert(crossRef);

                triggerSync();
                if (onComplete != null)
                    onComplete.run();
            } catch (Exception e) {
                Log.e(TAG, "Error adding song to playlist", e);
            }
        });
    }

    /**
     * Batch import songs to a playlist in a single transaction.
     * Songs are NOT added to recents (timestamp is set to 0).
     */
    public void importSongsToPlaylist(long playlistId, List<Song> songs, Runnable onComplete) {
        diskIO.execute(() -> {
            try {
                Playlist targetPlaylist = playlistDao.getPlaylistByIdSync(playlistId);
                String likedSongsName = appContext.getString(com.midnight.music.R.string.liked_songs);
                boolean isLikedPlaylist = targetPlaylist != null && likedSongsName.equals(targetPlaylist.getName());

                for (Song song : songs) {
                    song.setTimestamp(0);
                    if (isLikedPlaylist) {
                        song.setLiked(true);
                        Song existingSong = songDao.getSongByIdSync(song.getId());
                        if (existingSong != null) {
                            existingSong.setLiked(true);
                            songDao.updateLikedStatus(existingSong.getId(), true);
                        } else {
                            songDao.insert(song);
                        }
                    } else {
                        Song existingSong = songDao.getSongByIdSync(song.getId());
                        if (existingSong == null) {
                            songDao.insert(song);
                        }
                    }

                    PlaylistSongCrossRef crossRef = new PlaylistSongCrossRef(playlistId, song.getId());
                    playlistDao.insert(crossRef);
                }
                triggerSync();
                if (onComplete != null)
                    onComplete.run();
            } catch (Exception e) {
                Log.e(TAG, "Error batch importing songs to playlist", e);
            }
        });
    }

    /**
     * Remove a song from a playlist
     */
    public void removeSongFromPlaylist(long playlistId, String songId, Runnable onComplete) {
        diskIO.execute(() -> {
            try {
                // Check if the target playlist is "Liked Songs"
                Playlist targetPlaylist = playlistDao.getPlaylistByIdSync(playlistId);
                String likedSongsName = appContext.getString(com.midnight.music.R.string.liked_songs);
                if (targetPlaylist != null && likedSongsName.equals(targetPlaylist.getName())) {
                    Song existingSong = songDao.getSongByIdSync(songId);
                    if (existingSong != null) {
                        existingSong.setLiked(false);
                        songDao.updateLikedStatus(songId, false);
                    }
                }

                playlistDao.removeSongFromPlaylist(playlistId, songId);
                triggerSync();
                if (onComplete != null)
                    onComplete.run();
            } catch (Exception e) {
                Log.e(TAG, "Error removing song from playlist", e);
            }
        });
    }

    /**
     * Rename a playlist
     */
    public void renamePlaylist(long playlistId, String newName, Runnable onComplete) {
        diskIO.execute(() -> {
            try {
                playlistDao.updatePlaylistName(playlistId, newName);
                if (onComplete != null)
                    onComplete.run();
            } catch (Exception e) {
                Log.e(TAG, "Error renaming playlist", e);
            }
        });
    }

    /**
     * Get the "Liked Songs" playlist, creating it if it doesn't exist
     */
    public void getOrCreateLikedPlaylist(PlaylistCallback callback) {
        diskIO.execute(() -> {
            try {
                String likedSongsName = appContext.getString(com.midnight.music.R.string.liked_songs);
                Playlist existing = playlistDao.getPlaylistByNameSync(likedSongsName);
                if (existing != null) {
                    callback.onSuccess(existing.getId());
                } else {
                    Playlist liked = new Playlist(likedSongsName);
                    liked.setCreatedAt(System.currentTimeMillis());
                    long id = playlistDao.insert(liked);
                    callback.onSuccess(id);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting/creating liked playlist", e);
                callback.onError(e);
            }
        });
    }

    // ============ Download Operations ============

    /**
     * Get all downloaded songs from local database
     */
    public LiveData<List<Song>> getDownloadedSongs() {
        return songDao.getDownloadedSongs();
    }

    /**
     * Download a song and save to permanent storage
     */
    public void downloadSong(Context context, Song song, DownloadManager.DownloadListener listener) {
        // First ensure the song is in the database
        diskIO.execute(() -> {
            song.setTimestamp(System.currentTimeMillis());
            // Preserve existing flags before REPLACE overwrites them
            Song existing = songDao.getSongByIdSync(song.getId());
            if (existing != null) {
                song.setLiked(existing.isLiked());
                song.setDownloaded(existing.isDownloaded());
                song.setLocalPath(existing.getLocalPath());
                songDao.update(song);
            } else {
                songDao.insert(song);
            }
        });
        // Then start the download
        DownloadManager.getInstance(context).downloadSong(song, listener);
    }

    /**
     * Delete a downloaded song
     */
    public void deleteDownload(Context context, Song song, Runnable onComplete) {
        DownloadManager.getInstance(context).deleteDownload(song, onComplete);
    }

    /**
     * Check if a song is already downloaded
     */
    public void isSongDownloaded(String songId, DownloadCheckCallback callback) {
        diskIO.execute(() -> {
            boolean downloaded = songDao.isSongDownloaded(songId);
            callback.onResult(downloaded);
        });
    }

    // ============ Callbacks ============

    public interface SearchCallback {
        void onSuccess(List<Song> songs);

        void onError(Exception e);
    }

    public interface LikeCallback {
        void onComplete(boolean isLiked);

        void onError(Exception e);
    }

    public interface PlaylistCallback {
        void onSuccess(long playlistId);

        void onError(Exception e);
    }

    public interface DownloadCheckCallback {
        void onResult(boolean isDownloaded);
    }

    // ============ Recommendation Operations ============

    /**
     * Set the Last.fm API key for recommendations
     */
    public void setLastFmApiKey(String apiKey) {
        this.recommendationManager = RecommendationManager.getInstance(apiKey);
    }

    /**
     * Get similar tracks based on a track name and artist
     */
    public void getSimilarTracks(String trackName, String artistName, int limit,
            RecommendationManager.RecommendationCallback callback) {
        if (recommendationManager == null) {
            callback.onError(new IllegalStateException("Last.fm API key not set. Call setLastFmApiKey() first."));
            return;
        }
        recommendationManager.getSimilarTracks(trackName, artistName, limit, callback);
    }

    /**
     * Get trending tracks
     */
    public void getTrendingTracks(int limit, RecommendationManager.RecommendationCallback callback) {
        if (recommendationManager == null) {
            callback.onError(new IllegalStateException("Last.fm API key not set."));
            return;
        }
        recommendationManager.getTrendingTracks(limit, callback);
    }

    /**
     * Get top tracks by artist
     */
    public void getArtistTopTracks(String artist, int limit,
            RecommendationManager.RecommendationCallback callback) {
        if (recommendationManager == null) {
            callback.onError(new IllegalStateException("Last.fm API key not set."));
            return;
        }
        recommendationManager.getArtistTopTracks(artist, limit, callback);
    }

    // ============ Cloud Sync ============

    /**
     * Trigger a one-time background sync to Supabase.
     * Called automatically after local playlist/song changes.
     */
    private void triggerSync() {
        if (SessionManager.getInstance(appContext).isLoggedIn()) {
            CloudSyncWorker.triggerImmediateSync(appContext);
        }
    }
}
