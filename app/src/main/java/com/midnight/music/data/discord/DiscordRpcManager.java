package com.midnight.music.data.discord;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.midnight.music.data.model.Song;
import com.midnight.music.data.network.NetworkClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Discord Rich Presence Manager using direct Discord Gateway WebSocket.
 * Reconstructed directly from Animeze's DiscordRpcManager architecture.
 */
public class DiscordRpcManager {
    private static final String TAG = "DiscordRpcManager";
    public static final String APPLICATION_ID = "1537082907934335035";
    private static final String GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json";
    private static final String DISCORD_API = "https://discord.com/api/v9";
    private static final String SMALL_IMAGE_URL = "https://midnight.ze68.workers.dev/logo.png";

    private static volatile DiscordRpcManager instance;

    private final Context appContext;
    private final SharedPreferences prefs;
    private final OkHttpClient okHttpClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Observable UI states
    private final MutableLiveData<Boolean> isEnabled = new MutableLiveData<>();
    private final MutableLiveData<String> token = new MutableLiveData<>();
    private final MutableLiveData<DiscordUserProfile> userProfile = new MutableLiveData<>();
    private final MutableLiveData<String> connectionStatus = new MutableLiveData<>();
    private final MutableLiveData<Boolean> showTitle = new MutableLiveData<>();
    private final MutableLiveData<Boolean> showArtist = new MutableLiveData<>();
    private final MutableLiveData<Boolean> showTime = new MutableLiveData<>();
    private final MutableLiveData<Integer> activityType = new MutableLiveData<>();
    private final MutableLiveData<DiscordPresenceData> currentPresence = new MutableLiveData<>();

    // Gateway runtime state
    private WebSocket webSocket;
    private ScheduledFuture<?> heartbeatFuture;
    private Integer lastSequenceNumber = null;
    private String sessionId = null;
    private boolean isInitialized = false;
    private String presenceStore = "";
    private int retryAttempts = 0;
    private static final int MAX_RETRY_ATTEMPTS = 10;

    // Cache: imageUrl -> "mp:external/..."
    private final Map<String, String> assetCache = new ConcurrentHashMap<>();

    public interface VerifyCallback {
        void onSuccess(DiscordUserProfile profile);
        void onError(String message);
    }

    private DiscordRpcManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences("discord_rpc_prefs", Context.MODE_PRIVATE);
        this.okHttpClient = NetworkClient.getInstance();

        // Load initial state from preferences
        boolean enabled = prefs.getBoolean("discord_enabled", true);
        String savedToken = prefs.getString("discord_token", null);
        boolean sTitle = prefs.getBoolean("discord_show_title", true);
        boolean sArtist = prefs.getBoolean("discord_show_artist", true);
        boolean sTime = prefs.getBoolean("discord_show_time", true);
        int aType = prefs.getInt("discord_activity_type", 2); // 2 = Listening to Music

        isEnabled.postValue(enabled);
        token.postValue(savedToken);
        showTitle.postValue(sTitle);
        showArtist.postValue(sArtist);
        showTime.postValue(sTime);
        activityType.postValue(aType);
        userProfile.postValue(loadSavedProfile());
        connectionStatus.postValue(savedToken != null ? "Connecting" : "Disconnected");

        if (enabled && savedToken != null && !savedToken.trim().isEmpty()) {
            connectGateway();
        }
    }

    public static DiscordRpcManager getInstance(Context context) {
        if (instance == null) {
            synchronized (DiscordRpcManager.class) {
                if (instance == null) {
                    instance = new DiscordRpcManager(context);
                }
            }
        }
        return instance;
    }

    public LiveData<Boolean> getIsEnabled() { return isEnabled; }
    public LiveData<String> getToken() { return token; }
    public LiveData<DiscordUserProfile> getUserProfile() { return userProfile; }
    public LiveData<String> getConnectionStatus() { return connectionStatus; }
    public LiveData<Boolean> getShowTitle() { return showTitle; }
    public LiveData<Boolean> getShowArtist() { return showArtist; }
    public LiveData<Boolean> getShowTime() { return showTime; }
    public LiveData<Integer> getActivityType() { return activityType; }
    public LiveData<DiscordPresenceData> getCurrentPresence() { return currentPresence; }

    private DiscordUserProfile loadSavedProfile() {
        String id = prefs.getString("user_id", null);
        if (id == null) return null;
        return new DiscordUserProfile(
                id,
                prefs.getString("username", ""),
                prefs.getString("global_name", null),
                prefs.getString("discriminator", null),
                prefs.getString("avatar_url", null)
        );
    }

    // ── Settings Mutators ──────────────────────────────────────────────────

    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean("discord_enabled", enabled).apply();
        isEnabled.postValue(enabled);
        if (enabled) {
            if (token.getValue() != null && !token.getValue().isEmpty()) {
                connectGateway();
            }
        } else {
            stopRPC();
        }
    }

    public void setShowTitle(boolean v) {
        prefs.edit().putBoolean("discord_show_title", v).apply();
        showTitle.postValue(v);
        refreshCurrentPresence();
    }

    public void setShowArtist(boolean v) {
        prefs.edit().putBoolean("discord_show_artist", v).apply();
        showArtist.postValue(v);
        refreshCurrentPresence();
    }

    public void setShowTime(boolean v) {
        prefs.edit().putBoolean("discord_show_time", v).apply();
        showTime.postValue(v);
        refreshCurrentPresence();
    }

    public void setActivityType(int type) {
        prefs.edit().putInt("discord_activity_type", type).apply();
        activityType.postValue(type);
        refreshCurrentPresence();
    }

    // ── Token Verification & Login ─────────────────────────────────────────

    public void verifyAndSaveToken(String inputToken, VerifyCallback callback) {
        if (inputToken == null || inputToken.trim().isEmpty()) {
            if (callback != null) callback.onError("Token cannot be empty");
            return;
        }
        final String cleanToken = inputToken.trim().replace("\"", "");

        executor.execute(() -> {
            try {
                Request request = new Request.Builder()
                        .url(DISCORD_API + "/users/@me")
                        .header("Authorization", cleanToken)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                        .build();

                Response response = okHttpClient.newCall(request).execute();
                if (!response.isSuccessful()) {
                    mainHandler.post(() -> {
                        if (callback != null) callback.onError("Invalid Discord token (HTTP " + response.code() + ")");
                    });
                    return;
                }

                String bodyStr = response.body() != null ? response.body().string() : null;
                if (bodyStr == null) throw new Exception("Empty response body");

                JSONObject json = new JSONObject(bodyStr);
                String id = json.getString("id");
                String username = json.optString("username", "Discord User");
                String globalName = json.isNull("global_name") ? null : json.optString("global_name");
                String discriminator = json.optString("discriminator", "0");
                String avatarHash = json.isNull("avatar") ? null : json.optString("avatar");
                String avatarUrl = avatarHash != null
                        ? "https://cdn.discordapp.com/avatars/" + id + "/" + avatarHash + ".png?size=256"
                        : null;

                DiscordUserProfile profile = new DiscordUserProfile(id, username, globalName, discriminator, avatarUrl);

                prefs.edit()
                        .putString("discord_token", cleanToken)
                        .putString("user_id", id)
                        .putString("username", username)
                        .putString("global_name", globalName)
                        .putString("discriminator", discriminator)
                        .putString("avatar_url", avatarUrl)
                        .apply();

                token.postValue(cleanToken);
                userProfile.postValue(profile);

                mainHandler.post(() -> {
                    if (callback != null) callback.onSuccess(profile);
                });

                if (Boolean.TRUE.equals(isEnabled.getValue())) {
                    connectGateway();
                }

            } catch (Exception e) {
                Log.e(TAG, "Token verify failed", e);
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("Error: " + e.getMessage());
                });
            }
        });
    }

    public void logout() {
        prefs.edit().clear().apply();
        token.postValue(null);
        userProfile.postValue(null);
        currentPresence.postValue(null);
        assetCache.clear();
        presenceStore = "";
        stopRPC();
    }

    // ── External Asset Proxy (Matches Animeze / Dartotsu) ──────────────────

    private String getDiscordUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) return null;
        if (imageUrl.startsWith("mp:")) return imageUrl;

        String cached = assetCache.get(imageUrl);
        if (cached != null) return cached;

        String userToken = prefs.getString("discord_token", null);
        if (userToken == null) return null;

        try {
            JSONObject bodyJson = new JSONObject();
            JSONArray urlsArr = new JSONArray();
            urlsArr.put(imageUrl);
            bodyJson.put("urls", urlsArr);

            Request request = new Request.Builder()
                    .url(DISCORD_API + "/applications/" + APPLICATION_ID + "/external-assets")
                    .header("Authorization", userToken)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(bodyJson.toString(), MediaType.parse("application/json")))
                    .build();

            Response response = okHttpClient.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                String body = response.body().string();
                JSONArray arr = new JSONArray(body);
                if (arr.length() > 0) {
                    String path = arr.getJSONObject(0).getString("external_asset_path");
                    String result = "mp: " + path;
                    assetCache.put(imageUrl, result);
                    Log.d(TAG, "Registered Discord asset: " + result);
                    return result;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "external-assets registration failed: " + e.getMessage());
        }
        return null;
    }

    // ── Presence Updates ───────────────────────────────────────────────────

    public void updatePresence(Song song, boolean isPlaying, long positionMs, long durationMs) {
        if (song == null) {
            clearPresence();
            return;
        }

        long now = System.currentTimeMillis();
        long startMs = now - positionMs;
        long endMs = durationMs > 0 ? (startMs + durationMs) : (now + 180000);

        DiscordPresenceData data = new DiscordPresenceData(
                song.getSong(),
                song.getSingers() != null ? song.getSingers() : song.getAlbum(),
                song.getAlbum(),
                song.getImageUrl(),
                isPlaying,
                startMs,
                endMs
        );
        currentPresence.postValue(data);

        if (!Boolean.TRUE.equals(isEnabled.getValue())) return;

        executor.execute(() -> {
            try {
                // Register artwork if available
                String largeImage = getDiscordUrl(song.getImageUrl());
                String smallImage = getDiscordUrl(SMALL_IMAGE_URL);

                int type = prefs.getInt("discord_activity_type", 2); // 2 = Listening
                boolean showT = prefs.getBoolean("discord_show_title", true);
                boolean showA = prefs.getBoolean("discord_show_artist", true);
                boolean showTm = prefs.getBoolean("discord_show_time", true);

                JSONObject activity = new JSONObject();
                activity.put("application_id", APPLICATION_ID);
                activity.put("name", song.getSong() != null ? song.getSong() : "Midnight Music");
                activity.put("type", type);

                if (showT && song.getSong() != null) {
                    activity.put("details", song.getSong());
                }

                if (showA) {
                    String artist = song.getSingers() != null ? song.getSingers() : song.getAlbum();
                    if (artist != null && !artist.isEmpty()) {
                        activity.put("state", artist);
                    }
                }

                if (showTm && isPlaying) {
                    JSONObject timestamps = new JSONObject();
                    timestamps.put("start", startMs);
                    if (endMs > startMs) {
                        timestamps.put("end", endMs);
                    }
                    activity.put("timestamps", timestamps);
                }

                JSONObject assets = new JSONObject();
                if (largeImage != null) {
                    assets.put("large_image", largeImage);
                    assets.put("large_text", song.getSong());
                }
                if (smallImage != null) {
                    assets.put("small_image", smallImage);
                    assets.put("small_text", "Midnight Music");
                }
                if (assets.length() > 0) {
                    activity.put("assets", assets);
                }

                JSONArray buttons = new JSONArray();
                buttons.put("Listen on Midnight");
                activity.put("buttons", buttons);

                JSONObject metadata = new JSONObject();
                JSONArray buttonUrls = new JSONArray();
                buttonUrls.put("https://midnight.ze68.workers.dev");
                metadata.put("button_urls", buttonUrls);
                activity.put("metadata", metadata);

                // Wrapper Op 3
                JSONObject rpc = new JSONObject();
                rpc.put("op", 3);
                JSONObject d = new JSONObject();
                JSONArray activities = new JSONArray();
                activities.put(activity);
                d.put("activities", activities);
                d.put("afk", true);
                d.put("since", JSONObject.NULL);
                d.put("status", "idle");
                rpc.put("d", d);

                String rpcJson = rpc.toString();
                Log.d(TAG, "Setting Discord presence: " + rpcJson);
                setPresence(rpcJson);

            } catch (Exception e) {
                Log.e(TAG, "Error building Discord presence", e);
            }
        });
    }

    private void refreshCurrentPresence() {
        DiscordPresenceData data = currentPresence.getValue();
        if (data != null) {
            Song s = new Song();
            s.setSong(data.getSongTitle());
            s.setSingers(data.getArtist());
            s.setAlbum(data.getAlbum());
            s.setImageUrl(data.getImageUrl());
            long duration = data.getEndTimestampMs() - data.getStartTimestampMs();
            long position = System.currentTimeMillis() - data.getStartTimestampMs();
            updatePresence(s, data.isPlaying(), position, duration);
        }
    }

    private synchronized void setPresence(String json) {
        this.presenceStore = json;
        if (isInitialized && webSocket != null) {
            try {
                boolean sent = webSocket.send(json);
                if (sent) {
                    Log.d(TAG, "Sent presence update over active WebSocket");
                } else {
                    Log.w(TAG, "WebSocket send failed, reconnecting...");
                    reconnect();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to send presence: " + e.getMessage());
                reconnect();
            }
        } else {
            connectGateway();
        }
    }

    public void clearPresence() {
        currentPresence.postValue(null);
        presenceStore = "";
        stopRPC();
    }

    // ── Gateway WebSocket Connection ───────────────────────────────────────

    public synchronized void connectGateway() {
        final String userToken = prefs.getString("discord_token", null);
        if (userToken == null || userToken.trim().isEmpty()) {
            connectionStatus.postValue("Disconnected");
            return;
        }
        if (!prefs.getBoolean("discord_enabled", true)) {
            return;
        }
        if (webSocket != null && (isInitialized || "Connecting".equals(connectionStatus.getValue()))) {
            Log.d(TAG, "Already connected or connecting, skipping connectGateway");
            return;
        }

        connectionStatus.postValue("Connecting");
        isInitialized = false;

        Request request = new Request.Builder().url(GATEWAY_URL).build();
        webSocket = okHttpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onMessage(WebSocket ws, String text) {
                try {
                    JSONObject msg = new JSONObject(text);
                    int op = msg.getInt("op");
                    if (msg.has("s") && !msg.isNull("s")) {
                        lastSequenceNumber = msg.getInt("s");
                    }

                    switch (op) {
                        case 0: // Dispatch
                            if ("READY".equals(msg.optString("t"))) {
                                handleReady(msg.getJSONObject("d"));
                            }
                            break;
                        case 1: // Heartbeat requested
                            sendHeartbeat(ws);
                            break;
                        case 7: // Reconnect
                            Log.w(TAG, "Op 7 Reconnect requested");
                            scheduler.schedule(() -> reconnect(), 500, TimeUnit.MILLISECONDS);
                            break;
                        case 9: // Invalid session
                            Log.w(TAG, "Op 9 Invalid session");
                            scheduler.schedule(() -> reconnect(), 1000, TimeUnit.MILLISECONDS);
                            break;
                        case 10: // Hello
                            long interval = msg.getJSONObject("d").getLong("heartbeat_interval");
                            startHeartbeat(ws, interval);
                            sendIdentify(ws, userToken);
                            break;
                        case 11: // Heartbeat ACK
                            Log.d(TAG, "Heartbeat ACK received");
                            break;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "onMessage error", e);
                }
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                Log.e(TAG, "WS failure: " + t.getMessage() + " (resp: " + (response != null ? response.code() : "null") + ")");
                connectionStatus.postValue("Error");
                isInitialized = false;
                webSocket = null;
                retryAttempts++;
                if (retryAttempts < MAX_RETRY_ATTEMPTS) {
                    scheduler.schedule(() -> reconnect(), 5000, TimeUnit.MILLISECONDS);
                } else {
                    stopHeartbeat();
                }
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                Log.d(TAG, "WS closed: " + code + " " + reason);
                connectionStatus.postValue("Disconnected");
                isInitialized = false;
                webSocket = null;
            }
        });
    }

    private void handleReady(JSONObject d) {
        sessionId = d.optString("session_id");
        Log.d(TAG, "READY! session=" + sessionId);
        connectionStatus.postValue("Connected");
        isInitialized = true;
        retryAttempts = 0;

        if (!presenceStore.isEmpty() && webSocket != null) {
            webSocket.send(presenceStore);
            Log.d(TAG, "Pending presence sent on READY");
        }
    }

    private synchronized void reconnect() {
        try {
            if (webSocket != null) webSocket.close(4000, "Reconnecting");
        } catch (Exception ignored) {}
        webSocket = null;
        isInitialized = false;
        connectionStatus.postValue("Disconnected");
        stopHeartbeat();
        connectGateway();
    }

    public synchronized void stopRPC() {
        try {
            if (webSocket != null) {
                JSONObject clearPayload = new JSONObject();
                clearPayload.put("op", 3);
                JSONObject d = new JSONObject();
                d.put("activities", new JSONArray());
                d.put("afk", false);
                d.put("since", JSONObject.NULL);
                d.put("status", "offline");
                clearPayload.put("d", d);
                webSocket.send(clearPayload.toString());
                webSocket.close(1000, "stopRPC");
            }
        } catch (Exception ignored) {}
        isInitialized = false;
        webSocket = null;
        stopHeartbeat();
        connectionStatus.postValue("Disconnected");
    }

    private void startHeartbeat(WebSocket ws, long intervalMs) {
        stopHeartbeat();
        heartbeatFuture = scheduler.scheduleAtFixedRate(() -> sendHeartbeat(ws), intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    private void stopHeartbeat() {
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(true);
            heartbeatFuture = null;
        }
    }

    private void sendHeartbeat(WebSocket ws) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("op", 1);
            payload.put("d", lastSequenceNumber != null ? lastSequenceNumber : JSONObject.NULL);
            if (ws != null) ws.send(payload.toString());
            Log.d(TAG, "Heartbeat sent (seq=" + lastSequenceNumber + ")");
        } catch (Exception e) {
            Log.e(TAG, "Failed to send heartbeat", e);
        }
    }

    private void sendIdentify(WebSocket ws, String userToken) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("op", 2);
            JSONObject d = new JSONObject();
            d.put("token", userToken);
            d.put("intents", 0);

            JSONObject props = new JSONObject();
            props.put("os", "linux");
            props.put("browser", "Midnight Music");
            props.put("device", "Midnight Music");
            d.put("properties", props);

            payload.put("d", d);
            if (ws != null) ws.send(payload.toString());
            Log.d(TAG, "IDENTIFY sent to Discord Gateway");
        } catch (Exception e) {
            Log.e(TAG, "Failed to send IDENTIFY", e);
        }
    }
}
