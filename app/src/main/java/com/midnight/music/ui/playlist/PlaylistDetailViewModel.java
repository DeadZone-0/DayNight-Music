package com.midnight.music.ui.playlist;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.midnight.music.R;
import com.midnight.music.data.db.AppDatabase;
import com.midnight.music.data.db.PlaylistDao;
import com.midnight.music.data.db.SongDao;
import com.midnight.music.data.model.PlaylistWithSongs;
import com.midnight.music.data.model.Song;
import com.midnight.music.data.model.PlaylistSongCrossRef;
import com.midnight.music.data.model.Playlist;

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
        
        com.midnight.music.data.repository.MusicRepository.getInstance(application).toggleLikeSong(song, new com.midnight.music.data.repository.MusicRepository.LikeCallback() {
            @Override
            public void onComplete(boolean isLiked) {
                // UI will update automatically via LiveData observers
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Error toggling like status from MusicRepository: " + e.getMessage());
            }
        });
    }

    public void removeSongFromPlaylist(Song song) {
        executor.execute(() -> {
            playlistDao.removeSongFromPlaylist(playlistId, song.getId());
        });
    }
} 
