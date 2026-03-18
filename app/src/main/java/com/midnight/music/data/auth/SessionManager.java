package com.midnight.music.data.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Securely persists auth session tokens using EncryptedSharedPreferences.
 * Stores access token, refresh token, user ID, email, and nickname.
 */
public class SessionManager {
    private static final String TAG = "SessionManager";
    private static final String PREF_NAME = "daynight_auth_prefs";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_NICKNAME = "nickname";
    private static final String KEY_AVATAR_URL = "avatar_url";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";

    private static volatile SessionManager instance;
    private final SharedPreferences prefs;

    private SessionManager(Context context) {
        SharedPreferences encryptedPrefs;
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            encryptedPrefs = EncryptedSharedPreferences.create(
                    context,
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Failed to create encrypted prefs, falling back to regular prefs", e);
            encryptedPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }
        this.prefs = encryptedPrefs;
    }

    public static SessionManager getInstance(Context context) {
        if (instance == null) {
            synchronized (SessionManager.class) {
                if (instance == null) {
                    instance = new SessionManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /**
     * Save a full auth session after successful login/signup.
     */
    public void saveSession(String accessToken, String refreshToken, String userId,
                            String email, String nickname, String avatarUrl) {
        prefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putString(KEY_USER_ID, userId)
                .putString(KEY_EMAIL, email)
                .putString(KEY_NICKNAME, nickname)
                .putString(KEY_AVATAR_URL, avatarUrl)
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .apply();
    }

    /**
     * Update just the access token (e.g. after a token refresh).
     */
    public void updateAccessToken(String accessToken) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, accessToken).apply();
    }

    /**
     * Update the nickname after the user sets it.
     */
    public void updateNickname(String nickname) {
        prefs.edit().putString(KEY_NICKNAME, nickname).apply();
    }

    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, null);
    }

    public String getRefreshToken() {
        return prefs.getString(KEY_REFRESH_TOKEN, null);
    }

    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    public String getEmail() {
        return prefs.getString(KEY_EMAIL, null);
    }

    public String getNickname() {
        return prefs.getString(KEY_NICKNAME, null);
    }

    public String getAvatarUrl() {
        return prefs.getString(KEY_AVATAR_URL, null);
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    /**
     * Clear all session data on logout.
     */
    public void clearSession() {
        prefs.edit().clear().apply();
    }
}
