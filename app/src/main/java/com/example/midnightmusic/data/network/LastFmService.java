package com.example.midnightmusic.data.network;

import com.google.gson.annotations.SerializedName;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Last.fm API service for getting track recommendations.
 * Uses the track.getSimilar method to find similar tracks.
 */
public interface LastFmService {
    String BASE_URL = "https://ws.audioscrobbler.com/2.0/";

    @GET("?method=track.getSimilar&format=json")
    Call<SimilarTracksResponse> getSimilarTracks(
        @Query("track") String track,
        @Query("artist") String artist,
        @Query("api_key") String apiKey,
        @Query("limit") int limit
    );

    @GET("?method=artist.getTopTracks&format=json")
    Call<ArtistTopTracksResponse> getArtistTopTracks(
        @Query("artist") String artist,
        @Query("api_key") String apiKey,
        @Query("limit") int limit
    );

    @GET("?method=chart.getTopTracks&format=json")
    Call<ChartTopTracksResponse> getChartTopTracks(
        @Query("api_key") String apiKey,
        @Query("limit") int limit
    );

    // ========== Response Models ==========

    class SimilarTracksResponse {
        @SerializedName("similartracks")
        public SimilarTracks similarTracks;

        public static class SimilarTracks {
            @SerializedName("track")
            public List<LastFmTrack> tracks;
        }
    }

    class ArtistTopTracksResponse {
        @SerializedName("toptracks")
        public TopTracks topTracks;

        public static class TopTracks {
            @SerializedName("track")
            public List<LastFmTrack> tracks;
        }
    }

    class ChartTopTracksResponse {
        @SerializedName("tracks")
        public ChartTracks tracks;

        public static class ChartTracks {
            @SerializedName("track")
            public List<LastFmTrack> trackList;
        }
    }

    class LastFmTrack {
        @SerializedName("name")
        public String name;

        @SerializedName("artist")
        public LastFmArtist artist;

        public String getArtistName() {
            if (artist != null && artist.name != null) {
                return artist.name;
            }
            return "";
        }
    }

    class LastFmArtist {
        @SerializedName("name")
        public String name;

        @SerializedName("mbid")
        public String mbid;
    }
}
