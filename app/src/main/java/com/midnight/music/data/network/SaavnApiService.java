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
    String BASE_URL = BuildConfig.SAAVN_API_URL;

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
}
