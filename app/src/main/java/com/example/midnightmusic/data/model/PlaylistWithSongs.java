package com.example.midnightmusic.data.model;

import androidx.room.Embedded;
import androidx.room.Junction;
import androidx.room.Relation;
import java.util.List;

public class PlaylistWithSongs {
    @Embedded
    public Playlist playlist;

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = @Junction(
            value = PlaylistSongCrossRef.class,
            parentColumn = "playlistId",
            entityColumn = "songId"
        )
    )
    public List<Song> songs;
    
    public int getSongCount() {
        return songs != null ? songs.size() : 0;
    }
    
    public String getFormattedSongCount() {
        int count = getSongCount();
        return count + (count == 1 ? " song" : " songs");
    }
    
    public List<String> getImageUrls(int max) {
        if (songs == null || songs.isEmpty()) {
            return null;
        }
        
        max = Math.min(max, songs.size());
        List<String> urls = new java.util.ArrayList<>(max);
        
        for (int i = 0; i < max; i++) {
            String url = songs.get(i).getImageUrl();
            if (url != null && !url.isEmpty()) {
                urls.add(url);
            }
        }
        
        return urls;
    }
} 