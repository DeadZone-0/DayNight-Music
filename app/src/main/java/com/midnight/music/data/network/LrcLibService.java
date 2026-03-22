package com.midnight.music.data.network;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
import java.util.List;

/**
 * Retrofit service interface for the LRCLib API (https://lrclib.net).
 * Used to fetch synchronized (timestamped) lyrics for songs.
 */
public interface LrcLibService {
    String BASE_URL = "https://lrclib.net/";

    @GET("api/search")
    Call<List<LrcResponse>> searchLyrics(
            @Query("track_name") String trackName,
            @Query("artist_name") String artistName);

    @GET("api/search")
    Call<List<LrcResponse>> searchLyricsWithAlbum(
            @Query("track_name") String trackName,
            @Query("artist_name") String artistName,
            @Query("album_name") String albumName);
}
