package com.example.midnightmusic.data.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.example.midnightmusic.data.model.Song;
import com.example.midnightmusic.data.model.Playlist;
import com.example.midnightmusic.data.model.PlaylistSongCrossRef;

@Database(
    entities = {
        Song.class, 
        Playlist.class, 
        PlaylistSongCrossRef.class
    }, 
    version = 2, 
    exportSchema = false
)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {
    private static final String DATABASE_NAME = "midnight_music_db";
    private static AppDatabase instance;

    public abstract SongDao songDao();
    public abstract PlaylistDao playlistDao();

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                    context.getApplicationContext(),
                    AppDatabase.class,
                    DATABASE_NAME)
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }

    /**
     * Destroys the current database instance, forcing it to be recreated
     * on the next call to getInstance().
     */
    public static void destroyInstance() {
        instance = null;
    }
} 