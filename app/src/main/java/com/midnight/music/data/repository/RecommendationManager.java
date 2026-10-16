package com.midnight.music.data.repository;

import android.util.Log;

import androidx.annotation.NonNull;

import com.midnight.music.data.model.Song;
import com.midnight.music.data.network.JioSaavnService;
import com.midnight.music.data.network.LastFmService;
import com.midnight.music.data.network.NetworkClient;
import com.midnight.music.data.network.SaavnApiService;
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
    private final SaavnApiService saavnApiService;
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
                .client(NetworkClient.getInstance())
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        this.lastFmService = lastFmRetrofit.create(LastFmService.class);

        Retrofit saavnRetrofit = new Retrofit.Builder()
                .baseUrl(JioSaavnService.BASE_URL)
                .client(NetworkClient.getInstance())
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        this.saavnService = saavnRetrofit.create(JioSaavnService.class);

        Retrofit midnightRetrofit = new Retrofit.Builder()
                .baseUrl(SaavnApiService.BASE_URL)
                .client(NetworkClient.getInstance())
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        this.saavnApiService = midnightRetrofit.create(SaavnApiService.class);
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
     * Get Spotify-quality similar tracks based on a seed song.
     * Guaranteed to never return empty as long as network is available.
     *
     * Multi-tier strategy:
     * 1. Saavn Direct Suggestions API (api/songs/{id}/suggestions).
     * 2. Primary Artist radio search (api/search/songs?query={primary_artist}).
     * 3. Album / Collaborator search.
     * 4. Language Top Hits / Global Trending Hits fallback.
     */
    public void getSimilarTracks(Song seedSong, int limit,
                                  RecommendationCallback callback) {
        if (seedSong == null) {
            fetchTrendingDirectFromSaavn(limit, callback);
            return;
        }

        executor.execute(() -> {
            List<Song> finalResults = new ArrayList<>();
            Set<String> seenIds = new HashSet<>();
            Set<String> seenTitles = new HashSet<>();

            if (seedSong.getId() != null) {
                seenIds.add(seedSong.getId());
            }
            if (seedSong.getSong() != null) {
                seenTitles.add(normalizeTitle(seedSong.getSong()));
            }

            // Step 1: Direct Saavn Suggestions API
            if (seedSong.getId() != null && !seedSong.getId().isEmpty()) {
                try {
                    Response<com.midnight.music.data.network.SaavnSuggestionsResponse> resp =
                            saavnApiService.getSimilarSongs(seedSong.getId(), limit).execute();
                    if (resp.isSuccessful() && resp.body() != null && resp.body().getData() != null) {
                        for (com.midnight.music.data.network.SaavnSongResult res : resp.body().getData()) {
                            Song s = res.toSong();
                            addIfValid(s, finalResults, seenIds, seenTitles);
                            if (finalResults.size() >= limit) break;
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Direct Saavn suggestions failed, falling back to smart artist radio", e);
                }
            }

            // Step 2: Primary Artist Search (if suggestions didn't fill the limit)
            if (finalResults.size() < limit && seedSong.getSingers() != null && !seedSong.getSingers().isEmpty()) {
                String primaryArtist = extractPrimaryArtist(seedSong.getSingers());
                if (!primaryArtist.isEmpty()) {
                    try {
                        Response<com.midnight.music.data.network.SaavnSearchResponse> artistResp =
                                saavnApiService.searchSongs(primaryArtist, 1, Math.max(limit, 15)).execute();
                        if (artistResp.isSuccessful() && artistResp.body() != null
                                && artistResp.body().getData() != null
                                && artistResp.body().getData().getResults() != null) {
                            for (com.midnight.music.data.network.SaavnSongResult res : artistResp.body().getData().getResults()) {
                                Song s = res.toSong();
                                addIfValid(s, finalResults, seenIds, seenTitles);
                                if (finalResults.size() >= limit) break;
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Artist radio search failed", e);
                    }
                }
            }

            // Step 3: Album / Collaborators (if still need more)
            if (finalResults.size() < limit && seedSong.getAlbum() != null && !seedSong.getAlbum().isEmpty()
                    && !seedSong.getAlbum().equalsIgnoreCase(seedSong.getSong())) {
                try {
                    Response<com.midnight.music.data.network.SaavnSearchResponse> albumResp =
                            saavnApiService.searchSongs(seedSong.getAlbum(), 1, 10).execute();
                    if (albumResp.isSuccessful() && albumResp.body() != null
                            && albumResp.body().getData() != null
                            && albumResp.body().getData().getResults() != null) {
                        for (com.midnight.music.data.network.SaavnSongResult res : albumResp.body().getData().getResults()) {
                            Song s = res.toSong();
                            addIfValid(s, finalResults, seenIds, seenTitles);
                            if (finalResults.size() >= limit) break;
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Album search failed", e);
                }
            }

            // Step 4: Language Top Hits or Global Hits fallback
            if (finalResults.size() < Math.min(limit, 4)) {
                String query = (seedSong.getLanguage() != null && !seedSong.getLanguage().isEmpty())
                        ? seedSong.getLanguage() + " Top Hits"
                        : "Top Hits";
                try {
                    Response<com.midnight.music.data.network.SaavnSearchResponse> trendingResp =
                            saavnApiService.searchSongs(query, 1, 15).execute();
                    if (trendingResp.isSuccessful() && trendingResp.body() != null
                            && trendingResp.body().getData() != null
                            && trendingResp.body().getData().getResults() != null) {
                        for (com.midnight.music.data.network.SaavnSongResult res : trendingResp.body().getData().getResults()) {
                            Song s = res.toSong();
                            addIfValid(s, finalResults, seenIds, seenTitles);
                            if (finalResults.size() >= limit) break;
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Language trending search failed", e);
                }
            }

            if (!finalResults.isEmpty()) {
                callback.onSuccess(finalResults);
            } else {
                fetchTrendingDirectFromSaavn(limit, callback);
            }
        });
    }

    private void addIfValid(Song song, List<Song> list, Set<String> seenIds, Set<String> seenTitles) {
        if (song == null || song.getId() == null) return;
        if (song.getMediaUrl() == null || song.getMediaUrl().trim().isEmpty()) return;
        if (seenIds.contains(song.getId())) return;
        String norm = normalizeTitle(song.getSong());
        if (!norm.isEmpty() && seenTitles.contains(norm)) return;

        seenIds.add(song.getId());
        if (!norm.isEmpty()) seenTitles.add(norm);
        list.add(song);
    }

    private String normalizeTitle(String title) {
        if (title == null) return "";
        return title.toLowerCase()
                .replaceAll("\\(.*?\\)", "")
                .replaceAll("\\[.*?\\]", "")
                .replaceAll("[^a-zA-Z0-9]", "")
                .trim();
    }

    private String extractPrimaryArtist(String singers) {
        if (singers == null || singers.trim().isEmpty()) return "";
        String first = singers.split(",")[0];
        first = first.split("(?i)\\s+feat\\.?\\s+")[0];
        first = first.split("(?i)\\s+ft\\.?\\s+")[0];
        first = first.split("&")[0];
        first = first.split("/")[0];
        return first.trim();
    }

    /**
     * Get trending/chart tracks from Last.fm, resolved through JioSaavn.
     * Falls back directly to JioSaavn if Last.fm key is missing or fails.
     */
    public void getTrendingTracks(int limit, RecommendationCallback callback) {
        // ponytail: fallback directly to JioSaavn so the home screen always populates even without Last.fm
        if (apiKey == null || apiKey.isEmpty()) {
            fetchTrendingDirectFromSaavn(limit, callback);
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
                            fetchTrendingDirectFromSaavn(limit, callback);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<LastFmService.ChartTopTracksResponse> call,
                                          @NonNull Throwable t) {
                        fetchTrendingDirectFromSaavn(limit, callback);
                    }
                });
    }

    private void fetchTrendingDirectFromSaavn(int limit, RecommendationCallback callback) {
        Log.d(TAG, "Fetching trending tracks directly from Midnight Saavn API");
        saavnApiService.searchSongs("Top Hits", 1, limit).enqueue(new Callback<com.midnight.music.data.network.SaavnSearchResponse>() {
            @Override
            public void onResponse(@NonNull Call<com.midnight.music.data.network.SaavnSearchResponse> call,
                                   @NonNull Response<com.midnight.music.data.network.SaavnSearchResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null && response.body().getData().getResults() != null) {
                    List<Song> songs = new ArrayList<>();
                    for (com.midnight.music.data.network.SaavnSongResult sr : response.body().getData().getResults()) {
                        Song s = sr.toSong();
                        if (s.getMediaUrl() != null && !s.getMediaUrl().isEmpty()) {
                            songs.add(s);
                            if (songs.size() >= limit) break;
                        }
                    }
                    if (!songs.isEmpty()) {
                        callback.onSuccess(songs);
                        return;
                    }
                }
                callback.onError(new Exception("No trending tracks found"));
            }

            @Override
            public void onFailure(@NonNull Call<com.midnight.music.data.network.SaavnSearchResponse> call, @NonNull Throwable t) {
                callback.onError(new Exception("Trending fetch error: " + t.getMessage()));
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

                saavnApiService.searchSongs(query, 1, 1).enqueue(new Callback<com.midnight.music.data.network.SaavnSearchResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<com.midnight.music.data.network.SaavnSearchResponse> call,
                                           @NonNull Response<com.midnight.music.data.network.SaavnSearchResponse> response) {
                        try {
                            if (response.isSuccessful() && response.body() != null
                                    && response.body().getData() != null
                                    && response.body().getData().getResults() != null
                                    && !response.body().getData().getResults().isEmpty()) {
                                Song song = response.body().getData().getResults().get(0).toSong();
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
                    public void onFailure(@NonNull Call<com.midnight.music.data.network.SaavnSearchResponse> call,
                                          @NonNull Throwable t) {
                        Log.e(TAG, "Saavn search failed for: " + track.name, t);
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
