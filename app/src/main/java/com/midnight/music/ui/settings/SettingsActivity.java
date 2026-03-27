package com.midnight.music.ui.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.midnight.music.R;
import com.midnight.music.databinding.FragmentSettingsBinding;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.File;

public class SettingsActivity extends AppCompatActivity {
    private FragmentSettingsBinding binding;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "DaynightMusicPrefs";
    private static final String DARK_MODE_KEY = "darkMode";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = FragmentSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        setupToolbar();
        setupThemeToggle();
        setupClearCacheButton();
        setupAppInfoButton();
        setupCheckUpdatesButton();
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupThemeToggle() {
        SwitchMaterial themeSwitch = binding.themeSwitch;

        // Load saved preference
        boolean isDarkMode = sharedPreferences.getBoolean(DARK_MODE_KEY, true);
        themeSwitch.setChecked(isDarkMode);

        themeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Save preference
            sharedPreferences.edit().putBoolean(DARK_MODE_KEY, isChecked).apply();

            // Apply theme
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });
    }

    private void setupClearCacheButton() {
        binding.clearCacheButton.setOnClickListener(v -> {
            new AlertDialog.Builder(this, R.style.AlertDialogTheme)
                    .setTitle("Clear Cache")
                    .setMessage(
                            "Are you sure you want to clear the app cache? This will remove all temporarily stored data.")
                    .setPositiveButton("Clear", (dialog, which) -> {
                        clearAppCache();
                    })
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
