package com.midnight.music.ui.playlist;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.midnight.music.R;
import com.midnight.music.data.db.AppDatabase;
import com.midnight.music.data.db.PlaylistDao;
import com.midnight.music.data.db.SongDao;
import com.midnight.music.data.model.PlaylistWithSongs;
import com.midnight.music.data.model.Song;
import com.midnight.music.data.model.PlaylistSongCrossRef;
import com.midnight.music.data.model.Playlist;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class PlaylistDetailViewModel extends AndroidViewModel {
    private static final String TAG = "PlaylistDetailViewModel";
    private final PlaylistDao playlistDao;
    private final SongDao songDao;
    private final Executor executor;
    private final MediatorLiveData<PlaylistWithSongs> playlist = new MediatorLiveData<>();
    private final long playlistId;
    private final Application application;
    private boolean isSpecialLikedSongs = false;
    private PlaylistWithSongs lastPws;
    private List<Song> lastLikedSongs;

    public PlaylistDetailViewModel(@NonNull Application application, long playlistId) {
        super(application);
        this.playlistId = playlistId;
        this.application = application;
        AppDatabase db = AppDatabase.getInstance(application);
        playlistDao = db.playlistDao();
        songDao = db.songDao();
        executor = AppDatabase.databaseWriteExecutor;

        String likedSongsName = application.getString(R.string.liked_songs);

        // Check if this playlist is "Liked Songs" and trigger self-healing
        executor.execute(() -> {
            try {
                Playlist liked = playlistDao.getPlaylistByNameSync(likedSongsName);
                if (liked != null && liked.getId() == playlistId) {
                    isSpecialLikedSongs = true;
                    // Self-healing: Ensure all songs marked liked exist in cross-ref
                    List<Song> songs = songDao.getLikedSongsSync();
                    if (songs != null) {
                        for (Song s : songs) {
                            playlistDao.insert(new PlaylistSongCrossRef(liked.getId(), s.getId()));
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking/syncing liked songs in ViewModel", e);
            }
        });

        LiveData<PlaylistWithSongs> roomPlaylist = playlistDao.getPlaylistWithSongs(playlistId);
        LiveData<List<Song>> roomLikedSongs = songDao.getLikedSongs();

        playlist.addSource(roomPlaylist, pws -> {
            lastPws = pws;
            mergeAndEmit();
        });

        playlist.addSource(roomLikedSongs, likedSongs -> {
            lastLikedSongs = likedSongs;
            mergeAndEmit();
        });
    }

    private void mergeAndEmit() {
        if (lastPws == null) {
            return;
        }
        boolean isLiked = isSpecialLikedSongs ||
                (lastPws.playlist != null && application.getString(R.string.liked_songs).equals(lastPws.playlist.getName()));

        if (isLiked) {
            isSpecialLikedSongs = true;
            PlaylistWithSongs copy = new PlaylistWithSongs();
            copy.playlist = lastPws.playlist;
            if (lastLikedSongs != null) {
                copy.songs = new ArrayList<>(lastLikedSongs);
            } else if (lastPws.songs != null) {
                copy.songs = new ArrayList<>(lastPws.songs);
            } else {
                copy.songs = new ArrayList<>();
            }
            playlist.setValue(copy);
        } else {
            playlist.setValue(lastPws);
        }
    }

    public LiveData<PlaylistWithSongs> getPlaylist() {
        return playlist;
    }

    /**
     * Forces a reload of the playlist data
     * @param playlistId ID of the playlist to reload
     */
    public void loadPlaylist(long playlistId) {
        // Room LiveData observes tables automatically; no manual polling needed
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
