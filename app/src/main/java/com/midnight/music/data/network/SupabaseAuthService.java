package com.midnight.music.data.network;

import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;

/**
 * Supabase GoTrue Auth API service.
 * Handles sign-up, sign-in, token refresh, and user metadata updates.
 */
public interface SupabaseAuthService {

    /**
     * Sign up with email/password.
     * Body: { "email": "...", "password": "...", "data": { "nickname": "..." } }
     */
    @POST("auth/v1/signup")
    Call<JsonObject> signUp(@Body JsonObject body);

    /**
     * Sign in with email/password.
     * Body: { "email": "...", "password": "..." }
     */
    @POST("auth/v1/token?grant_type=password")
    Call<JsonObject> signInWithPassword(@Body JsonObject body);

    /**
     * Sign in with Google ID token (exchanged for Supabase session).
     * Body: { "id_token": "..." }
     */
    @POST("auth/v1/token?grant_type=id_token")
    Call<JsonObject> signInWithIdToken(@Body JsonObject body);

    /**
     * Refresh token to get a new access token.
     * Body: { "refresh_token": "..." }
     */
    @POST("auth/v1/token?grant_type=refresh_token")
    Call<JsonObject> refreshToken(@Body JsonObject body);

    /**
     * Verify an OTP or PKCE code from an email link.
     */
    @POST("auth/v1/verify")
    Call<JsonObject> verifyOtp(@Body JsonObject body);

    /**
     * Get current user details (requires Bearer token).
     */
    @GET("auth/v1/user")
    Call<JsonObject> getUser(@Header("Authorization") String bearerToken);

    /**
     * Update user metadata (e.g. nickname).
     * Body: { "data": { "nickname": "..." } }
     */
    @PUT("auth/v1/user")
    Call<JsonObject> updateUser(
            @Header("Authorization") String bearerToken,
            @Body JsonObject body);

    /**
     * Sign out (invalidates the refresh token).
     */
    @POST("auth/v1/logout")
    Call<Void> signOut(@Header("Authorization") String bearerToken);
}
