package com.midnight.music.utils;

import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.midnight.music.player.MusicPlayerManager;

/**
 * Manages a sleep timer that gracefully fades out and pauses playback.
 * Supports countdown-based timers and "end of current track" mode.
 */
public class SleepTimerManager {
    private static final String TAG = "SleepTimerManager";
    private static volatile SleepTimerManager instance;

    private CountDownTimer countDownTimer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // State
    private boolean isActive = false;
    private boolean endOfTrackMode = false;
    private long remainingMillis = 0;

    // LiveData for UI observation
    private final MutableLiveData<Long> remainingTimeLiveData = new MutableLiveData<>(0L);
    private final MutableLiveData<Boolean> isActiveLiveData = new MutableLiveData<>(false);

    // Fade-out config
    private static final long FADE_DURATION_MS = 10_000; // 10 seconds
    private static final int FADE_STEPS = 20;

    private SleepTimerManager() {}

    public static SleepTimerManager getInstance() {
        if (instance == null) {
            synchronized (SleepTimerManager.class) {
                if (instance == null) {
                    instance = new SleepTimerManager();
                }
            }
        }
        return instance;
    }

    /**
     * Start a countdown timer for the given duration in minutes.
     */
    public void startTimer(int minutes, MusicPlayerManager playerManager) {
        cancel(); // Cancel any existing timer

        endOfTrackMode = false;
        long millis = minutes * 60_000L;
        remainingMillis = millis;
        isActive = true;
        isActiveLiveData.postValue(true);

        Log.d(TAG, "Sleep timer started: " + minutes + " minutes");

        countDownTimer = new CountDownTimer(millis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                remainingMillis = millisUntilFinished;
                remainingTimeLiveData.postValue(millisUntilFinished);

                // Start fade-out when approaching the end
                if (millisUntilFinished <= FADE_DURATION_MS) {
                    float volume = (float) millisUntilFinished / FADE_DURATION_MS;
                    try {
                        playerManager.getPlayer().setVolume(Math.max(0f, volume));
                    } catch (Exception e) {
                        Log.e(TAG, "Error setting volume during fade", e);
                    }
                }
            }

            @Override
            public void onFinish() {
                Log.d(TAG, "Sleep timer finished — pausing playback");
                try {
                    playerManager.getPlayer().setVolume(0f);
                    playerManager.getPlayer().pause();
                    // Restore volume for next play
                    mainHandler.postDelayed(() -> {
                        try {
                            playerManager.getPlayer().setVolume(1f);
                        } catch (Exception e) {
                            Log.e(TAG, "Error restoring volume", e);
                        }
                    }, 500);
                } catch (Exception e) {
                    Log.e(TAG, "Error pausing player on timer finish", e);
                }
                resetState();
            }
        };
        countDownTimer.start();
    }

    /**
     * Enable "End of Track" mode: playback will pause after the current song ends.
     */
    public void startEndOfTrack() {
        cancel();
        endOfTrackMode = true;
        isActive = true;
        isActiveLiveData.postValue(true);
        remainingTimeLiveData.postValue(-1L); // -1 signals "end of track" mode
        Log.d(TAG, "Sleep timer: End of track mode activated");
    }

    /**
     * Should be called from MusicPlayerManager when a song ends (STATE_ENDED).
     * Returns true if the player should NOT advance to the next song.
     */
    public boolean shouldPauseAtEndOfTrack() {
        if (endOfTrackMode && isActive) {
            Log.d(TAG, "End-of-track sleep: pausing now");
            resetState();
            return true;
        }
        return false;
    }

    /**
     * Cancel any active timer.
     */
    public void cancel() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        resetState();
    }

    private void resetState() {
        isActive = false;
        endOfTrackMode = false;
        remainingMillis = 0;
        isActiveLiveData.postValue(false);
        remainingTimeLiveData.postValue(0L);
    }

    // ── Getters ──

    public boolean isActive() { return isActive; }
    public boolean isEndOfTrackMode() { return endOfTrackMode; }
    public long getRemainingMillis() { return remainingMillis; }
    public LiveData<Long> getRemainingTimeLiveData() { return remainingTimeLiveData; }
    public LiveData<Boolean> getIsActiveLiveData() { return isActiveLiveData; }

    /**
     * Formats remaining milliseconds into "Xm" or "Xh Ym" string.
     */
    public static String formatRemaining(long millis) {
        if (millis <= 0) return "";
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long hours = minutes / 60;
        minutes = minutes % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }
}
