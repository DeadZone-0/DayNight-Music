package com.midnight.music.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.palette.graphics.Palette;

/**
 * Manages app-wide accent colour.
 * Two modes:
 *   DYNAMIC – accent tracks the dominant colour of the currently playing song.
 *   STATIC  – user picks a fixed colour from a preset palette.
 *
 * Persisted in SharedPreferences so the choice survives restarts.
 */
public class AccentManager {
    private static final String PREFS_NAME = "accent_prefs";
    private static final String KEY_MODE = "accent_mode";          // "dynamic" | "static"
    private static final String KEY_STATIC_COLOR = "accent_color"; // hex int

    public static final int MODE_DYNAMIC = 0;
    public static final int MODE_STATIC = 1;

    /** 7 preset accent colours */
    public static final int[] PRESET_COLORS = {
            Color.parseColor("#9C4DFF"), // Purple (default)
            Color.parseColor("#82B1FF"), // Blue
            Color.parseColor("#64FFDA"), // Teal
            Color.parseColor("#69F0AE"), // Green
            Color.parseColor("#FFD180"), // Orange
            Color.parseColor("#FF8A80"), // Red
            Color.parseColor("#FF80AB"), // Pink
    };

    public static final int DEFAULT_ACCENT = PRESET_COLORS[0]; // Purple

    private static volatile AccentManager instance;

    private final SharedPreferences prefs;
    private final MutableLiveData<Integer> accentColorLiveData = new MutableLiveData<>();
    private int currentMode;
    private int staticColor;
    private int dynamicColor; // from palette

    private AccentManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        currentMode = prefs.getInt(KEY_MODE, MODE_STATIC);
        staticColor = prefs.getInt(KEY_STATIC_COLOR, DEFAULT_ACCENT);
        dynamicColor = DEFAULT_ACCENT;
        accentColorLiveData.postValue(resolveColor());
    }

    public static AccentManager getInstance(Context context) {
        if (instance == null) {
            synchronized (AccentManager.class) {
                if (instance == null) {
                    instance = new AccentManager(context);
                }
            }
        }
        return instance;
    }

    // ─── Getters ──────────────────────────────────────────────

    /** Observable accent colour that UI components can observe. */
    public LiveData<Integer> getAccentColor() {
        return accentColorLiveData;
    }

    /** Synchronous read. */
    public int getAccentColorValue() {
        Integer val = accentColorLiveData.getValue();
        return val != null ? val : DEFAULT_ACCENT;
    }

    public int getMode() {
        return currentMode;
    }

    public int getStaticColor() {
        return staticColor;
    }

    // ─── Setters ──────────────────────────────────────────────

    /** Switch between DYNAMIC and STATIC modes. */
    public void setMode(int mode) {
        currentMode = mode;
        prefs.edit().putInt(KEY_MODE, mode).apply();
        accentColorLiveData.postValue(resolveColor());
    }

    /** Set a new static accent colour and switch to static mode. */
    public void setStaticColor(int color) {
        staticColor = color;
        currentMode = MODE_STATIC;
        prefs.edit()
                .putInt(KEY_STATIC_COLOR, color)
                .putInt(KEY_MODE, MODE_STATIC)
                .apply();
        accentColorLiveData.postValue(resolveColor());
    }

    /**
     * Called every time a new album palette is extracted.
     * Only updates the live accent if in DYNAMIC mode.
     */
    public void updateFromPalette(Palette palette) {
        if (palette == null) return;
        int vibrant = palette.getVibrantColor(DEFAULT_ACCENT);
        int dominant = palette.getDominantColor(vibrant);
        // Prefer vibrant – it looks better as an accent; fall back to dominant
        dynamicColor = (vibrant != DEFAULT_ACCENT) ? vibrant : dominant;

        if (currentMode == MODE_DYNAMIC) {
            accentColorLiveData.postValue(dynamicColor);
        }
    }

    // ─── Internal ─────────────────────────────────────────────

    private int resolveColor() {
        return currentMode == MODE_DYNAMIC ? dynamicColor : staticColor;
    }
}
