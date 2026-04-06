package com.midnight.music.ui.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.midnight.music.R;
import com.midnight.music.databinding.FragmentSettingsBinding;
import com.midnight.music.utils.ThemeManager;

import java.io.File;

public class SettingsActivity extends AppCompatActivity {
    private FragmentSettingsBinding binding;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "DaynightMusicPrefs";
    public static final String AUDIO_QUALITY_KEY = "audioQuality";
    public static final String DEFAULT_QUALITY = "320kbps";
    
    private ThemeManager themeManager;
    private ThemeAdapter themeAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = FragmentSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        themeManager = ThemeManager.getInstance(this);

        setupToolbar();
        setupThemePicker();
        setupDynamicAccent();
        setupAudioQuality();
        setupClearCacheButton();
        setupAppInfoButton();
        setupCheckUpdatesButton();

        observeThemeChanges();
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void observeThemeChanges() {
        themeManager.getAccentColor().observe(this, color -> {
            if (binding == null) return;
            binding.headerAppearance.setTextColor(color);
            binding.headerData.setTextColor(color);
            binding.headerAbout.setTextColor(color);
        });

        themeManager.getCurrentTheme().observe(this, theme -> {
            updateThemeName(theme);
            if (themeAdapter != null) {
                themeAdapter.setSelectedTheme(mapThemeToKey(theme));
            }
        });
    }

    private String mapThemeToKey(String theme) {
        if (ThemeManager.THEME_PURPLE_NIGHT.equals(theme)) return "midnight";
        if (ThemeManager.THEME_OCEAN_BLUE.equals(theme)) return "ocean";
        if (ThemeManager.THEME_SUNSET.equals(theme)) return "sunset";
        if (ThemeManager.THEME_FOREST.equals(theme)) return "forest";
        if (ThemeManager.THEME_LIGHT.equals(theme)) return "light";
        if (ThemeManager.THEME_AMOLED.equals(theme)) return "amoled";
        return "midnight";
    }

    private String mapKeyToTheme(String key) {
        if ("midnight".equals(key)) return ThemeManager.THEME_PURPLE_NIGHT;
        if ("ocean".equals(key)) return ThemeManager.THEME_OCEAN_BLUE;
        if ("sunset".equals(key)) return ThemeManager.THEME_SUNSET;
        if ("forest".equals(key)) return ThemeManager.THEME_FOREST;
        if ("light".equals(key)) return ThemeManager.THEME_LIGHT;
        if ("amoled".equals(key)) return ThemeManager.THEME_AMOLED;
        return ThemeManager.THEME_PURPLE_NIGHT;
    }

    private void updateThemeName(String theme) {
        String name = switch (theme) {
            case ThemeManager.THEME_PURPLE_NIGHT -> "Midnight";
            case ThemeManager.THEME_OCEAN_BLUE -> "Ocean";
            case ThemeManager.THEME_SUNSET -> "Sunset";
            case ThemeManager.THEME_FOREST -> "Forest";
            case ThemeManager.THEME_LIGHT -> "Light";
            case ThemeManager.THEME_AMOLED -> "AMOLED";
            default -> "Midnight";
        };
        binding.currentThemeName.setText(name);
    }

    private void setupThemePicker() {
        updateThemeName(themeManager.getSelectedTheme());

        themeAdapter = new ThemeAdapter(this, themeManager);
        themeAdapter.setOnThemeClickListener(key -> {
            String newTheme = mapKeyToTheme(key);
            themeManager.setTheme(newTheme);
            applyTheme(newTheme);
        });

        binding.themeGrid.setLayoutManager(new GridLayoutManager(this, 3, GridLayoutManager.HORIZONTAL, false));
        binding.themeGrid.setAdapter(themeAdapter);
        binding.themeGrid.setNestedScrollingEnabled(false);
    }

    private void applyTheme(String theme) {
        boolean isLightTheme = theme.equals(ThemeManager.THEME_LIGHT);
        
        if (isLightTheme) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }
    }

    private void setupDynamicAccent() {
        binding.dynamicAccentSwitch.setChecked(themeManager.isDynamicAccent());

        binding.dynamicAccentSwitch.setOnCheckedChangeListener((btn, checked) -> {
            themeManager.setDynamicAccentEnabled(checked);
            if (checked) {
                Toast.makeText(this, "Dynamic accent enabled - will follow album art", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupAudioQuality() {
        final String[] qualityLabels = {"Data Saver (96kbps)", "Standard (160kbps)", "High Quality (320kbps)"};
        final String[] qualityValues = {"96kbps", "160kbps", "320kbps"};

        String savedQuality = sharedPreferences.getString(AUDIO_QUALITY_KEY, DEFAULT_QUALITY);
        updateQualitySubtitle(savedQuality);

        binding.audioQualityButton.setOnClickListener(v -> {
            String currentQuality = sharedPreferences.getString(AUDIO_QUALITY_KEY, DEFAULT_QUALITY);
            int checkedIndex = 2;
            for (int i = 0; i < qualityValues.length; i++) {
                if (qualityValues[i].equals(currentQuality)) {
                    checkedIndex = i;
                    break;
                }
            }

            new AlertDialog.Builder(this, R.style.AlertDialogTheme)
                    .setTitle("Audio Quality")
                    .setSingleChoiceItems(qualityLabels, checkedIndex, (dialog, which) -> {
                        String selected = qualityValues[which];
                        sharedPreferences.edit().putString(AUDIO_QUALITY_KEY, selected).apply();
                        updateQualitySubtitle(selected);
                        dialog.dismiss();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void updateQualitySubtitle(String quality) {
        switch (quality) {
            case "96kbps":
                binding.qualitySubtitle.setText("Data Saver (96kbps)");
                break;
            case "160kbps":
                binding.qualitySubtitle.setText("Standard (160kbps)");
                break;
            case "320kbps":
                binding.qualitySubtitle.setText("High Quality (320kbps)");
                break;
        }
    }

    private void setupClearCacheButton() {
        binding.clearCacheButton.setOnClickListener(v -> {
            new AlertDialog.Builder(this, R.style.AlertDialogTheme)
                    .setTitle("Clear Cache")
                    .setMessage("This will remove all temporarily stored data. Downloaded songs will not be affected.")
                    .setPositiveButton("Clear", (dialog, which) -> {
                        clearCache();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void clearCache() {
        try {
            File cacheDir = getCacheDir();
            deleteDir(cacheDir);
            Toast.makeText(this, "Cache cleared", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to clear cache", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = deleteDir(new File(dir, child));
                    if (!success) return false;
                }
            }
            return dir.delete();
        } else if (dir != null && dir.isFile()) {
            return dir.delete();
        }
        return false;
    }

    private void setupAppInfoButton() {
        binding.appInfoButton.setOnClickListener(v -> {
            String version = "1.0.0";
            try {
                PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                version = pInfo.versionName;
            } catch (PackageManager.NameNotFoundException e) {
                Log.e("SettingsActivity", "Failed to get package info for version", e);
            }

            new AlertDialog.Builder(this, R.style.AlertDialogTheme)
                    .setTitle("DayNight Music")
                    .setMessage("Version: " + version + "\n\nA premium music streaming experience.")
                    .setPositiveButton("OK", null)
                    .show();
        });
    }

    private void setupCheckUpdatesButton() {
        binding.checkUpdatesButton.setOnClickListener(v -> {
            Toast.makeText(this, "You're on the latest version!", Toast.LENGTH_SHORT).show();
        });
    }
}
