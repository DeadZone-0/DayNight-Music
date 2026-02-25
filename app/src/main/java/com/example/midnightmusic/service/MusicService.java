package com.example.midnightmusic.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.midnightmusic.MainActivity;
import com.example.midnightmusic.R;
import com.example.midnightmusic.data.model.Song;
import com.example.midnightmusic.player.MusicPlayerManager;

/**
 * Foreground Service that shows a media notification with playback controls.
 * Uses a manual NotificationCompat approach for maximum reliability.
 */
public class MusicService extends Service {
    private static final String TAG = "MusicService";
    private static final String CHANNEL_ID = "midnight_music_playback";
    private static final int NOTIFICATION_ID = 1;
    private static final int POSITION_UPDATE_INTERVAL_MS = 1000;

    // Broadcast action constants
    public static final String ACTION_PLAY_PAUSE = "com.example.midnightmusic.ACTION_PLAY_PAUSE";
    public static final String ACTION_NEXT = "com.example.midnightmusic.ACTION_NEXT";
    public static final String ACTION_PREVIOUS = "com.example.midnightmusic.ACTION_PREVIOUS";
    public static final String ACTION_CLOSE = "com.example.midnightmusic.ACTION_CLOSE";
    // Action to update the notification when song or state changes
    public static final String ACTION_UPDATE = "com.example.midnightmusic.ACTION_UPDATE";

    private MediaSessionCompat mediaSession;
    private MusicPlayerManager playerManager;
    private NotificationManager notificationManager;
    private BroadcastReceiver controlReceiver;
    private android.os.Handler positionHandler;
    private Runnable positionUpdater;

    /**
     * Start the music notification service.
     */
    public static void startService(Context context) {
        try {
            Intent intent = new Intent(context, MusicService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting MusicService", e);
        }
    }

    /**
     * Stop the music notification service.
     */
    public static void stopService(Context context) {
        try {
            context.stopService(new Intent(context, MusicService.class));
        } catch (Exception e) {
            Log.e(TAG, "Error stopping MusicService", e);
        }
    }

    /**
     * Tell the running service to refresh the notification (e.g. after song change).
     */
    public static void updateNotification(Context context) {
        try {
            Intent intent = new Intent(context, MusicService.class);
            intent.setAction(ACTION_UPDATE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating notification", e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        playerManager = MusicPlayerManager.getInstance(this);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();
        setupMediaSession();
        registerControlReceiver();
        startPositionUpdater();
        Log.d(TAG, "MusicService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            handleAction(intent.getAction());
        } else {
            // First start — show a notification immediately to satisfy foreground requirement
            showNotification();
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Music Playback",
                    NotificationManager.IMPORTANCE_LOW  // Low so it doesn't make sounds
            );
            channel.setDescription("Shows current song and playback controls");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void setupMediaSession() {
        mediaSession = new MediaSessionCompat(this, "MidnightMusic");
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                if (!playerManager.isPlaying()) playerManager.togglePlayPause();
                showNotification();
            }

            @Override
            public void onPause() {
                if (playerManager.isPlaying()) playerManager.togglePlayPause();
                showNotification();
            }

            @Override
            public void onSkipToNext() {
                playerManager.skipToNext();
                showNotification();
            }

            @Override
            public void onSkipToPrevious() {
                playerManager.skipToPrevious();
                showNotification();
            }

            @Override
            public void onSeekTo(long pos) {
                playerManager.seekTo(pos);
                showNotification();
            }

            @Override
            public void onStop() {
                stopSelf();
            }
        });
        mediaSession.setActive(true);
    }

    private void registerControlReceiver() {
        controlReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || intent.getAction() == null) return;
                handleAction(intent.getAction());
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PLAY_PAUSE);
        filter.addAction(ACTION_NEXT);
        filter.addAction(ACTION_PREVIOUS);
        filter.addAction(ACTION_CLOSE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(controlReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(controlReceiver, filter);
        }
    }

    private void handleAction(String action) {
        switch (action) {
            case ACTION_PLAY_PAUSE:
                playerManager.togglePlayPause();
                showNotification();
                break;
            case ACTION_NEXT:
                playerManager.skipToNext();
                // Notification will be updated via ACTION_UPDATE from MusicPlayerManager
                break;
            case ACTION_PREVIOUS:
                playerManager.skipToPrevious();
                // Notification will be updated via ACTION_UPDATE from MusicPlayerManager
                break;
            case ACTION_CLOSE:
                stopSelf();
                break;
            case ACTION_UPDATE:
                showNotification();
                break;
        }
    }

    private void showNotification() {
        Song song = playerManager.getCurrentSong();
        boolean isPlaying = playerManager.isPlaying();

        // Update MediaSession state
        updateMediaSessionState(song, isPlaying);

        // Build notification with placeholder art first (fast)
        Notification notification = buildNotification(song, isPlaying, null);
        startForeground(NOTIFICATION_ID, notification);

        // Then load album art async and update
        if (song != null && song.getImageUrl() != null) {
            try {
                Glide.with(this)
                        .asBitmap()
                        .load(song.getImageUrl())
                        .into(new CustomTarget<Bitmap>(256, 256) {
                            @Override
                            public void onResourceReady(@NonNull Bitmap bitmap,
                                                        @Nullable Transition<? super Bitmap> transition) {
                                Notification updated = buildNotification(song, playerManager.isPlaying(), bitmap);
                                notificationManager.notify(NOTIFICATION_ID, updated);
                            }

                            @Override
                            public void onLoadCleared(@Nullable Drawable placeholder) {
                                // No-op
                            }
                        });
            } catch (Exception e) {
                Log.e(TAG, "Error loading album art for notification", e);
            }
        }
    }

    private Notification buildNotification(Song song, boolean isPlaying, Bitmap albumArt) {
        String title = song != null ? song.getSong() : "Midnight Music";
        String artist = song != null ? song.getSingers() : "";

        // PendingIntent to open the app when notification body is tapped
        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Action: Previous
        PendingIntent prevIntent = PendingIntent.getBroadcast(this, 1,
                new Intent(ACTION_PREVIOUS).setPackage(getPackageName()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Action: Play/Pause
        PendingIntent playPauseIntent = PendingIntent.getBroadcast(this, 2,
                new Intent(ACTION_PLAY_PAUSE).setPackage(getPackageName()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Action: Next
        PendingIntent nextIntent = PendingIntent.getBroadcast(this, 3,
                new Intent(ACTION_NEXT).setPackage(getPackageName()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Action: Close
        PendingIntent closeIntent = PendingIntent.getBroadcast(this, 4,
                new Intent(ACTION_CLOSE).setPackage(getPackageName()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_play)
                .setContentTitle(title)
                .setContentText(artist)
                .setContentIntent(contentIntent)
                .setDeleteIntent(closeIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(isPlaying)
                .setOnlyAlertOnce(true)
                // Add media control actions
                .addAction(R.drawable.ic_previous, "Previous", prevIntent)
                .addAction(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play,
                        isPlaying ? "Pause" : "Play", playPauseIntent)
                .addAction(R.drawable.ic_next, "Next", nextIntent)
                // Apply MediaStyle — show all 3 actions in compact view
                .setStyle(new MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2)
                        .setShowCancelButton(true)
                        .setCancelButtonIntent(closeIntent));

        if (albumArt != null) {
            builder.setLargeIcon(albumArt);
        }

        return builder.build();
    }

    private void updateMediaSessionState(Song song, boolean isPlaying) {
        try {
            // Set playback state
            PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                    .setActions(
                            PlaybackStateCompat.ACTION_PLAY |
                            PlaybackStateCompat.ACTION_PAUSE |
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                            PlaybackStateCompat.ACTION_PLAY_PAUSE |
                            PlaybackStateCompat.ACTION_SEEK_TO
                    )
                    .setState(
                            isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                            playerManager.getPlayer().getCurrentPosition(),
                            1.0f
                    );
            mediaSession.setPlaybackState(stateBuilder.build());

            // Set metadata
            if (song != null) {
                MediaMetadataCompat.Builder metaBuilder = new MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.getSong())
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.getSingers())
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.getAlbum());

                if (song.getDuration() != null) {
                    try {
                        long durationMs = Long.parseLong(song.getDuration()) * 1000;
                        metaBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs);
                    } catch (NumberFormatException ignored) {}
                }

                mediaSession.setMetadata(metaBuilder.build());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating media session state", e);
        }
    }

    /**
     * Periodically updates the playback position in the MediaSession
     * so the system seekbar stays accurate.
     */
    private void startPositionUpdater() {
        positionHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        positionUpdater = new Runnable() {
            @Override
            public void run() {
                try {
                    if (playerManager != null && playerManager.isPlaying()) {
                        updateMediaSessionState(playerManager.getCurrentSong(), true);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in position updater", e);
                }
                positionHandler.postDelayed(this, POSITION_UPDATE_INTERVAL_MS);
            }
        };
        positionHandler.post(positionUpdater);
    }

    @Override
    public void onDestroy() {
        try {
            if (positionHandler != null) {
                positionHandler.removeCallbacksAndMessages(null);
                positionHandler = null;
            }
            if (controlReceiver != null) {
                unregisterReceiver(controlReceiver);
                controlReceiver = null;
            }
            if (mediaSession != null) {
                mediaSession.setActive(false);
                mediaSession.release();
                mediaSession = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy", e);
        }
        super.onDestroy();
        Log.d(TAG, "MusicService destroyed");
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (!playerManager.isPlaying()) {
            stopSelf();
        }
    }
}
