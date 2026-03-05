package com.example.midnightmusic.ui.library;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.midnightmusic.data.model.Playlist;
import com.example.midnightmusic.data.model.PlaylistWithSongs;
import com.example.midnightmusic.data.model.Song;
import com.example.midnightmusic.data.repository.MusicRepository;

import java.util.List;

public class PlaylistViewModel extends AndroidViewModel {
    private final MusicRepository repository;

    public PlaylistViewModel(@NonNull Application application) {
        super(application);
        repository = MusicRepository.getInstance(application);
    }

    public LiveData<List<PlaylistWithSongs>> getAllPlaylists() {
        return repository.getAllPlaylists();
    }

    public LiveData<PlaylistWithSongs> getPlaylistWithSongs(long playlistId) {
        return repository.getPlaylist(playlistId);
    }

    public LiveData<Integer> getLikedSongsCount() {
        return repository.getLikedSongsCount();
    }

    public void createPlaylist(String name) {
        repository.createPlaylist(name, new MusicRepository.PlaylistCallback() {
            @Override
            public void onSuccess(long playlistId) {
                // Playlist created successfully
            }

            @Override
            public void onError(Exception e) {
                // Handle error
            }
        });
    }

    public void renamePlaylist(Playlist playlist, String newName) {
        repository.renamePlaylist(playlist.getId(), newName, null);
    }

    public void deletePlaylist(Playlist playlist) {
        repository.deletePlaylist(playlist, null);
    }

    public void addSongToPlaylist(long playlistId, Song song) {
        repository.addSongToPlaylist(playlistId, song, null);
    }

    /**
     * Create a playlist and batch-import songs into it.
     * Uses createPlaylist callback to get the ID, then batch-inserts all songs.
     */
    public void createPlaylistAndImport(String name, List<Song> songs, Runnable onComplete) {
        repository.createPlaylist(name, new MusicRepository.PlaylistCallback() {
            @Override
            public void onSuccess(long playlistId) {
                repository.importSongsToPlaylist(playlistId, songs, onComplete);
            }

            @Override
            public void onError(Exception e) {
                // If playlist already exists, ignore
            }
        });
    }

    public void removeSongFromPlaylist(long playlistId, Song song) {
        repository.removeSongFromPlaylist(playlistId, song.getId(), null);
    }

    public void getOrCreateLikedPlaylist(MusicRepository.PlaylistCallback callback) {
        repository.getOrCreateLikedPlaylist(callback);
    }
}