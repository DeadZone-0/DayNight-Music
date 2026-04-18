package com.midnight.music.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.midnight.music.data.model.Song;

import java.util.List;

@Dao
public interface SongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Song song);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Song> songs);

    @Update
    void update(Song song);

    @Delete
    void delete(Song song);

    @Query("SELECT * FROM songs WHERE id = :songId")
    LiveData<Song> getSongById(String songId);

    @Query("SELECT * FROM songs WHERE id = :songId")
    Song getSongByIdSync(String songId);

    @Query("SELECT * FROM songs WHERE isLiked = 1 ORDER BY timestamp DESC")
    LiveData<List<Song>> getLikedSongs();

    @Query("SELECT * FROM songs WHERE isLiked = 1 ORDER BY timestamp DESC")
    List<Song> getLikedSongsSync();

    @Query("SELECT COUNT(*) FROM songs WHERE id = :songId AND isLiked = 1")
    boolean isSongLiked(String songId);

    @Query("SELECT * FROM songs WHERE id IN (:songIds)")
    LiveData<List<Song>> getSongsByIds(List<String> songIds);

    @Query("UPDATE songs SET isLiked = :isLiked WHERE id = :songId")
    void updateLikedStatus(String songId, boolean isLiked);

    @Query("SELECT COUNT(*) FROM songs WHERE isLiked = 1")
    LiveData<Integer> getLikedSongsCount();

    @Query("DELETE FROM songs WHERE isLiked = 0 AND isDownloaded = 0")
    void deleteUnlikedSongs();

    @Query("SELECT * FROM songs WHERE timestamp > 0 ORDER BY timestamp DESC LIMIT :limit")
    LiveData<List<Song>> getRecentSongs(int limit);

    @Query("SELECT * FROM songs WHERE isDownloaded = 1 ORDER BY timestamp DESC")
    LiveData<List<Song>> getDownloadedSongs();

    @Query("SELECT * FROM songs WHERE isDownloaded = 1 ORDER BY timestamp DESC")
    List<Song> getDownloadedSongsSync();

    @Query("SELECT * FROM songs")
    List<Song> getAllSongsSync();

    @Query("SELECT COUNT(*) FROM songs WHERE id = :songId AND isDownloaded = 1")
    boolean isSongDownloaded(String songId);

    @Query("UPDATE songs SET isDownloaded = :isDownloaded, localPath = :localPath WHERE id = :songId")
    void updateDownloadStatus(String songId, boolean isDownloaded, String localPath);
}
