package com.midnight.music.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.midnight.music.data.auth.SessionManager;
import com.midnight.music.data.db.AppDatabase;
import com.midnight.music.data.db.PlaylistDao;
import com.midnight.music.data.db.SongDao;
import com.midnight.music.data.model.Playlist;
import com.midnight.music.data.model.PlaylistSongCrossRef;
import com.midnight.music.data.model.PlaylistWithSongs;
import com.midnight.music.data.model.Song;
import com.midnight.music.data.network.SupabaseApiClient;
import com.midnight.music.data.network.SupabaseDataService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Handles automatic synchronization between the local Room database and Supabase cloud.
 * Uses a push-then-pull strategy:
 *   1. Push all local playlists and songs to the cloud (upsert).
 *   2. Pull cloud data and merge into local database.
 */
public class CloudSyncManager {
    private static final String TAG = "CloudSyncManager";
    private static volatile CloudSyncManager instance;

    private final SongDao songDao;
    private final PlaylistDao playlistDao;
    private final SupabaseDataService dataService;
    private final SessionManager sessionManager;
    private final Executor diskIO;

    private boolean isSyncing = false;

    private CloudSyncManager(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        this.songDao = db.songDao();
        this.playlistDao = db.playlistDao();
        this.dataService = SupabaseApiClient.getInstance().getDataService();
        this.sessionManager = SessionManager.getInstance(context);
        this.diskIO = Executors.newSingleThreadExecutor();
    }

    public static CloudSyncManager getInstance(Context context) {
        if (instance == null) {
            synchronized (CloudSyncManager.class) {
                if (instance == null) {
                    instance = new CloudSyncManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public interface SyncCallback {
        void onComplete(boolean success, String message);
    }

    /**
     * Perform a full bidirectional sync.
     * Call this after login, on app launch (if logged in), or after local changes.
     */
    public void sync(SyncCallback callback) {
        if (!sessionManager.isLoggedIn()) {
            if (callback != null) callback.onComplete(false, "Not logged in");
            return;
        }
        if (isSyncing) {
            if (callback != null) callback.onComplete(false, "Sync already in progress");
            return;
        }

        isSyncing = true;
        String userId = sessionManager.getUserId();
        String accessToken = sessionManager.getAccessToken();
        
        // Ensure token is set in SupabaseApiClient before sync
        if (accessToken != null) {
            SupabaseApiClient.getInstance().setAccessToken(accessToken);
        }

        Log.d(TAG, "Starting sync for user: " + userId);

        // Step 1: Push local data to cloud
        diskIO.execute(() -> {
            try {
                Log.d(TAG, "Pushing songs to cloud...");
                pushSongsToCloud(userId);
                Log.d(TAG, "Pushing playlists to cloud...");
                pushPlaylistsToCloud(userId);
                Log.d(TAG, "Pushing playlist songs to cloud...");
                pushPlaylistSongsToCloud(userId);

                // Step 2: Pull cloud data to local
                Log.d(TAG, "Pulling songs from cloud...");
                pullSongsFromCloud(userId);
                Log.d(TAG, "Pulling playlists from cloud...");
                pullPlaylistsFromCloud(userId);
                Log.d(TAG, "Pulling playlist songs from cloud...");
                pullPlaylistSongsFromCloud(userId);

                isSyncing = false;
                Log.d(TAG, "Sync completed successfully");
                if (callback != null) callback.onComplete(true, "Sync complete");
            } catch (Exception e) {
                isSyncing = false;
                Log.e(TAG, "Sync failed", e);
                if (callback != null) callback.onComplete(false, e.getMessage());
            }
        });
    }

    // ============ Push to Cloud ============

    /**
     * Delete a song from cloud or update its like status.
     * Called when a song is unliked locally.
     */
    public void deleteOrUpdateSongInCloud(String userId, String songId, boolean isLiked) {
        try {
            if (!isLiked) {
                // Song is no longer liked - delete from cloud songs table
                Response<Void> response = dataService.deleteSong(songId, userId).execute();
                if (response.isSuccessful()) {
                    Log.d(TAG, "Deleted song from cloud: " + songId);
                } else {
                    Log.e(TAG, "Failed to delete song: " + response.code());
                }
            } else {
                // Song is liked - update is_liked=true in cloud (in case it exists)
                JsonObject body = new JsonObject();
                body.addProperty("is_liked", true);
                Response<JsonArray> response = dataService.updateSong(
                        songId, userId, body, "return=minimal"
                ).execute();
                if (response.isSuccessful()) {
                    Log.d(TAG, "Updated song like status in cloud: " + songId);
                } else {
                    Log.e(TAG, "Failed to update song: " + response.code());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in deleteOrUpdateSongInCloud", e);
        }
    }

    private void pushSongsToCloud(String userId) {
        try {
            // Get all songs from local DB
            List<Song> allLocalSongs = songDao.getAllSongsSync();
            if (allLocalSongs == null || allLocalSongs.isEmpty()) return;

            // First, get existing cloud songs to find which ones to delete (unliked)
            try {
                Response<JsonArray> cloudResponse = dataService.getSongs(
                        "eq." + userId,
                        "id"
                ).execute();

                if (cloudResponse.isSuccessful() && cloudResponse.body() != null) {
                    // Build set of local song IDs (all songs in DB)
                    java.util.Set<String> localSongIds = new java.util.HashSet<>();
                    for (Song s : allLocalSongs) {
                        localSongIds.add(s.getId());
                    }

                    // Find cloud songs that don't exist locally - they were unliked
                    for (JsonElement elem : cloudResponse.body()) {
                        JsonObject cloudSong = elem.getAsJsonObject();
                        String cloudSongId = cloudSong.get("id").getAsString();

                        if (!localSongIds.contains(cloudSongId)) {
                            // Song was unliked - delete from cloud
                            try {
                                dataService.deleteSong(cloudSongId, userId).execute();
                                Log.d(TAG, "Deleted unliked song from cloud: " + cloudSongId);
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to delete song: " + cloudSongId, e);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting cloud songs for sync", e);
            }

            // Now push/update all liked songs
            JsonArray songsArray = new JsonArray();
            for (Song song : allLocalSongs) {
                if (song.isLiked()) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", song.getId());
                    obj.addProperty("user_id", userId);
                    obj.addProperty("title", song.getSong());
                    obj.addProperty("artist", song.getSingers());
                    obj.addProperty("album", song.getAlbum());
                    obj.addProperty("duration", song.getDuration());
                    obj.addProperty("image_url", song.getImageUrl());
                    obj.addProperty("language", song.getLanguage());
                    obj.addProperty("year", song.getYear());
                    obj.addProperty("is_liked", song.isLiked());
                    obj.addProperty("perma_url", song.getPermaUrl());
                    obj.addProperty("media_url", song.getMediaUrl());
                    songsArray.add(obj);
                }
            }

            if (songsArray.size() > 0) {
                Response<JsonArray> response = dataService.upsertSongs(
                        songsArray,
                        "resolution=merge-duplicates"
                ).execute();

                if (!response.isSuccessful()) {
                    Log.e(TAG, "Failed to push songs: " + response.code());
                } else {
                    Log.d(TAG, "Pushed " + songsArray.size() + " liked songs to cloud");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error pushing songs", e);
        }
    }

    private void pushPlaylistsToCloud(String userId) {
        try {
            List<PlaylistWithSongs> playlists = playlistDao.getAllPlaylistsWithSongsSync();
            if (playlists == null || playlists.isEmpty()) return;

            JsonArray playlistsArray = new JsonArray();
            for (PlaylistWithSongs pws : playlists) {
                Playlist p = pws.playlist;
                JsonObject obj = new JsonObject();
                obj.addProperty("id", p.getId());
                obj.addProperty("user_id", userId);
                obj.addProperty("name", p.getName());
                obj.addProperty("created_at", p.getCreatedAt());
                playlistsArray.add(obj);
            }

            Response<JsonArray> response = dataService.upsertPlaylists(
                    playlistsArray,
                    "resolution=merge-duplicates"
            ).execute();

            if (!response.isSuccessful()) {
                Log.e(TAG, "Failed to push playlists: " + response.code());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error pushing playlists", e);
        }
    }

    private void pushPlaylistSongsToCloud(String userId) {
        try {
            List<PlaylistWithSongs> playlists = playlistDao.getAllPlaylistsWithSongsSync();
            if (playlists == null || playlists.isEmpty()) return;

            JsonArray crossRefsArray = new JsonArray();
            for (PlaylistWithSongs pws : playlists) {
                if (pws.songs == null) continue;
                for (Song song : pws.songs) {
                    // Also push the song itself first
                    JsonObject songObj = new JsonObject();
                    songObj.addProperty("id", song.getId());
                    songObj.addProperty("user_id", userId);
                    songObj.addProperty("title", song.getSong());
                    songObj.addProperty("artist", song.getSingers());
                    songObj.addProperty("album", song.getAlbum());
                    songObj.addProperty("duration", song.getDuration());
                    songObj.addProperty("image_url", song.getImageUrl());
                    songObj.addProperty("language", song.getLanguage());
                    songObj.addProperty("year", song.getYear());
                    songObj.addProperty("is_liked", song.isLiked());
                    songObj.addProperty("perma_url", song.getPermaUrl());
                    songObj.addProperty("media_url", song.getMediaUrl());

                    // Push it
                    JsonArray singleSong = new JsonArray();
                    singleSong.add(songObj);
                    try {
                        dataService.upsertSongs(singleSong, "resolution=merge-duplicates").execute();
                    } catch (Exception ignored) {}

                    JsonObject ref = new JsonObject();
                    ref.addProperty("playlist_id", pws.playlist.getId());
                    ref.addProperty("song_id", song.getId());
                    ref.addProperty("user_id", userId);
                    ref.addProperty("added_at", System.currentTimeMillis());
                    crossRefsArray.add(ref);
                }
            }

            if (crossRefsArray.size() > 0) {
                Response<JsonArray> response = dataService.upsertPlaylistSongs(
                        crossRefsArray,
                        "resolution=merge-duplicates"
                ).execute();

                if (!response.isSuccessful()) {
                    Log.e(TAG, "Failed to push playlist_songs: " + response.code());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error pushing playlist songs", e);
        }
    }

    // ============ Pull from Cloud ============

    private void pullSongsFromCloud(String userId) {
        try {
            Response<JsonArray> response = dataService.getSongs(
                    "eq." + userId,
                    "*"
            ).execute();

            if (response.isSuccessful() && response.body() != null) {
                for (JsonElement elem : response.body()) {
                    JsonObject obj = elem.getAsJsonObject();
                    String songId = obj.get("id").getAsString();

                    // Check if already exists locally
                    Song existing = songDao.getSongByIdSync(songId);
                    if (existing == null) {
                        Song song = new Song(songId);
                        song.setSong(getJsonString(obj, "title"));
                        song.setSingers(getJsonString(obj, "artist"));
                        song.setAlbum(getJsonString(obj, "album"));
                        song.setDuration(getJsonString(obj, "duration"));
                        song.setImageUrl(getJsonString(obj, "image_url"));
                        song.setLanguage(getJsonString(obj, "language"));
                        song.setYear(getJsonString(obj, "year"));
                        song.setLiked(obj.has("is_liked") && obj.get("is_liked").getAsBoolean());
                        song.setPermaUrl(getJsonString(obj, "perma_url"));
                        song.setMediaUrl(getJsonString(obj, "media_url"));
                        song.setTimestamp(0); // Don't pollute recents
                        songDao.insert(song);
                    } else {
                        // Update URLs from cloud if local is missing them
                        String cloudPermaUrl = getJsonString(obj, "perma_url");
                        String cloudMediaUrl = getJsonString(obj, "media_url");
                        boolean updated = false;
                        if (existing.getPermaUrl() == null && cloudPermaUrl != null) {
                            existing.setPermaUrl(cloudPermaUrl);
                            updated = true;
                        }
                        if (existing.getMediaUrl() == null && cloudMediaUrl != null) {
                            existing.setMediaUrl(cloudMediaUrl);
                            updated = true;
                        }
                        if (updated) {
                            songDao.update(existing);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error pulling songs", e);
        }
    }

    private void pullPlaylistsFromCloud(String userId) {
        try {
            Response<JsonArray> response = dataService.getPlaylists(
                    "eq." + userId,
                    "*"
            ).execute();

            if (response.isSuccessful() && response.body() != null) {
                for (JsonElement elem : response.body()) {
                    JsonObject obj = elem.getAsJsonObject();
                    long playlistId = obj.get("id").getAsLong();

                    Playlist existing = playlistDao.getPlaylistByIdSync(playlistId);
                    if (existing == null) {
                        Playlist playlist = new Playlist(getJsonString(obj, "name"));
                        playlist.setId(playlistId);
                        if (obj.has("created_at") && !obj.get("created_at").isJsonNull()) {
                            playlist.setCreatedAt(obj.get("created_at").getAsLong());
                        }
                        playlistDao.insert(playlist);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error pulling playlists", e);
        }
    }

    private void pullPlaylistSongsFromCloud(String userId) {
        try {
            Response<JsonArray> response = dataService.getPlaylistSongs(
                    "eq." + userId,
                    "*"
            ).execute();

            if (response.isSuccessful() && response.body() != null) {
                for (JsonElement elem : response.body()) {
                    JsonObject obj = elem.getAsJsonObject();
                    long playlistId = obj.get("playlist_id").getAsLong();
                    String songId = obj.get("song_id").getAsString();
                    long addedAt = obj.has("added_at") ? obj.get("added_at").getAsLong() : System.currentTimeMillis();

                    PlaylistSongCrossRef crossRef = new PlaylistSongCrossRef(playlistId, songId);
                    crossRef.setAddedAt(addedAt);
                    try {
                        playlistDao.insert(crossRef);
                    } catch (Exception ignored) {
                        // Cross ref may already exist
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error pulling playlist songs", e);
        }
    }

    // ============ Helpers ============

    private String getJsonString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    public boolean isSyncing() {
        return isSyncing;
    }
}
