package com.midnight.music.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.palette.graphics.Palette;

import com.midnight.music.R;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages app-wide themes and dynamic accent.
 * 
 * Themes include:
 * - purple_night (default dark)
 * - ocean_blue
 * - sunset
 * - forest
 * - light
 * - amoled
 * 
 * Dynamic accent works globally with all themes - when enabled, the accent
 * color is extracted from the currently playing song's album art.
 */
public class ThemeManager {
    private static final String PREFS_NAME = "theme_prefs";
    private static final String KEY_THEME = "selected_theme";
    private static final String KEY_DYNAMIC_ACCENT = "dynamic_accent_enabled";
    private static final String KEY_FOLLOW_SYSTEM = "follow_system_theme";

    public static final String THEME_PURPLE_NIGHT = "purple_night";
    public static final String THEME_OCEAN_BLUE = "ocean_blue";
    public static final String THEME_SUNSET = "sunset";
    public static final String THEME_FOREST = "forest";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_AMOLED = "amoled";

    public static final String DEFAULT_THEME = THEME_PURPLE_NIGHT;

    private static volatile ThemeManager instance;

    private final SharedPreferences prefs;
    private final MutableLiveData<String> currentTheme = new MutableLiveData<>();
    private final MutableLiveData<Integer> accentColor = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isDynamicAccent = new MutableLiveData<>();
    
    private volatile String selectedTheme;
    private final AtomicBoolean dynamicAccentEnabled = new AtomicBoolean(false);
    private final AtomicBoolean followSystemEnabled = new AtomicBoolean(false);
    private final AtomicInteger dynamicColor = new AtomicInteger(Color.parseColor("#BB86FC"));
    
    private final Map<String, ThemeColors> themeColorsMap = new HashMap<>();

    private volatile ThemeColors currentColors;

    private ThemeManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        selectedTheme = prefs.getString(KEY_THEME, DEFAULT_THEME);
        dynamicAccentEnabled.set(prefs.getBoolean(KEY_DYNAMIC_ACCENT, false));
        followSystemEnabled.set(prefs.getBoolean(KEY_FOLLOW_SYSTEM, false));
        
        initializeThemeColors();
        
        // Validate selectedTheme against themeColorsMap
        if (!themeColorsMap.containsKey(selectedTheme)) {
            selectedTheme = DEFAULT_THEME;
        }
        
        currentColors = themeColorsMap.get(selectedTheme);
        if (currentColors == null) {
            currentColors = themeColorsMap.get(DEFAULT_THEME);
            selectedTheme = DEFAULT_THEME;
        }
        
        currentTheme.postValue(selectedTheme);
        isDynamicAccent.postValue(dynamicAccentEnabled.get());
        accentColor.postValue(dynamicAccentEnabled.get() ? dynamicColor.get() : currentColors.primary);
    }

    public static ThemeManager getInstance(Context context) {
        if (instance == null) {
            synchronized (ThemeManager.class) {
                if (instance == null) {
                    instance = new ThemeManager(context);
                }
            }
        }
        return instance;
    }

    private void initializeThemeColors() {
        themeColorsMap.put(THEME_PURPLE_NIGHT, new ThemeColors(
                Color.parseColor("#BB86FC"), Color.parseColor("#9C4DFF"),
                Color.parseColor("#0F0F0F"), Color.parseColor("#1A1A1A"),
                Color.parseColor("#2B2B2B"), Color.parseColor("#242424"),
                Color.parseColor("#E1E1E1"), Color.parseColor("#AAAAAA"),
                Color.parseColor("#1ABB86FC"), Color.parseColor("#33BB86FC"),
                Color.parseColor("#BB86FC"), Color.parseColor("#9C4DFF")
        ));
        
        themeColorsMap.put(THEME_OCEAN_BLUE, new ThemeColors(
                Color.parseColor("#64B5F6"), Color.parseColor("#2196F3"),
                Color.parseColor("#0D1B2A"), Color.parseColor("#152535"),
                Color.parseColor("#1E3347"), Color.parseColor("#1A2D40"),
                Color.parseColor("#E3F2FD"), Color.parseColor("#90CAF9"),
                Color.parseColor("#1A64B5F6"), Color.parseColor("#3364B5F6"),
                Color.parseColor("#64B5F6"), Color.parseColor("#2196F3")
        ));
        
        themeColorsMap.put(THEME_SUNSET, new ThemeColors(
                Color.parseColor("#FFAB40"), Color.parseColor("#FF6D00"),
                Color.parseColor("#1A0F0A"), Color.parseColor("#251510"),
                Color.parseColor("#332015"), Color.parseColor("#2A1810"),
                Color.parseColor("#FFF3E0"), Color.parseColor("#FFCC80"),
                Color.parseColor("#1AFFAB40"), Color.parseColor("#33FFAB40"),
                Color.parseColor("#FF6D00"), Color.parseColor("#FFAB40")
        ));
        
        themeColorsMap.put(THEME_FOREST, new ThemeColors(
                Color.parseColor("#69F0AE"), Color.parseColor("#4CAF50"),
                Color.parseColor("#0A1A0F"), Color.parseColor("#102015"),
                Color.parseColor("#153020"), Color.parseColor("#0F2518"),
                Color.parseColor("#E8F5E9"), Color.parseColor("#A5D6A7"),
                Color.parseColor("#1A69F0AE"), Color.parseColor("#3369F0AE"),
                Color.parseColor("#4CAF50"), Color.parseColor("#69F0AE")
        ));
        
        themeColorsMap.put(THEME_LIGHT, new ThemeColors(
                Color.parseColor("#7A2BE2"), Color.parseColor("#5E21B0"),
                Color.parseColor("#F5F5F7"), Color.parseColor("#FFFFFF"),
                Color.parseColor("#F0EDF2"), Color.parseColor("#FFFFFF"),
                Color.parseColor("#1C1B1F"), Color.parseColor("#49454F"),
                Color.parseColor("#1A7A2BE2"), Color.parseColor("#337A2BE2"),
                Color.parseColor("#7A2BE2"), Color.parseColor("#9C4DFF")
        ));
        
        themeColorsMap.put(THEME_AMOLED, new ThemeColors(
                Color.parseColor("#BB86FC"), Color.parseColor("#9C4DFF"),
                Color.parseColor("#000000"), Color.parseColor("#000000"),
                Color.parseColor("#121212"), Color.parseColor("#0D0D0D"),
                Color.parseColor("#E1E1E1"), Color.parseColor("#AAAAAA"),
                Color.parseColor("#1ABB86FC"), Color.parseColor("#33BB86FC"),
                Color.parseColor("#BB86FC"), Color.parseColor("#9C4DFF")
        ));
    }

    // ─── Getters ──────────────────────────────────────────────

    public LiveData<String> getCurrentTheme() {
        return currentTheme;
    }

    public String getSelectedTheme() {
        return selectedTheme;
    }

    public LiveData<Integer> getAccentColor() {
        return accentColor;
    }

    public int getAccentColorValue() {
        Integer val = accentColor.getValue();
        return val != null ? val : currentColors.primary;
    }

    public LiveData<Boolean> isDynamicAccentEnabled() {
        return isDynamicAccent;
    }

    public boolean isDynamicAccent() {
        return dynamicAccentEnabled.get();
    }

    public boolean isFollowSystem() {
        return followSystemEnabled.get();
    }

    public ThemeColors getCurrentColors() {
        return currentColors;
    }

    public int getPrimaryColor() {
        return dynamicAccentEnabled.get() ? dynamicColor.get() : currentColors.primary;
    }

    public int getAccent20Color() {
        return dynamicAccentEnabled.get() ? adjustAlpha(dynamicColor.get(), 0.2f) : currentColors.accent20;
    }

    // ─── Setters ──────────────────────────────────────────────

    public void setTheme(String theme) {
        if (!themeColorsMap.containsKey(theme)) {
            theme = DEFAULT_THEME;
        }
        
        selectedTheme = theme;
        currentColors = themeColorsMap.get(theme);
        
        prefs.edit().putString(KEY_THEME, theme).apply();
        currentTheme.postValue(theme);
        
        if (!dynamicAccentEnabled.get()) {
            accentColor.postValue(currentColors.primary);
        }
    }

    public void setDynamicAccentEnabled(boolean enabled) {
        dynamicAccentEnabled.set(enabled);
        prefs.edit().putBoolean(KEY_DYNAMIC_ACCENT, enabled).apply();
        isDynamicAccent.postValue(enabled);
        
        if (enabled) {
            accentColor.postValue(dynamicColor.get());
        } else {
            accentColor.postValue(currentColors.primary);
        }
    }

    public void setFollowSystem(boolean enabled) {
        followSystemEnabled.set(enabled);
        prefs.edit().putBoolean(KEY_FOLLOW_SYSTEM, enabled).apply();
        // TODO: Implement system theme observation
        // Call applySystemTheme() to apply system theme immediately
        // Register UiModeManager.OnNightModeChangedListener to observe system theme changes
        // and call updateThemeBasedOnSystem() when system theme toggling is detected
    }

    /**
     * Called when album art palette is extracted.
     * Updates accent color if dynamic accent is enabled.
     */
    public void updateFromPalette(Palette palette) {
        if (palette == null) return;
        
        int dynamicColorValue = dynamicColor.get();
        
        // Try vibrant swatch first
        Palette.Swatch vibrantSwatch = palette.getVibrantSwatch();
        if (vibrantSwatch != null) {
            dynamicColorValue = vibrantSwatch.getRgb();
        } else {
            // Fall back to dominant swatch
            Palette.Swatch dominantSwatch = palette.getDominantSwatch();
            if (dominantSwatch != null) {
                dynamicColorValue = dominantSwatch.getRgb();
            } else {
                // Last resort: use dominant color with fallback
                dynamicColorValue = palette.getDominantColor(Color.parseColor("#BB86FC"));
            }
        }
        
        dynamicColor.set(dynamicColorValue);
        
        if (dynamicAccentEnabled.get()) {
            accentColor.postValue(dynamicColorValue);
        }
    }

    private int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return Color.argb(alpha, red, green, blue);
    }

    /**
     * Create a drawable with the current accent color
     */
    public GradientDrawable createAccentDrawable(Context context) {
        int color = getAccentColorValue();
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    // ─── Theme Colors Data Class ─────────────────────────────────

    public static class ThemeColors {
        public final int primary;
        public final int primaryVariant;
        public final int background;
        public final int surface;
        public final int surfaceVariant;
        public final int cardBackground;
        public final int textPrimary;
        public final int textSecondary;
        public final int rippleAccent;
        public final int accent20;
        public final int gradientStart;
        public final int gradientEnd;

        public ThemeColors(int primary, int primaryVariant, int background, int surface,
                         int surfaceVariant, int cardBackground, int textPrimary, int textSecondary,
                         int rippleAccent, int accent20, int gradientStart, int gradientEnd) {
            this.primary = primary;
            this.primaryVariant = primaryVariant;
            this.background = background;
            this.surface = surface;
            this.surfaceVariant = surfaceVariant;
            this.cardBackground = cardBackground;
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
            this.rippleAccent = rippleAccent;
            this.accent20 = accent20;
            this.gradientStart = gradientStart;
            this.gradientEnd = gradientEnd;
        }
    }
}
