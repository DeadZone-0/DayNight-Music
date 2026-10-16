package com.midnight.music.data.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.midnight.music.data.model.Song;
import com.midnight.music.data.model.Playlist;
import com.midnight.music.data.model.PlaylistSongCrossRef;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(
    entities = {
        Song.class, 
        Playlist.class, 
        PlaylistSongCrossRef.class
    }, 
    version = 3, 
    exportSchema = false
)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {
    private static final String DATABASE_NAME = "midnight_music_db";
    private static volatile AppDatabase instance;
    public static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(2);

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
}
