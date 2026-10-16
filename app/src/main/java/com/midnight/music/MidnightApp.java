package com.midnight.music;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import com.midnight.music.utils.ThemeManager;

public class MidnightApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize ThemeManager early and enforce Night mode by default
        ThemeManager themeManager = ThemeManager.getInstance(this);
        String selectedTheme = themeManager.getSelectedTheme();
        if (ThemeManager.THEME_LIGHT.equals(selectedTheme)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }
    }
}
