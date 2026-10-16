package com.midnight.music.ui.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
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
import com.midnight.music.data.discord.DiscordRpcManager;
import com.midnight.music.databinding.FragmentSettingsBinding;
import com.midnight.music.ui.discord.DiscordRpcActivity;
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
        setupDiscordRpc();
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
            binding.headerIntegrations.setTextColor(color);
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
        if (ThemeManager.THEME_MONOCHROME.equals(theme)) return ThemeManager.THEME_MONOCHROME;
        if (ThemeManager.THEME_CYBER_NEON.equals(theme)) return ThemeManager.THEME_CYBER_NEON;
        if (ThemeManager.THEME_SAKURA_BLOOM.equals(theme)) return ThemeManager.THEME_SAKURA_BLOOM;
        if (ThemeManager.THEME_AMBER_GOLD.equals(theme)) return ThemeManager.THEME_AMBER_GOLD;
        if (ThemeManager.THEME_EVA_PURPLE.equals(theme)) return ThemeManager.THEME_EVA_PURPLE;
        // Legacy fallback
        if (ThemeManager.THEME_PURPLE_NIGHT.equals(theme) || ThemeManager.THEME_AMOLED.equals(theme)) return ThemeManager.THEME_MONOCHROME;
        if (ThemeManager.THEME_OCEAN_BLUE.equals(theme) || ThemeManager.THEME_FOREST.equals(theme)) return ThemeManager.THEME_CYBER_NEON;
        if (ThemeManager.THEME_SUNSET.equals(theme)) return ThemeManager.THEME_AMBER_GOLD;
        return ThemeManager.THEME_MONOCHROME;
    }

    private String mapKeyToTheme(String key) {
        if (ThemeManager.THEME_CYBER_NEON.equals(key)) return ThemeManager.THEME_CYBER_NEON;
        if (ThemeManager.THEME_SAKURA_BLOOM.equals(key)) return ThemeManager.THEME_SAKURA_BLOOM;
        if (ThemeManager.THEME_AMBER_GOLD.equals(key)) return ThemeManager.THEME_AMBER_GOLD;
        if (ThemeManager.THEME_EVA_PURPLE.equals(key)) return ThemeManager.THEME_EVA_PURPLE;
        return ThemeManager.THEME_MONOCHROME;
    }

    private void updateThemeName(String theme) {
        String name = switch (theme) {
            case ThemeManager.THEME_MONOCHROME -> "Monochrome B&W";
            case ThemeManager.THEME_CYBER_NEON -> "Cyberpunk Cyan";
            case ThemeManager.THEME_SAKURA_BLOOM -> "Sakura Bloom";
            case ThemeManager.THEME_AMBER_GOLD -> "Amber Gold";
            case ThemeManager.THEME_EVA_PURPLE -> "EVA Violet";
            default -> "Monochrome B&W";
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

        binding.themeGrid.setLayoutManager(new GridLayoutManager(this, 3));
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
                Toast.makeText(this, "Dynamic accent enabled - highlights follow album art", Toast.LENGTH_SHORT).show();
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
                    .setTitle("Audio Streaming Quality")
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

    private void setupClearCacheButton() {
        updateCacheSizeSubtitle();
        binding.clearCacheButton.setOnClickListener(v -> {
            new AlertDialog.Builder(this, R.style.AlertDialogTheme)
                    .setTitle("Clear Cache")
                    .setMessage("This will remove all temporarily stored audio streams and images. Your downloaded songs will remain safe.")
                    .setPositiveButton("Clear", (dialog, which) -> {
                        clearCache();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void updateCacheSizeSubtitle() {
        new Thread(() -> {
            long totalBytes = getDirSize(getCacheDir()) + getDirSize(getCodeCacheDir());
            String formatted = formatFileSize(totalBytes);
            runOnUiThread(() -> {
                if (binding != null) {
                    binding.cacheSubtitle.setText(formatted + " cached • Tap to clear");
                }
            });
        }).start();
    }

    private long getDirSize(File dir) {
        long size = 0;
        if (dir != null && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory()) {
                        size += getDirSize(f);
                    } else {
                        size += f.length();
                    }
                }
            }
        } else if (dir != null && dir.isFile()) {
            size += dir.length();
        }
        return size;
    }

    private String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void clearCache() {
        try {
            File cacheDir = getCacheDir();
            deleteDir(cacheDir);
            updateCacheSizeSubtitle();
            Toast.makeText(this, "Cache cleared successfully", Toast.LENGTH_SHORT).show();
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

    private void setupDiscordRpc() {
        binding.discordRpcButton.setOnClickListener(v -> {
            startActivity(new Intent(this, DiscordRpcActivity.class));
        });

        DiscordRpcManager rpcManager = DiscordRpcManager.getInstance(this);
        rpcManager.getUserProfile().observe(this, profile -> {
            if (binding == null) return;
            if (profile != null) {
                binding.discordRpcStatusSubtitle.setText("Connected as " + profile.getUsername());
            } else {
                binding.discordRpcStatusSubtitle.setText("Broadcast live listening activity to Discord");
            }
        });

        rpcManager.getConnectionStatus().observe(this, status -> {
            if (binding == null) return;
            if (status == null) status = "Disconnected";
            switch (status) {
                case "Ready":
                case "Connected":
                    binding.discordRpcBadge.setText("ONLINE");
                    binding.discordRpcBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF23A55A));
                    binding.discordRpcBadge.setTextColor(android.graphics.Color.WHITE);
                    break;
                case "Connecting":
                    binding.discordRpcBadge.setText("CONNECTING");
                    binding.discordRpcBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFEE75C));
                    binding.discordRpcBadge.setTextColor(0xFF000000);
                    break;
                case "Failed":
                case "Error":
                    binding.discordRpcBadge.setText("ERROR");
                    binding.discordRpcBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFED4245));
                    binding.discordRpcBadge.setTextColor(android.graphics.Color.WHITE);
                    break;
                case "Disconnected":
                default:
                    binding.discordRpcBadge.setText("OFFLINE");
                    binding.discordRpcBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF4E5058));
                    binding.discordRpcBadge.setTextColor(0xFFDBDEE1);
                    break;
            }
        });
    }

    private void setupAppInfoButton() {
        String version = com.midnight.music.BuildConfig.VERSION_NAME;
        binding.infoSubtitle.setText("Version " + version + " • Open Source");
        binding.infoVersionBadge.setText("v" + version);

        binding.appInfoButton.setOnClickListener(v -> {
            new AlertDialog.Builder(this, R.style.AlertDialogTheme)
                    .setTitle("Midnight Music")
                    .setMessage("Version: " + version + "\n\nA modern, high-fidelity music streaming client with dynamic acoustics & real-time synchronized lyrics.")
                    .setPositiveButton("OK", null)
                    .show();
        });
    }

    private void setupCheckUpdatesButton() {
        binding.checkUpdatesButton.setOnClickListener(v -> {
            new com.midnight.music.utils.UpdateManager(this).checkForUpdates(true);
        });
    }
}
