package com.example.midnightmusic.ui.playlist;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.midnightmusic.R;
import com.example.midnightmusic.data.db.AppDatabase;
import com.example.midnightmusic.data.db.PlaylistDao;
import com.example.midnightmusic.data.db.SongDao;
import com.example.midnightmusic.data.model.PlaylistWithSongs;
import com.example.midnightmusic.data.model.Song;
import com.example.midnightmusic.data.model.PlaylistSongCrossRef;
import com.example.midnightmusic.data.model.Playlist;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.Date;

public class PlaylistDetailViewModel extends AndroidViewModel {
    private static final String TAG = "PlaylistDetailViewModel";
    private final PlaylistDao playlistDao;
    private final SongDao songDao;
    private final Executor executor;
    private final LiveData<PlaylistWithSongs> playlist;
    private final long playlistId;
    private final Application application;

    public PlaylistDetailViewModel(@NonNull Application application, long playlistId) {
        super(application);
        this.playlistId = playlistId;
        this.application = application;
        AppDatabase db = AppDatabase.getInstance(application);
        playlistDao = db.playlistDao();
        songDao = db.songDao();
        executor = Executors.newSingleThreadExecutor();
        playlist = playlistDao.getPlaylistWithSongs(playlistId);
    }

    public LiveData<PlaylistWithSongs> getPlaylist() {
        return playlist;
    }

    /**
     * Forces a reload of the playlist data
     * @param playlistId ID of the playlist to reload
     */
    public void loadPlaylist(long playlistId) {
        executor.execute(() -> {
            // This will trigger a database reload and the LiveData will be updated
            AppDatabase db = AppDatabase.getInstance(getApplication());
            // Nothing else needed as the LiveData will automatically update when the database changes
        });
    }

    public void toggleLike(Song song) {
        if (song == null || song.getId() == null) return;
        
        executor.execute(() -> {
            try {
                // Get the Liked Songs playlist
                Playlist likedSongsPlaylist = playlistDao.getPlaylistByNameSync(application.getString(R.string.liked_songs));
                
                // Create it if it doesn't exist
                if (likedSongsPlaylist == null) {
                    // Create a new playlist with the required name parameter
                    likedSongsPlaylist = new Playlist(application.getString(R.string.liked_songs));
                    // setCreatedAt with current timestamp as a long value
                    likedSongsPlaylist.setCreatedAt(System.currentTimeMillis());
                    long newPlaylistId = playlistDao.insert(likedSongsPlaylist);
                    likedSongsPlaylist.setId(newPlaylistId);
                }
                
                // Toggle liked status in database
                Song existingSong = songDao.getSongByIdSync(song.getId());
                if (existingSong != null) {
                    // Update the existing song's liked status
                    boolean newLikedStatus = !existingSong.isLiked();
                    existingSong.setLiked(newLikedStatus);
                    songDao.updateLikedStatus(existingSong.getId(), newLikedStatus);
                    
                    // If liked, add to Liked Songs playlist if not already there
                    if (newLikedStatus) {
                        // Add to Liked Songs playlist
                        PlaylistSongCrossRef crossRef = new PlaylistSongCrossRef(likedSongsPlaylist.getId(), existingSong.getId());
                        playlistDao.insert(crossRef);
                    } else {
                        // If unliked, remove from Liked Songs playlist
                        playlistDao.removeSongFromPlaylist(likedSongsPlaylist.getId(), existingSong.getId());
                    }
                } else {
                    // Song doesn't exist in database yet, add it
                    song.setLiked(true);
                    playlistDao.insert(song);
                    
                    // Add to Liked Songs playlist
                    PlaylistSongCrossRef crossRef = new PlaylistSongCrossRef(likedSongsPlaylist.getId(), song.getId());
                    playlistDao.insert(crossRef);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error toggling like status: " + e.getMessage());
            }
        });
    }

    public void removeSongFromPlaylist(Song song) {
        executor.execute(() -> {
            playlistDao.removeSongFromPlaylist(playlistId, song.getId());
        });
    }
} 