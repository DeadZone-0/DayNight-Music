package com.midnight.music.ui.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.midnight.music.R;
import com.midnight.music.databinding.FragmentSettingsBinding;
import com.midnight.music.utils.AccentManager;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.File;

public class SettingsActivity extends AppCompatActivity {
    private FragmentSettingsBinding binding;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "DaynightMusicPrefs";
    private static final String DARK_MODE_KEY = "darkMode";
    public static final String AUDIO_QUALITY_KEY = "audioQuality";
    public static final String DEFAULT_QUALITY = "320kbps";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = FragmentSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        setupToolbar();
        setupThemeToggle();
        setupAccentColor();
        setupAudioQuality();
        setupClearCacheButton();
        setupAppInfoButton();
        setupCheckUpdatesButton();

        // Observe accent colour to tint section headers and switches
        AccentManager.getInstance(this).getAccentColor().observe(this, color -> {
            if (binding == null) return;
            binding.headerAppearance.setTextColor(color);
            binding.headerData.setTextColor(color);
            binding.headerAbout.setTextColor(color);

            android.content.res.ColorStateList thumbStateList = new android.content.res.ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_checked},
                            new int[]{-android.R.attr.state_checked}
                    },
                    new int[]{
                            color,
                            androidx.core.content.ContextCompat.getColor(this, R.color.gray_light)
                    }
            );

            android.content.res.ColorStateList trackStateList = new android.content.res.ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_checked},
                            new int[]{-android.R.attr.state_checked}
                    },
                    new int[]{
                            android.graphics.Color.argb(76, android.graphics.Color.red(color), android.graphics.Color.green(color), android.graphics.Color.blue(color)), // 30% opacity
                            androidx.core.content.ContextCompat.getColor(this, R.color.gray_dark)
                    }
            );

            binding.themeSwitch.setThumbTintList(thumbStateList);
            binding.themeSwitch.setTrackTintList(trackStateList);
            binding.accentDynamicSwitch.setThumbTintList(thumbStateList);
            binding.accentDynamicSwitch.setTrackTintList(trackStateList);
        });
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupThemeToggle() {
        SwitchMaterial themeSwitch = binding.themeSwitch;
        boolean isDarkMode = sharedPreferences.getBoolean(DARK_MODE_KEY, true);
        themeSwitch.setChecked(isDarkMode);

        themeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean(DARK_MODE_KEY, isChecked).apply();
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });
    }

    // ============ Accent Color ============

    private void setupAccentColor() {
        AccentManager accentManager = AccentManager.getInstance(this);

        View[] swatches = {
                binding.swatchPurple, binding.swatchBlue, binding.swatchTeal,
                binding.swatchGreen, binding.swatchOrange, binding.swatchRed,
                binding.swatchPink
        };
        String[] colorNames = {"Purple", "Blue", "Teal", "Green", "Orange", "Red", "Pink"};

        // Tint each swatch with its colour
        for (int i = 0; i < swatches.length; i++) {
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(AccentManager.PRESET_COLORS[i]);
            bg.setStroke(2, Color.argb(50, 255, 255, 255));
            swatches[i].setBackground(bg);
        }

        // Initial state
        boolean isDynamic = accentManager.getMode() == AccentManager.MODE_DYNAMIC;
        binding.accentDynamicSwitch.setChecked(isDynamic);
        binding.accentSwatchesRow.setVisibility(isDynamic ? View.GONE : View.VISIBLE);
        binding.accentDynamicLabel.setVisibility(isDynamic ? View.VISIBLE : View.GONE);
        updateAccentSubtitle(accentManager, colorNames);
        highlightSelectedSwatch(swatches, accentManager.getStaticColor());

        // Dynamic toggle
        binding.accentDynamicSwitch.setOnCheckedChangeListener((btn, checked) -> {
            accentManager.setMode(checked ? AccentManager.MODE_DYNAMIC : AccentManager.MODE_STATIC);
            binding.accentSwatchesRow.setVisibility(checked ? View.GONE : View.VISIBLE);
            binding.accentDynamicLabel.setVisibility(checked ? View.VISIBLE : View.GONE);
            updateAccentSubtitle(accentManager, colorNames);
        });

        // Swatch clicks
        for (int i = 0; i < swatches.length; i++) {
            final int index = i;
            swatches[i].setOnClickListener(v -> {
                accentManager.setStaticColor(AccentManager.PRESET_COLORS[index]);
                highlightSelectedSwatch(swatches, AccentManager.PRESET_COLORS[index]);
                updateAccentSubtitle(accentManager, colorNames);
                v.animate().scaleX(1.3f).scaleY(1.3f).setDuration(100)
                        .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start())
                        .start();
            });
        }
    }

    private void updateAccentSubtitle(AccentManager am, String[] names) {
        if (am.getMode() == AccentManager.MODE_DYNAMIC) {
            binding.accentSubtitle.setText("Dynamic · Album art");
        } else {
            String name = "Custom";
            for (int i = 0; i < AccentManager.PRESET_COLORS.length; i++) {
                if (AccentManager.PRESET_COLORS[i] == am.getStaticColor()) {
                    name = names[i];
                    break;
                }
            }
            binding.accentSubtitle.setText("Static · " + name);
        }
    }

    private void highlightSelectedSwatch(View[] swatches, int selectedColor) {
        for (int i = 0; i < swatches.length; i++) {
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(AccentManager.PRESET_COLORS[i]);
            if (AccentManager.PRESET_COLORS[i] == selectedColor) {
                bg.setStroke(4, Color.WHITE);
            } else {
                bg.setStroke(2, Color.argb(50, 255, 255, 255));
            }
            swatches[i].setBackground(bg);
        }
    }

    // ============ Audio Quality ============

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
            default:
                binding.qualitySubtitle.setText("High Quality (320kbps)");
                break;
        }
    }

    // ============ Cache & Info ============

    private void setupClearCacheButton() {
        binding.clearCacheButton.setOnClickListener(v -> {
            new AlertDialog.Builder(this, R.style.AlertDialogTheme)
                    .setTitle("Clear Cache")
                    .setMessage("Are you sure you want to clear the app cache? This will remove all temporarily stored data.")
                    .setPositiveButton("Clear", (dialog, which) -> clearAppCache())
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void setupAppInfoButton() {
        binding.appInfoButton.setOnClickListener(v -> {
            String versionName = getAppVersion();
            new AlertDialog.Builder(this, R.style.AlertDialogTheme)
                    .setTitle("App Info")
                    .setMessage("Daynight Music\nVersion: " + versionName +
                            "\n\nA  music streaming app." +
                            "\n\n  by DeadZone-0")
                    .setPositiveButton("OK", null)
                    .show();
        });
    }

    private void setupCheckUpdatesButton() {
        if (binding.checkUpdatesButton != null) {
            binding.checkUpdatesButton.setOnClickListener(v -> {
                com.midnight.music.utils.UpdateManager updateManager = new com.midnight.music.utils.UpdateManager(this);
                updateManager.checkForUpdates(true);
            });
        }
    }

    private String getAppVersion() {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "Unknown";
        }
    }

    private void clearAppCache() {
        try {
            File cacheDir = getCacheDir();
            deleteDir(cacheDir);
            Toast.makeText(this, "Cache cleared successfully", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error clearing cache", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = deleteDir(new File(dir, child));
                    if (!success) {
                        return false;
                    }
                }
            }
            return dir.delete();
        } else if (dir != null && dir.isFile()) {
            return dir.delete();
        } else {
            return false;
        }
    }
}
