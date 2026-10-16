package com.midnight.music.data.network;

import com.midnight.music.BuildConfig;

/**
 * Supabase project configuration constants.
 * Secrets are injected from local.properties via BuildConfig at build time.
 */
public final class SupabaseConfig {
    private SupabaseConfig() {} // Prevent instantiation

    public static final String PROJECT_URL = BuildConfig.SUPABASE_URL != null ? BuildConfig.SUPABASE_URL.trim() : "";
    public static final String ANON_KEY = BuildConfig.SUPABASE_ANON_KEY != null ? BuildConfig.SUPABASE_ANON_KEY.trim() : "";

    // ponytail: quick check whether backend credentials are provided
    public static boolean isConfigured() {
        return !PROJECT_URL.isEmpty() && !ANON_KEY.isEmpty();
    }

    // Auth endpoints
    public static final String AUTH_BASE = (isConfigured() ? PROJECT_URL : "https://localhost") + "/auth/v1";
    public static final String REST_BASE = (isConfigured() ? PROJECT_URL : "https://localhost") + "/rest/v1";

    // Google OAuth provider name in Supabase
    public static final String GOOGLE_PROVIDER = "google";
}
