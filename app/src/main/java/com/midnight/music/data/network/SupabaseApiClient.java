package com.midnight.music.data.network;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Singleton Retrofit client for all Supabase API calls.
 * Automatically injects the API key header and (optionally) the user's JWT bearer token.
 */
public class SupabaseApiClient {
    private static volatile SupabaseApiClient instance;

    private final SupabaseAuthService authService;
    private final SupabaseDataService dataService;
    private String accessToken; // Mutable â€” set after login

    private SupabaseApiClient() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request.Builder builder = chain.request().newBuilder()
                                .addHeader("apikey", SupabaseConfig.ANON_KEY)
                                .addHeader("Content-Type", "application/json");

                        // Add Bearer token if available and not already set on the request
                        if (accessToken != null && chain.request().header("Authorization") == null) {
                            builder.addHeader("Authorization", "Bearer " + accessToken);
                        }

                        return chain.proceed(builder.build());
                    }
                })
                .addInterceptor(logging)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(SupabaseConfig.PROJECT_URL + "/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        this.authService = retrofit.create(SupabaseAuthService.class);
        this.dataService = retrofit.create(SupabaseDataService.class);
    }

    public static SupabaseApiClient getInstance() {
        if (instance == null) {
            synchronized (SupabaseApiClient.class) {
                if (instance == null) {
                    instance = new SupabaseApiClient();
                }
            }
        }
        return instance;
    }

    public SupabaseAuthService getAuthService() {
        return authService;
    }

    public SupabaseDataService getDataService() {
        return dataService;
    }

    public void setAccessToken(String token) {
        this.accessToken = token;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void clearAccessToken() {
        this.accessToken = null;
    }
}
