package com.example.midnightmusic.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.midnightmusic.data.model.Song;
import com.example.midnightmusic.data.network.JioSaavnService;
import com.example.midnightmusic.data.network.SongResponse;
import com.example.midnightmusic.data.network.PlaylistResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class PlaylistImportManager {
    private static final String TAG = "PlaylistImportManager";
    private static volatile PlaylistImportManager instance;
    private final JioSaavnService apiService;
    private final MusicRepository musicRepository;
    private final Executor networkExecutor;

    public interface ImportCallback {
        void onProgress(int current, int total, String currentSongName);

        void onSuccess(List<Song> songs);

        void onError(Exception e);
    }

    private PlaylistImportManager(Context context) {
        this.musicRepository = MusicRepository.getInstance(context);
        this.networkExecutor = Executors.newFixedThreadPool(4); // Separate executor for network tasks

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(JioSaavnService.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        this.apiService = retrofit.create(JioSaavnService.class);
    }

    public static PlaylistImportManager getInstance(Context context) {
        if (instance == null) {
            synchronized (PlaylistImportManager.class) {
                if (instance == null) {
                    instance = new PlaylistImportManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public void importPlaylist(String url, ImportCallback callback) {
        if (url == null || url.isEmpty()) {
            callback.onError(new IllegalArgumentException("URL cannot be empty"));
            return;
        }

        if (url.contains("jiosaavn.com")) {
            importJioSaavnPlaylist(url, callback);
        } else if (url.contains("spotify.com")) {
            importSpotifyPlaylist(url, callback);
        } else {
            callback.onError(new IllegalArgumentException("Unsupported playlist URL"));
        }
    }

    private void importJioSaavnPlaylist(String url, ImportCallback callback) {
        apiService.getPlaylist(url, false).enqueue(new Callback<PlaylistResponse>() {
            @Override
            public void onResponse(@NonNull Call<PlaylistResponse> call,
                    @NonNull Response<PlaylistResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getSongs() != null) {
                    List<Song> songs = new ArrayList<>();
                    for (SongResponse songResponse : response.body().getSongs()) {
                        songs.add(songResponse.toSong());
                    }
                    callback.onSuccess(songs);
                } else {
                    callback.onError(new Exception("Failed to import JioSaavn playlist: " + response.code()));
                }
            }

            @Override
            public void onFailure(@NonNull Call<PlaylistResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "JioSaavn playlist import failed", t);
                callback.onError(new Exception("Network error: " + t.getMessage()));
            }
        });
    }

    private void importSpotifyPlaylist(String url, ImportCallback callback) {
        networkExecutor.execute(() -> {
            try {
                // 1. Extract playlist ID from the URL
                String playlistId = extractSpotifyPlaylistId(url);
                if (playlistId == null) {
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .post(() -> callback.onError(new Exception("Invalid Spotify playlist URL")));
                    return;
                }

                // 2. Fetch the embed page which has __NEXT_DATA__ with track info
                String embedUrl = "https://open.spotify.com/embed/playlist/" + playlistId;
                java.net.URL spotifyUrl = new java.net.URL(embedUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) spotifyUrl.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13)");

                java.io.BufferedReader in = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream()));
                String inputLine;
                StringBuilder content = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
                in.close();
                conn.disconnect();

                String html = content.toString();
                List<String> searchQueries = new ArrayList<>();

                // 3. Parse __NEXT_DATA__ JSON which contains title+subtitle pairs
                java.util.regex.Pattern nextDataPattern = java.util.regex.Pattern
                        .compile("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.+?)</script>");
                java.util.regex.Matcher nextDataMatcher = nextDataPattern.matcher(html);

                if (nextDataMatcher.find()) {
                    String jsonStr = nextDataMatcher.group(1);

                    // Extract "title":"TrackName","subtitle":"ArtistName" pairs
                    java.util.regex.Pattern trackPattern = java.util.regex.Pattern
                            .compile("\"title\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"subtitle\"\\s*:\\s*\"([^\"]+)\"");
                    java.util.regex.Matcher trackMatcher = trackPattern.matcher(jsonStr);

                    boolean isFirst = true; // Skip first match (playlist title)
                    while (trackMatcher.find()) {
                        if (isFirst) {
                            isFirst = false;
                            continue;
                        }
                        String trackName = trackMatcher.group(1);
                        String artistName = trackMatcher.group(2);
                        // Clean up unicode escapes
                        trackName = trackName.replace("\\u00a0", " ").replace("\\u2019", "'");
                        artistName = artistName.replace("\\u00a0", " ").replace("\\u2019", "'");
                        String query = trackName + " " + artistName;
                        if (!searchQueries.contains(query)) {
                            searchQueries.add(query);
                        }
                    }
                }

                if (searchQueries.isEmpty()) {
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .post(() -> callback.onError(new Exception(
                                    "Could not find any tracks in the Spotify link. Make sure the playlist is public.")));
                    return;
                }

                Log.d(TAG, "Extracted " + searchQueries.size() + " tracks from Spotify. Resolving via JioSaavn...");

                // 4. Resolve via JioSaavn Search
                resolveTracksViaSaavn(searchQueries, callback);

            } catch (Exception e) {
                Log.e(TAG, "Failed to scrape Spotify: ", e);
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(() -> callback
                                .onError(new Exception("Failed to import Spotify playlist: " + e.getMessage())));
            }
        });
    }

    private String extractSpotifyPlaylistId(String url) {
        // Handles: https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=...
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("playlist/([a-zA-Z0-9]+)");
        java.util.regex.Matcher m = p.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    private void resolveTracksViaSaavn(List<String> queries, ImportCallback callback) {
        List<Song> resolvedSongs = java.util.Collections.synchronizedList(new ArrayList<>());
        java.util.Set<String> addedIds = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
        int total = queries.size();

        // Report initial progress
        new android.os.Handler(android.os.Looper.getMainLooper())
                .post(() -> callback.onProgress(0, total, "Starting resolution..."));

        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(total);
        java.util.concurrent.atomic.AtomicInteger currentProgress = new java.util.concurrent.atomic.AtomicInteger(0);

        for (String query : queries) {
            // Rate limiting sleep to prevent 429 Too Many Requests from JioSaavn
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
            }

            apiService.searchSongs(query, false).enqueue(new Callback<List<SongResponse>>() {
                @Override
                public void onResponse(@NonNull Call<List<SongResponse>> call,
                        @NonNull Response<List<SongResponse>> response) {
                    try {
                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                            Song song = response.body().get(0).toSong();
                            if (song.getMediaUrl() != null && addedIds.add(song.getId())) {
                                resolvedSongs.add(song);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error resolving track: " + query, e);
                    } finally {
                        int progress = currentProgress.incrementAndGet();
                        new android.os.Handler(android.os.Looper.getMainLooper())
                                .post(() -> callback.onProgress(progress, total, query));
                        latch.countDown();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<List<SongResponse>> call, @NonNull Throwable t) {
                    Log.e(TAG, "JioSaavn search failed for: " + query, t);
                    int progress = currentProgress.incrementAndGet();
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .post(() -> callback.onProgress(progress, total, query + " (Failed)"));
                    latch.countDown();
                }
            });
        }

        try {
            // Wait up to 2 minutes for all network calls to finish
            latch.await(2, java.util.concurrent.TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Log.e(TAG, "Timeout waiting for JioSaavn results", e);
        }

        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            if (!resolvedSongs.isEmpty()) {
                callback.onSuccess(new ArrayList<>(resolvedSongs));
            } else {
                callback.onError(new Exception("Could not resolve any playable tracks from JioSaavn."));
            }
        });
    }
}
