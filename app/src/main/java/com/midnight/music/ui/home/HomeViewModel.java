package com.midnight.music.ui.home;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.midnight.music.data.model.PlaylistWithSongs;
import com.midnight.music.data.model.Song;
import com.midnight.music.data.repository.MusicRepository;
import com.midnight.music.data.repository.RecommendationManager;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ViewModel for HomeFragment.
 * Loads real data from Room DB and Last.fm recommendations.
 */
public class HomeViewModel extends AndroidViewModel {
    private static final String TAG = "HomeViewModel";
    private static final String LASTFM_API_KEY = com.midnight.music.BuildConfig.LASTFM_API_KEY;

    private final MusicRepository repository;
    private final MutableLiveData<String> greeting = new MutableLiveData<>();
    private final LiveData<List<PlaylistWithSongs>> playlists;
    private final LiveData<List<Song>> recentlyPlayed;

    // Recommendation data
    private final MutableLiveData<List<Song>> recommendations = new MutableLiveData<>();
    private final MutableLiveData<List<Song>> trending = new MutableLiveData<>();

    public HomeViewModel(@NonNull Application application) {
        super(application);
        repository = MusicRepository.getInstance(application);
        repository.setLastFmApiKey(LASTFM_API_KEY);

        playlists = repository.getAllPlaylists();
        recentlyPlayed = repository.getRecentlyPlayedSongs();
        updateGreeting();
        loadTrending();
    }

    public LiveData<String> getGreeting() { return greeting; }
    public LiveData<List<PlaylistWithSongs>> getPlaylists() { return playlists; }
    public LiveData<List<Song>> getRecentlyPlayedSongs() { return recentlyPlayed; }
    public LiveData<List<Song>> getRecommendations() { return recommendations; }
    public LiveData<List<Song>> getTrending() { return trending; }

    public void updateGreeting() {
        LocalTime now = LocalTime.now();
        int hour = now.getHour();
        String greetingText;
        if (hour >= 5 && hour < 12) greetingText = "Good Morning";
        else if (hour >= 12 && hour < 17) greetingText = "Good Afternoon";
        else if (hour >= 17 && hour < 21) greetingText = "Good Evening";
        else greetingText = "Good Night";
        greeting.setValue(greetingText);
    }

    /**
     * Load recommendations based on MULTIPLE recently played songs.
     * Picks up to 3 recent songs, fetches similar tracks for each,
     * then merges + deduplicates the results.
     */
    public void loadRecommendations(List<Song> recentSongs) {
        if (recentSongs == null || recentSongs.isEmpty()) {
            recommendations.postValue(new ArrayList<>());
            return;
        }

        // Pick up to 3 seed songs spread across the list
        List<Song> seeds = new ArrayList<>();
        seeds.add(recentSongs.get(0));
        if (recentSongs.size() > 3) seeds.add(recentSongs.get(3));
        if (recentSongs.size() > 7) seeds.add(recentSongs.get(7));

        List<Song> allResults = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        AtomicInteger pending = new AtomicInteger(seeds.size());

        for (Song seed : seeds) {
            if (seed.getSong() == null) {
                if (pending.decrementAndGet() == 0) {
                    mergeRecommendations(allResults, seenIds);
                }
                continue;
            }

            repository.getSimilarTracks(seed,
                    8,
                    new RecommendationManager.RecommendationCallback() {
                        @Override
                        public void onSuccess(List<Song> results) {
                            synchronized (allResults) {
                                for (Song s : results) {
                                    if (seenIds.add(s.getId())) {
                                        allResults.add(s);
                                    }
                                }
                            }
                            if (pending.decrementAndGet() == 0) {
                                mergeRecommendations(allResults, seenIds);
                            }
                        }

                        @Override
                        public void onError(Exception e) {
                            Log.e(TAG, "Seed recommendation failed for: " + seed.getSong(), e);
                            if (pending.decrementAndGet() == 0) {
                                mergeRecommendations(allResults, seenIds);
                            }
                        }
                    });
        }
    }

    private void mergeRecommendations(List<Song> allResults, Set<String> seenIds) {
        if (!allResults.isEmpty()) {
            // Shuffle to mix results from different seeds
            java.util.Collections.shuffle(allResults);
            // Cap at 15
            List<Song> final15 = allResults.size() > 15 ? allResults.subList(0, 15) : allResults;
            Log.d(TAG, "Merged " + final15.size() + " recommendations from multiple seeds");
            recommendations.postValue(new ArrayList<>(final15));
        } else {
            recommendations.postValue(new ArrayList<>());
        }
    }

    /**
     * Load trending tracks from Last.fm chart.
     */
    public void loadTrending() {
        repository.getTrendingTracks(10,
                new RecommendationManager.RecommendationCallback() {
                    @Override
                    public void onSuccess(List<Song> results) {
                        Log.d(TAG, "Got " + results.size() + " trending tracks");
                        trending.postValue(results);
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Trending tracks failed", e);
                        trending.postValue(null);
                    }
                });
    }

    public void saveSongToHistory(Song song) {
        repository.saveSong(song);
    }
}
