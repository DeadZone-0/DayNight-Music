package com.midnight.music.data.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * Supabase PostgREST API service for CRUD operations on songs, playlists, and playlist_songs.
 * All operations require an Authorization header with a Bearer token.
 */
public interface SupabaseDataService {

    // ============ Profiles ============

    @GET("rest/v1/profiles")
    Call<JsonArray> getProfile(
            @Query("id") String idFilter,
            @Query("select") String select);

    @PATCH("rest/v1/profiles")
    Call<JsonArray> updateProfile(
            @Query("id") String idFilter,
            @Body JsonObject body,
            @Header("Prefer") String prefer);

    // ============ Songs ============

    @GET("rest/v1/songs")
    Call<JsonArray> getSongs(
            @Query("user_id") String userIdFilter,
            @Query("select") String select);

    @POST("rest/v1/songs")
    Call<JsonArray> upsertSongs(
            @Body JsonArray body,
            @Header("Prefer") String prefer);

    @DELETE("rest/v1/songs")
    Call<Void> deleteSong(
            @Query("id") String idFilter,
            @Query("user_id") String userIdFilter);

    @PATCH("rest/v1/songs")
    Call<JsonArray> updateSong(
            @Query("id") String idFilter,
            @Query("user_id") String userIdFilter,
            @Body JsonObject body,
            @Header("Prefer") String prefer);

    // ============ Playlists ============

    @GET("rest/v1/playlists")
    Call<JsonArray> getPlaylists(
            @Query("user_id") String userIdFilter,
            @Query("select") String select);

    @POST("rest/v1/playlists")
    Call<JsonArray> upsertPlaylists(
            @Body JsonArray body,
            @Header("Prefer") String prefer);

    @DELETE("rest/v1/playlists")
    Call<Void> deletePlaylist(
            @Query("id") String idFilter,
            @Query("user_id") String userIdFilter);

    // ============ Playlist Songs ============

    @GET("rest/v1/playlist_songs")
    Call<JsonArray> getPlaylistSongs(
            @Query("user_id") String userIdFilter,
            @Query("select") String select);

    @POST("rest/v1/playlist_songs")
    Call<JsonArray> upsertPlaylistSongs(
            @Body JsonArray body,
            @Header("Prefer") String prefer);

    @DELETE("rest/v1/playlist_songs")
    Call<Void> deletePlaylistSong(
            @Query("playlist_id") String playlistIdFilter,
            @Query("song_id") String songIdFilter,
            @Query("user_id") String userIdFilter);

    @DELETE("rest/v1/playlist_songs")
    Call<Void> deletePlaylistSongsByPlaylist(
            @Query("playlist_id") String playlistIdFilter,
            @Query("user_id") String userIdFilter);
}
