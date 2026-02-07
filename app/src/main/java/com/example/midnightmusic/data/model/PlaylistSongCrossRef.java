package com.example.midnightmusic.data.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(
    tableName = "playlist_song_cross_ref",
    primaryKeys = {"playlistId", "songId"},
    indices = {
        @Index("songId")
    },
    foreignKeys = {
        @ForeignKey(
            entity = Playlist.class,
            parentColumns = "id",
            childColumns = "playlistId",
            onDelete = ForeignKey.CASCADE
        ),
        @ForeignKey(
            entity = Song.class,
            parentColumns = "id",
            childColumns = "songId",
            onDelete = ForeignKey.CASCADE
        )
    }
)
public class PlaylistSongCrossRef {
    private long playlistId;
    
    @NonNull
    private String songId;
    
    private long addedAt;

    public PlaylistSongCrossRef(long playlistId, @NonNull String songId) {
        this.playlistId = playlistId;
        this.songId = songId;
        this.addedAt = System.currentTimeMillis();
    }

    public long getPlaylistId() {
        return playlistId;
    }

    public void setPlaylistId(long playlistId) {
        this.playlistId = playlistId;
    }

    @NonNull
    public String getSongId() {
        return songId;
    }

    public void setSongId(@NonNull String songId) {
        this.songId = songId;
    }

    public long getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(long addedAt) {
        this.addedAt = addedAt;
    }
} 