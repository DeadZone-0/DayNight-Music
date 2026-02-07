package com.example.midnightmusic.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.example.midnightmusic.data.db.AppDatabase;
import com.example.midnightmusic.data.db.PlaylistDao;
import com.example.midnightmusic.data.db.SongDao;
import com.example.midnightmusic.data.model.Playlist;
import com.example.midnightmusic.data.model.PlaylistSongCrossRef;
import com.example.midnightmusic.data.model.PlaylistWithSongs;
import com.example.midnightmusic.data.model.Song;
import com.example.midnightmusic.data.network.JioSaavnService;
import com.example.midnightmusic.data.network.SongResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

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

    private MusicRepository(Context context) {
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
     * Toggle like status for a song
     */
    public void toggleLikeSong(Song song, LikeCallback callback) {
        diskIO.execute(() -> {
            try {
                boolean newLikeStatus = !song.isLiked();
                song.setLiked(newLikeStatus);
                songDao.insert(song);
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
                songDao.insert(song);
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
                // First ensure the song exists in the database
                songDao.insert(song);

                // Then create the cross-reference
                PlaylistSongCrossRef crossRef = new PlaylistSongCrossRef(playlistId, song.getId());
                playlistDao.insert(crossRef);

                if (onComplete != null)
                    onComplete.run();
            } catch (Exception e) {
                Log.e(TAG, "Error adding song to playlist", e);
            }
        });
    }

    /**
     * Remove a song from a playlist
     */
    public void removeSongFromPlaylist(long playlistId, String songId, Runnable onComplete) {
        diskIO.execute(() -> {
            try {
                playlistDao.removeSongFromPlaylist(playlistId, songId);
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
                Playlist existing = playlistDao.getPlaylistByNameSync("Liked Songs");
                if (existing != null) {
                    callback.onSuccess(existing.getId());
                } else {
                    Playlist liked = new Playlist("Liked Songs");
                    long id = playlistDao.insert(liked);
                    callback.onSuccess(id);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting/creating liked playlist", e);
                callback.onError(e);
            }
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
}
