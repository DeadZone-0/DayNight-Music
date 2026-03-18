package com.midnight.music.data.auth;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.midnight.music.data.network.SupabaseApiClient;
import com.midnight.music.data.network.SupabaseAuthService;
import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Repository handling all Supabase authentication operations.
 * Manages sign-up, sign-in (email & Google), token refresh, and user metadata.
 */
public class AuthRepository {
    private static final String TAG = "AuthRepository";
    private final SupabaseAuthService authService;

    public AuthRepository() {
        this.authService = SupabaseApiClient.getInstance().getAuthService();
    }

    // ============ Callbacks ============

    public interface AuthCallback {
        void onSuccess(AuthResult result);
        void onError(String message);
    }

    /**
     * Holds the parsed auth response after a successful login/signup.
     */
    public static class AuthResult {
        public final String accessToken;
        public final String refreshToken;
        public final String userId;
        public final String email;
        public final String nickname;
        public final String avatarUrl;
        public final boolean isNewUser;

        public AuthResult(String accessToken, String refreshToken, String userId,
                          String email, String nickname, String avatarUrl, boolean isNewUser) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.userId = userId;
            this.email = email;
            this.nickname = nickname;
            this.avatarUrl = avatarUrl;
            this.isNewUser = isNewUser;
        }
    }

    // ============ Email / Password Auth ============

    /**
     * Sign up with email, password, and optional nickname.
     */
    public void signUp(String email, String password, @Nullable String nickname, AuthCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);

        if (nickname != null && !nickname.isEmpty()) {
            JsonObject data = new JsonObject();
            data.addProperty("nickname", nickname);
            body.add("data", data);
        }

        authService.signUp(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject json = response.body();
                    
                    // Check if email confirmation is required (no access_token returned)
                    if (!json.has("access_token") || json.get("access_token").isJsonNull()) {
                        // This means sign up worked, but they need to check their email
                        callback.onError("Registration successful. Please check your email to confirm your account.");
                        return;
                    }

                    AuthResult result = parseAuthResponse(json, true);
                    if (result != null) {
                        SupabaseApiClient.getInstance().setAccessToken(result.accessToken);
                        callback.onSuccess(result);
                    } else {
                        callback.onError("Failed to parse sign-up response");
                    }
                } else {
                    callback.onError("Sign-up failed: " + extractErrorMessage(response));
                }
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                Log.e(TAG, "Sign-up network error", t);
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    /**
     * Sign in with email and password.
     */
    public void signIn(String email, String password, AuthCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);

        authService.signInWithPassword(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AuthResult result = parseAuthResponse(response.body(), false);
                    if (result != null) {
                        SupabaseApiClient.getInstance().setAccessToken(result.accessToken);
                        callback.onSuccess(result);
                    } else {
                        callback.onError("Failed to parse sign-in response");
                    }
                } else {
                    callback.onError("Sign-in failed: " + extractErrorMessage(response));
                }
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                Log.e(TAG, "Sign-in network error", t);
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    // ============ Google Sign-In ============

    /**
     * Exchange a Google ID token for a Supabase session.
     */
    public void signInWithGoogle(String idToken, AuthCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("provider", "google");
        body.addProperty("id_token", idToken);

        authService.signInWithIdToken(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject responseBody = response.body();
                    // Determine if this is a new user by checking for created_at vs last_sign_in_at
                    boolean isNewUser = false;
                    if (responseBody.has("user")) {
                        JsonObject user = responseBody.getAsJsonObject("user");
                        String createdAt = getStringField(user, "created_at");
                        String lastSignIn = getStringField(user, "last_sign_in_at");
                        // If created_at equals last_sign_in_at, this is a brand new user
                        if (createdAt != null && createdAt.equals(lastSignIn)) {
                            isNewUser = true;
                        }
                    }

                    AuthResult result = parseAuthResponse(responseBody, isNewUser);
                    if (result != null) {
                        SupabaseApiClient.getInstance().setAccessToken(result.accessToken);
                        callback.onSuccess(result);
                    } else {
                        callback.onError("Failed to parse Google sign-in response");
                    }
                } else {
                    callback.onError("Google sign-in failed: " + extractErrorMessage(response));
                }
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                Log.e(TAG, "Google sign-in network error", t);
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    // ============ Token Refresh ============

    /**
     * Refresh the access token using a stored refresh token.
     */
    public void refreshToken(String refreshToken, AuthCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("refresh_token", refreshToken);

        authService.refreshToken(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AuthResult result = parseAuthResponse(response.body(), false);
                    if (result != null) {
                        SupabaseApiClient.getInstance().setAccessToken(result.accessToken);
                        callback.onSuccess(result);
                    } else {
                        callback.onError("Failed to parse refresh response");
                    }
                } else {
                    callback.onError("Token refresh failed");
                }
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                Log.e(TAG, "Token refresh network error", t);
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    /**
     * Verify an OTP or PKCE code to obtain a session (used for deep linking).
     */
    public void verifyEmailOtp(String tokenHash, String type, AuthCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("token_hash", tokenHash);
        body.addProperty("type", type);

        authService.verifyOtp(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AuthResult result = parseAuthResponse(response.body(), false);
                    if (result != null) {
                        SupabaseApiClient.getInstance().setAccessToken(result.accessToken);
                        callback.onSuccess(result);
                    } else {
                        callback.onError("Failed to parse verification response");
                    }
                } else {
                    callback.onError("Email verification failed: " + extractErrorMessage(response));
                }
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                Log.e(TAG, "OTP verification network error", t);
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    // ============ Update Nickname ============

    /**
     * Update the user's nickname in Supabase Auth metadata.
     */
    public void updateNickname(String accessToken, String nickname, AuthCallback callback) {
        JsonObject data = new JsonObject();
        data.addProperty("nickname", nickname);
        JsonObject body = new JsonObject();
        body.add("data", data);

        authService.updateUser("Bearer " + accessToken, body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(null); // No new tokens needed
                } else {
                    callback.onError("Failed to update nickname");
                }
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    // ============ Sign Out ============

    public void signOut(String accessToken) {
        authService.signOut("Bearer " + accessToken).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                Log.d(TAG, "Signed out successfully");
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                Log.e(TAG, "Sign-out failed", t);
            }
        });
        SupabaseApiClient.getInstance().clearAccessToken();
    }

    // ============ Helpers ============

    @Nullable
    private AuthResult parseAuthResponse(JsonObject json, boolean isNewUser) {
        try {
            String accessToken = getStringField(json, "access_token");
            String refreshToken = getStringField(json, "refresh_token");

            if (accessToken == null) return null;

            String userId = null, email = null, nickname = null, avatarUrl = null;

            if (json.has("user") && json.get("user").isJsonObject()) {
                JsonObject user = json.getAsJsonObject("user");
                userId = getStringField(user, "id");
                email = getStringField(user, "email");

                if (user.has("user_metadata") && user.get("user_metadata").isJsonObject()) {
                    JsonObject meta = user.getAsJsonObject("user_metadata");
                    nickname = getStringField(meta, "nickname");
                    // Google accounts often have 'full_name' or 'name'
                    if (nickname == null) nickname = getStringField(meta, "full_name");
                    if (nickname == null) nickname = getStringField(meta, "name");
                    avatarUrl = getStringField(meta, "avatar_url");
                    if (avatarUrl == null) avatarUrl = getStringField(meta, "picture");
                }
            }

            return new AuthResult(accessToken, refreshToken, userId, email, nickname, avatarUrl, isNewUser);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing auth response", e);
            return null;
        }
    }

    @Nullable
    private String getStringField(JsonObject json, String field) {
        if (json.has(field) && !json.get(field).isJsonNull()) {
            return json.get(field).getAsString();
        }
        return null;
    }

    private String extractErrorMessage(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                return response.errorBody().string();
            }
        } catch (Exception e) {
            // ignore
        }
        return "HTTP " + response.code();
    }
}
