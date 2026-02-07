package com.example.midnightmusic.ui.home;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.midnightmusic.data.model.PlaylistWithSongs;
import com.example.midnightmusic.data.model.Song;
import com.example.midnightmusic.data.repository.MusicRepository;

import java.time.LocalTime;
import java.util.List;

/**
 * ViewModel for HomeFragment.
 * Manages UI state and survives configuration changes.
 */
public class HomeViewModel extends AndroidViewModel {
    private final MusicRepository repository;
    private final MutableLiveData<String> greeting = new MutableLiveData<>();
    private final LiveData<List<PlaylistWithSongs>> playlists;
    private final LiveData<List<Song>> recentlyPlayed;

    public HomeViewModel(@NonNull Application application) {
        super(application);
        repository = MusicRepository.getInstance(application);
        playlists = repository.getAllPlaylists();
        recentlyPlayed = repository.getRecentlyPlayedSongs();
        updateGreeting();
    }

    public LiveData<String> getGreeting() {
        return greeting;
    }

    public LiveData<List<PlaylistWithSongs>> getPlaylists() {
        return playlists;
    }

    public LiveData<List<Song>> getRecentlyPlayedSongs() {
        return recentlyPlayed;
    }

    public void updateGreeting() {
        LocalTime now = LocalTime.now();
        int hour = now.getHour();

        String greetingText;
        if (hour >= 5 && hour < 12) {
            greetingText = "Good Morning";
        } else if (hour >= 12 && hour < 17) {
            greetingText = "Good Afternoon";
        } else if (hour >= 17 && hour < 21) {
            greetingText = "Good Evening";
        } else {
            greetingText = "Good Night";
        }
        greeting.setValue(greetingText);
    }

    public void saveSongToHistory(Song song) {
        repository.saveSong(song);
    }
}
