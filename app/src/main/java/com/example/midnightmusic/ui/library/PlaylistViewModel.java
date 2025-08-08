package com.example.midnightmusic.ui.library;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.midnightmusic.data.db.AppDatabase;
import com.example.midnightmusic.data.db.PlaylistDao;
import com.example.midnightmusic.data.model.Playlist;
import com.example.midnightmusic.data.model.PlaylistSongCrossRef;
import com.example.midnightmusic.data.model.PlaylistWithSongs;
import com.example.midnightmusic.data.model.Song;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class PlaylistViewModel extends AndroidViewModel {
    private final PlaylistDao playlistDao;
    private final Executor executor;
    
    public PlaylistViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getInstance(application);
        playlistDao = db.playlistDao();
        executor = Executors.newSingleThreadExecutor();
    }
    
    public LiveData<List<PlaylistWithSongs>> getAllPlaylists() {
        return playlistDao.getAllPlaylistsWithSongs();
    }
    
    public LiveData<PlaylistWithSongs> getPlaylistWithSongs(long playlistId) {
        return playlistDao.getPlaylistWithSongs(playlistId);
    }
    
    public void createPlaylist(String name) {
        executor.execute(() -> {
            Playlist playlist = new Playlist(name);
            playlistDao.insert(playlist);
        });
    }
    
    public void renamePlaylist(Playlist playlist, String newName) {
        executor.execute(() -> {
            playlist.setName(newName);
            playlistDao.update(playlist);
        });
    }
    
    public void deletePlaylist(Playlist playlist) {
        executor.execute(() -> {
            playlistDao.delete(playlist);
        });
    }
    
    public void addSongToPlaylist(long playlistId, Song song) {
        executor.execute(() -> {
            // Make sure song is in the database
            playlistDao.insert(song);
            
            // Create cross reference
            PlaylistSongCrossRef crossRef = new PlaylistSongCrossRef(playlistId, song.getId());
            playlistDao.insert(crossRef);
        });
    }
    
    public void removeSongFromPlaylist(long playlistId, Song song) {
        executor.execute(() -> {
            playlistDao.removeSongFromPlaylist(playlistId, song.getId());
        });
    }
}