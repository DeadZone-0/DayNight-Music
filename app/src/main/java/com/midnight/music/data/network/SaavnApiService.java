package com.midnight.music.data.network;

import com.midnight.music.BuildConfig;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Retrofit interface for the new paginated Saavn API.
 * Base URL is loaded from local.properties via BuildConfig.
 */
public interface SaavnApiService {
    // ponytail: sanitize base URL and guarantee trailing slash to prevent Retrofit IllegalArgumentException
    String BASE_URL = (BuildConfig.SAAVN_API_URL == null || BuildConfig.SAAVN_API_URL.trim().isEmpty() || BuildConfig.SAAVN_API_URL.contains("PLACEHOLDER"))
            ? "https://midnight.ze68.workers.dev/"
            : (BuildConfig.SAAVN_API_URL.endsWith("/") ? BuildConfig.SAAVN_API_URL : BuildConfig.SAAVN_API_URL + "/");

    /**
     * Search for songs with pagination.
     * 
     * @param query Search query string
     * @param page
     * @param limit
     */
    @GET("api/search/songs")
    Call<SaavnSearchResponse> searchSongs(
            @Query("query") String query,
            @Query("page") int page,
            @Query("limit") int limit);

    /**
     * Get similar songs/suggestions based on a song ID.
     *
     * @param songId The JioSaavn song ID
     * @param limit Maximum number of similar songs to return
     */
    @GET("api/songs/{id}/suggestions")
    Call<SaavnSuggestionsResponse> getSimilarSongs(
            @retrofit2.http.Path("id") String songId,
            @Query("limit") int limit);

    /**
     * Search for artists with pagination.
     *
     * @param query Search query string
     * @param page Page number
     * @param limit Results per page
     */
    @GET("api/search/artists")
    Call<SaavnArtistSearchResponse> searchArtists(
            @Query("query") String query,
            @Query("page") int page,
            @Query("limit") int limit);

    /**
     * Get artist details with top songs.
     *
     * @param artistId The artist ID
     * @param limit Number of top songs to fetch
     */
    @GET("api/artists/{id}/songs")
    Call<SaavnArtistSongsResponse> getArtistTopSongs(
            @retrofit2.http.Path("id") String artistId,
            @Query("limit") int limit);
}
