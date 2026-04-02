package com.midnight.music.data.repository;

import android.util.Log;

import androidx.annotation.NonNull;

import com.midnight.music.data.model.Song;
import com.midnight.music.data.network.JioSaavnService;
import com.midnight.music.data.network.LastFmService;
import com.midnight.music.data.network.SongResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Manages song recommendations using Last.fm's similar tracks API,
 * then resolves playable audio URLs through the JioSaavn API.
 *
 * Flow: Track + Artist â†’ Last.fm getSimilar â†’ List of track names â†’
 *       JioSaavn search for each â†’ Playable Song objects
 */
public class RecommendationManager {
    private static final String TAG = "RecommendationManager";
    private static volatile RecommendationManager instance;

    private final LastFmService lastFmService;
    private final JioSaavnService saavnService;
    private final Executor executor = Executors.newFixedThreadPool(3);
    private String apiKey;

    public interface RecommendationCallback {
        void onSuccess(List<Song> recommendations);
        void onError(Exception e);
    }

    private RecommendationManager(String lastFmApiKey) {
        this.apiKey = lastFmApiKey;

        Retrofit lastFmRetrofit = new Retrofit.Builder()
                .baseUrl(LastFmService.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        this.lastFmService = lastFmRetrofit.create(LastFmService.class);

        Retrofit saavnRetrofit = new Retrofit.Builder()
                .baseUrl(JioSaavnService.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        this.saavnService = saavnRetrofit.create(JioSaavnService.class);
    }

    public static RecommendationManager getInstance(String apiKey) {
        if (instance == null) {
            synchronized (RecommendationManager.class) {
                if (instance == null) {
                    instance = new RecommendationManager(apiKey);
                }
            }
        }
        return instance;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Get similar tracks based on a currently playing song.
     * 1. Calls Last.fm track.getSimilar
     * 2. For each result, searches JioSaavn to get playable links
     * 3. Returns the resolved Song list
     */
    public void getSimilarTracks(Song song, int limit,
                                  RecommendationCallback callback) {
        if (song == null || song.getId() == null) {
            callback.onError(new IllegalArgumentException("Invalid song for suggestions"));
            return;
        }

        /* 
        // --- RESTORE LAST.FM SUGGESTIONS BY UNCOMMENTING THIS BLOCK AND COMMENTING OUT SAAVN ---
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError(new IllegalStateException("Last.fm API key not set"));
            return;
        }

        lastFmService.getSimilarTracks(song.getSong(), song.getSingers(), apiKey, limit)
                .enqueue(new Callback<LastFmService.SimilarTracksResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<LastFmService.SimilarTracksResponse> call,
                                           @NonNull Response<LastFmService.SimilarTracksResponse> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().similarTracks != null
                                && response.body().similarTracks.tracks != null) {

                            List<LastFmService.LastFmTrack> lastFmTracks =
                                    response.body().similarTracks.tracks;

                            Log.d(TAG, "Last.fm returned " + lastFmTracks.size() + " similar tracks");
                            resolveTracksViaSaavn(lastFmTracks, callback);
                        } else {
                            callback.onError(new Exception("No similar tracks found"));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<LastFmService.SimilarTracksResponse> call,
                                          @NonNull Throwable t) {
                        Log.e(TAG, "Last.fm API call failed", t);
                        callback.onError(new Exception("Last.fm error: " + t.getMessage()));
                    }
                });
        */

        // --- NEW SAAVN SUGGESTIONS IMPLEMENTATION ---
        
        // We need a Retrofit instance that uses SaavnApiService for suggestions
        Retrofit saavnRetrofit = new Retrofit.Builder()
                .baseUrl(com.midnight.music.data.network.SaavnApiService.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        com.midnight.music.data.network.SaavnApiService newSaavnApi = saavnRetrofit.create(com.midnight.music.data.network.SaavnApiService.class);

        newSaavnApi.getSimilarSongs(song.getId(), limit)
                .enqueue(new Callback<com.midnight.music.data.network.SaavnSuggestionsResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<com.midnight.music.data.network.SaavnSuggestionsResponse> call,
                                           @NonNull Response<com.midnight.music.data.network.SaavnSuggestionsResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                            List<Song> resolvedSongs = new ArrayList<>();
                            for (com.midnight.music.data.network.SaavnSongResult result : response.body().getData()) {
                                resolvedSongs.add(result.toSong());
                            }
                            callback.onSuccess(resolvedSongs);
                        } else {
                            callback.onError(new Exception("No similar tracks found via Saavn API"));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<com.midnight.music.data.network.SaavnSuggestionsResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "Saavn suggestions API call failed", t);
                        callback.onError(new Exception("Saavn API error: " + t.getMessage()));
                    }
                });
    }

    /**
     * Get trending/chart tracks from Last.fm, resolved through JioSaavn.
     */
    public void getTrendingTracks(int limit, RecommendationCallback callback) {
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError(new IllegalStateException("Last.fm API key not set"));
            return;
        }

        lastFmService.getChartTopTracks(apiKey, limit)
                .enqueue(new Callback<LastFmService.ChartTopTracksResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<LastFmService.ChartTopTracksResponse> call,
                                           @NonNull Response<LastFmService.ChartTopTracksResponse> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().tracks != null
                                && response.body().tracks.trackList != null) {

                            List<LastFmService.LastFmTrack> tracks =
                                    response.body().tracks.trackList;

                            Log.d(TAG, "Last.fm chart returned " + tracks.size() + " tracks");
                            resolveTracksViaSaavn(tracks, callback);
                        } else {
                            callback.onError(new Exception("No trending tracks found"));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<LastFmService.ChartTopTracksResponse> call,
                                          @NonNull Throwable t) {
                        callback.onError(new Exception("Last.fm error: " + t.getMessage()));
                    }
                });
    }

    /**
     * Get top tracks by a specific artist from Last.fm, resolved through JioSaavn.
     */
    public void getArtistTopTracks(String artist, int limit, RecommendationCallback callback) {
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError(new IllegalStateException("Last.fm API key not set"));
            return;
        }

        lastFmService.getArtistTopTracks(artist, apiKey, limit)
                .enqueue(new Callback<LastFmService.ArtistTopTracksResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<LastFmService.ArtistTopTracksResponse> call,
                                           @NonNull Response<LastFmService.ArtistTopTracksResponse> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().topTracks != null
                                && response.body().topTracks.tracks != null) {

                            List<LastFmService.LastFmTrack> tracks =
                                    response.body().topTracks.tracks;

                            Log.d(TAG, "Last.fm artist top tracks: " + tracks.size());
                            resolveTracksViaSaavn(tracks, callback);
                        } else {
                            callback.onError(new Exception("No top tracks found for artist"));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<LastFmService.ArtistTopTracksResponse> call,
                                          @NonNull Throwable t) {
                        callback.onError(new Exception("Last.fm error: " + t.getMessage()));
                    }
                });
    }

    /**
     * Takes a list of Last.fm tracks and searches each on JioSaavn to get
     * playable Song objects with audio URLs.
     */
    private void resolveTracksViaSaavn(List<LastFmService.LastFmTrack> lastFmTracks,
                                       RecommendationCallback callback) {
        executor.execute(() -> {
            List<Song> resolvedSongs = Collections.synchronizedList(new ArrayList<>());
            Set<String> addedIds = Collections.synchronizedSet(new HashSet<>());
            int maxResolve = Math.min(lastFmTracks.size(), 15); // Cap at 15 to avoid rate limits
            CountDownLatch latch = new CountDownLatch(maxResolve);

            for (int i = 0; i < maxResolve; i++) {
                LastFmService.LastFmTrack track = lastFmTracks.get(i);
                String query = track.name + " " + track.getArtistName();

                saavnService.searchSongs(query, false).enqueue(new Callback<List<SongResponse>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<SongResponse>> call,
                                           @NonNull Response<List<SongResponse>> response) {
                        try {
                            if (response.isSuccessful() && response.body() != null
                                    && !response.body().isEmpty()) {
                                // Take the first (best match) result
                                Song song = response.body().get(0).toSong();
                                if (song.getMediaUrl() != null && addedIds.add(song.getId())) {
                                    resolvedSongs.add(song);
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error resolving track: " + track.name, e);
                        } finally {
                            latch.countDown();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<SongResponse>> call,
                                          @NonNull Throwable t) {
                        Log.e(TAG, "JioSaavn search failed for: " + track.name, t);
                        latch.countDown();
                    }
                });
            }

            try {
                latch.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Log.e(TAG, "Timeout waiting for JioSaavn results", e);
            }

            if (!resolvedSongs.isEmpty()) {
                callback.onSuccess(new ArrayList<>(resolvedSongs));
            } else {
                callback.onError(new Exception("Could not resolve any tracks"));
            }
        });
    }
}
