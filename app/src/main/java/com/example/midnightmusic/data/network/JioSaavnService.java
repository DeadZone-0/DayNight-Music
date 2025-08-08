package com.example.midnightmusic.data.network;

import com.example.midnightmusic.data.model.Song;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
import java.util.List;

public interface JioSaavnService {
    String BASE_URL = "https://saavnapi-nine.vercel.app";

    @GET("result/")
    Call<List<SongResponse>> searchSongs(
        @Query("query") String query,
        @Query("lyrics") boolean lyrics
    );

    @GET("song/")
    Call<SongResponse> getSongDetails(
        @Query("query") String songLink,
        @Query("lyrics") boolean lyrics
    );

    @GET("playlist/")
    Call<List<SongResponse>> getPlaylist(
        @Query("query") String playlistLink,
        @Query("lyrics") boolean lyrics
    );

    @GET("album/")
    Call<List<SongResponse>> getAlbum(
        @Query("query") String albumLink,
        @Query("lyrics") boolean lyrics
    );

    @GET("lyrics/")
    Call<SongResponse> getLyrics(
        @Query("query") String songLink
    );
} 