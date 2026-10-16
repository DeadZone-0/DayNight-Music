package com.midnight.music.data.repository;

import android.text.Html;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.NonNull;

import com.midnight.music.data.model.Song;
import com.midnight.music.data.network.LrcLibService;
import com.midnight.music.data.network.LrcResponse;
import com.midnight.music.data.network.NetworkClient;
import com.midnight.music.utils.LrcParser;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Robust multi-tier repository for fetching song lyrics with virtually 100% coverage.
 * Tier 1: LRCLib exact match (providing synced karaoke timestamps)
 * Tier 2: JioSaavn direct internal API (full Hindi / Bollywood / regional coverage)
 * Tier 3: Lyrics.ovh open lyrics database (global English / pop / rock catalog)
 * Tier 4: LRCLib fuzzy search query fallback
 */
public class LyricsRepository {
    private static final String TAG = "LyricsRepository";
    private static final String BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private static volatile LyricsRepository instance;

    private final LrcLibService lrcLibService;
    private final OkHttpClient okHttpClient;
    private final ExecutorService executorService;
    private final LruCache<String, List<LrcParser.LyricLine>> memoryCache;

    public interface LyricsCallback {
        void onSuccess(List<LrcParser.LyricLine> lines, boolean isSynced);
        void onError(String message);
    }

    public static LyricsRepository getInstance() {
        if (instance == null) {
            synchronized (LyricsRepository.class) {
                if (instance == null) {
                    instance = new LyricsRepository();
                }
            }
        }
        return instance;
    }

    private LyricsRepository() {
        this.executorService = Executors.newFixedThreadPool(3);
        this.memoryCache = new LruCache<>(100);

        this.okHttpClient = NetworkClient.getInstance().newBuilder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request request = original.newBuilder()
                            .header("User-Agent", BROWSER_USER_AGENT)
                            .header("Accept", "application/json, text/plain, */*")
                            .build();
                    return chain.proceed(request);
                })
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(LrcLibService.BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        this.lrcLibService = retrofit.create(LrcLibService.class);
    }

    public void fetchLyrics(Song song, LyricsCallback callback) {
        if (song == null) {
            callback.onError("Song is null");
            return;
        }

        String songId = song.getId();
        if (songId != null) {
            List<LrcParser.LyricLine> cached = memoryCache.get(songId);
            if (cached != null && !cached.isEmpty()) {
                boolean isSynced = cached.get(0).getTimeMs() > 0 || (cached.size() > 1 && cached.get(1).getTimeMs() > 0);
                callback.onSuccess(cached, isSynced);
                return;
            }
        }

        String rawTrack = song.getSong();
        String rawArtist = song.getSingers();

        if (rawTrack == null || rawTrack.trim().isEmpty()) {
            callback.onError("Track name is empty");
            return;
        }

        String cleanTrack = cleanTrackName(rawTrack);
        String cleanArtist = cleanArtistName(rawArtist);

        executorService.execute(() -> {
            Log.d(TAG, "Fetching lyrics for track='" + cleanTrack + "', artist='" + cleanArtist + "', id=" + songId);

            // Tier 1: LRCLib exact match for synced timestamps
            try {
                retrofit2.Response<LrcResponse> response = lrcLibService.getLyrics(cleanTrack, cleanArtist).execute();
                if (response.isSuccessful() && response.body() != null) {
                    LrcResponse lrc = response.body();
                    if (processLrcResponse(songId, lrc, callback)) {
                        Log.d(TAG, "Tier 1 (LRCLib exact) succeeded with synced lyrics");
                        return;
                    }
                } else {
                    Log.d(TAG, "Tier 1 LRCLib get response code: " + response.code());
                }
            } catch (Exception e) {
                Log.w(TAG, "Tier 1 LRCLib getLyrics error: " + e.getMessage());
            }

            // Tier 2: JioSaavn direct internal API (great for Indian & Bollywood tracks)
            if (songId != null && !songId.trim().isEmpty()) {
                if (fetchJioSaavnLyricsSync(songId, callback)) {
                    Log.d(TAG, "Tier 2 (JioSaavn) succeeded");
                    return;
                }
            }

            // Tier 3: Lyrics.ovh open lyrics catalog (great for International & English tracks)
            if (fetchLyricsOvhSync(cleanTrack, cleanArtist, songId, callback)) {
                Log.d(TAG, "Tier 3 (Lyrics.ovh) succeeded");
                return;
            }

            // Tier 4: LRCLib search query fallback
            try {
                String query = (cleanTrack + " " + cleanArtist).trim();
                retrofit2.Response<List<LrcResponse>> response = lrcLibService.searchLyricsByQuery(query).execute();
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    if (processLrcList(songId, response.body(), callback)) {
                        Log.d(TAG, "Tier 4 (LRCLib search) succeeded");
                        return;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Tier 4 LRCLib search error: " + e.getMessage());
            }

            Log.w(TAG, "All lyrics tiers exhausted without result for: " + cleanTrack);
            callback.onError("No lyrics available for this song");
        });
    }

    private boolean processLrcResponse(String songId, LrcResponse lrc, LyricsCallback callback) {
        if (lrc == null) return false;
        if (lrc.hasSyncedLyrics() && lrc.getSyncedLyrics() != null) {
            List<LrcParser.LyricLine> lines = LrcParser.parse(lrc.getSyncedLyrics());
            if (!lines.isEmpty()) {
                if (songId != null) memoryCache.put(songId, lines);
                callback.onSuccess(lines, true);
                return true;
            }
        }
        if (lrc.getPlainLyrics() != null && !lrc.getPlainLyrics().trim().isEmpty()) {
            List<LrcParser.LyricLine> lines = parsePlainLyrics(lrc.getPlainLyrics());
            if (!lines.isEmpty()) {
                if (songId != null) memoryCache.put(songId, lines);
                callback.onSuccess(lines, false);
                return true;
            }
        }
        return false;
    }

    private boolean processLrcList(String songId, List<LrcResponse> list, LyricsCallback callback) {
        if (list == null || list.isEmpty()) return false;
        // Prefer synced
        for (LrcResponse lrc : list) {
            if (lrc.hasSyncedLyrics() && lrc.getSyncedLyrics() != null) {
                List<LrcParser.LyricLine> lines = LrcParser.parse(lrc.getSyncedLyrics());
                if (!lines.isEmpty()) {
                    if (songId != null) memoryCache.put(songId, lines);
                    callback.onSuccess(lines, true);
                    return true;
                }
            }
        }
        // Fallback to plain lyrics
        for (LrcResponse lrc : list) {
            if (lrc.getPlainLyrics() != null && !lrc.getPlainLyrics().trim().isEmpty()) {
                List<LrcParser.LyricLine> lines = parsePlainLyrics(lrc.getPlainLyrics());
                if (!lines.isEmpty()) {
                    if (songId != null) memoryCache.put(songId, lines);
                    callback.onSuccess(lines, false);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean fetchJioSaavnLyricsSync(String songId, LyricsCallback callback) {
        try {
            String url = "https://www.jiosaavn.com/api.php?__call=lyrics.getLyrics&ctx=web6dot0&api_version=4&_format=json&_marker=0&lyrics_id=" + songId;
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);
                    if (json.has("lyrics")) {
                        String rawLyrics = json.getString("lyrics");
                        if (rawLyrics != null && !rawLyrics.trim().isEmpty()) {
                            // Replace <br> and <br/> with line breaks, then clean HTML entities
                            String withNewlines = rawLyrics.replaceAll("(?i)<br\\s*/?>", "\n");
                            String formatted = Html.fromHtml(withNewlines, Html.FROM_HTML_MODE_LEGACY).toString();
                            List<LrcParser.LyricLine> lines = parsePlainLyrics(formatted);
                            if (!lines.isEmpty()) {
                                memoryCache.put(songId, lines);
                                callback.onSuccess(lines, false);
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "JioSaavn lyrics error: " + e.getMessage());
        }
        return false;
    }

    private boolean fetchLyricsOvhSync(String track, String artist, String songId, LyricsCallback callback) {
        try {
            String encodedArtist = URLEncoder.encode(artist, "UTF-8").replace("+", "%20");
            String encodedTrack = URLEncoder.encode(track, "UTF-8").replace("+", "%20");
            String url = "https://api.lyrics.ovh/v1/" + encodedArtist + "/" + encodedTrack;

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    JSONObject json = new JSONObject(response.body().string());
                    if (json.has("lyrics")) {
                        String text = json.getString("lyrics");
                        if (text != null && !text.trim().isEmpty()) {
                            List<LrcParser.LyricLine> lines = parsePlainLyrics(text);
                            if (!lines.isEmpty()) {
                                if (songId != null) memoryCache.put(songId, lines);
                                callback.onSuccess(lines, false);
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Lyrics.ovh error: " + e.getMessage());
        }
        return false;
    }

    private List<LrcParser.LyricLine> parsePlainLyrics(String text) {
        List<LrcParser.LyricLine> list = new ArrayList<>();
        if (text == null) return list;
        String[] split = text.split("\n");
        long fakeTime = 0;
        for (String line : split) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                list.add(new LrcParser.LyricLine(fakeTime, trimmed));
                fakeTime += 1000;
            }
        }
        return list;
    }

    public static String cleanTrackName(String track) {
        if (track == null) return "";
        // Unescape HTML entities
        String clean = Html.fromHtml(track, Html.FROM_HTML_MODE_LEGACY).toString();
        // Remove (From "..."), [From "..."], (Audio), [Official Video], etc.
        clean = clean.replaceAll("(?i)\\s*\\(from\\s+.*?\\)", "");
        clean = clean.replaceAll("(?i)\\s*\\[from\\s+.*?\\]", "");
        clean = clean.replaceAll("(?i)\\s*\\(original\\s+.*?\\)", "");
        clean = clean.replaceAll("(?i)\\s*\\[original\\s+.*?\\]", "");
        clean = clean.replaceAll("(?i)\\s*\\(slowed\\s+.*?\\)", "");
        clean = clean.replaceAll("(?i)\\s*\\(reverb\\s+.*?\\)", "");
        clean = clean.replaceAll("(?i)\\s*\\(lq\\)", "");
        clean = clean.replaceAll("(?i)\\s*\\(hq\\)", "");
        clean = clean.replaceAll("\\s*[\\(\\[].*?[\\)\\]]", " ");
        clean = clean.replaceAll("\\s+", " ").trim();
        return clean;
    }

    public static String cleanArtistName(String artist) {
        if (artist == null) return "";
        String clean = Html.fromHtml(artist, Html.FROM_HTML_MODE_LEGACY).toString();
        // Take primary artist before comma, slash, or feat
        if (clean.contains(",")) clean = clean.split(",")[0];
        if (clean.contains("/")) clean = clean.split("/")[0];
        if (clean.toLowerCase().contains(" feat")) clean = clean.split("(?i) feat")[0];
        if (clean.toLowerCase().contains(" ft")) clean = clean.split("(?i) ft")[0];
        clean = clean.replaceAll("\\s+", " ").trim();
        return clean;
    }
}
