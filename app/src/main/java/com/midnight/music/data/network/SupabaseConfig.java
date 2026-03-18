package com.midnight.music.data.network;

import com.midnight.music.BuildConfig;

/**
 * Supabase project configuration constants.
 * Secrets are injected from local.properties via BuildConfig at build time.
 */
public final class SupabaseConfig {
    private SupabaseConfig() {} // Prevent instantiation

    public static final String PROJECT_URL = BuildConfig.SUPABASE_URL;
    public static final String ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;

    // Auth endpoints
    public static final String AUTH_BASE = PROJECT_URL + "/auth/v1";
    public static final String REST_BASE = PROJECT_URL + "/rest/v1";

    // Google OAuth provider name in Supabase
    public static final String GOOGLE_PROVIDER = "google";
}
