package com.example.midnightmusic.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.example.midnightmusic.data.model.Playlist;
import com.example.midnightmusic.data.model.PlaylistSongCrossRef;
import com.example.midnightmusic.data.model.PlaylistWithSongs;
import com.example.midnightmusic.data.model.Song;

import java.util.List;

@Dao
public interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Playlist playlist);

    @Update
    void update(Playlist playlist);

    @Delete
    void delete(Playlist playlist);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Song song);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(PlaylistSongCrossRef crossRef);
    
    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId AND songId = :songId")
    void removeSongFromPlaylist(long playlistId, String songId);

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    LiveData<PlaylistWithSongs> getPlaylistWithSongs(long playlistId);
    
    @Transaction
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    LiveData<List<PlaylistWithSongs>> getAllPlaylistsWithSongs();
    
    @Query("SELECT * FROM playlists WHERE name = :name LIMIT 1")
    LiveData<Playlist> getPlaylistByName(String name);
    
    @Query("SELECT * FROM playlists WHERE name = :name LIMIT 1")
    Playlist getPlaylistByNameSync(String name);

    @Query("SELECT COUNT(*) FROM playlist_song_cross_ref WHERE playlistId = :playlistId")
    LiveData<Integer> getSongCount(long playlistId);

    @Query("SELECT EXISTS(SELECT 1 FROM playlists WHERE name = :name LIMIT 1)")
    boolean isPlaylistExists(String name);
    
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    LiveData<Playlist> getPlaylistById(long playlistId);

    @Query("SELECT * FROM playlists ORDER BY name ASC")
    LiveData<List<Playlist>> getAllPlaylists();

    @Query("SELECT * FROM playlists WHERE name LIKE :query ORDER BY name ASC")
    LiveData<List<Playlist>> searchPlaylists(String query);

    @Query("UPDATE playlists SET name = :newName WHERE id = :playlistId")
    void updatePlaylistName(long playlistId, String newName);

    @Query("SELECT COUNT(*) FROM playlists")
    LiveData<Integer> getPlaylistCount();

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    Playlist getPlaylistByIdSync(long id);
}