package com.example.midnightmusic.ui.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.example.midnightmusic.R;
import com.example.midnightmusic.databinding.FragmentSettingsBinding;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.File;

public class SettingsFragment extends Fragment {
    private FragmentSettingsBinding binding;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "MidnightMusicPrefs";
    private static final String DARK_MODE_KEY = "darkMode";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        sharedPreferences = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        setupToolbar();
        setupThemeToggle();
        setupClearCacheButton();
        setupAppInfoButton();
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> {
            requireActivity().getSupportFragmentManager().popBackStack();
        });
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
            new AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                    .setTitle("Clear Cache")
                    .setMessage("Are you sure you want to clear the app cache? This will remove all temporarily stored data.")
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
            new AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                    .setTitle("App Info")
                    .setMessage("Midnight Music\nVersion: " + versionName + 
                            "\n\nA  music streaming app  with Spotify like ui." +
                            "\n\n made by DeadZone-68")
                    .setPositiveButton("OK", null)
                    .show();
        });
    }

    private String getAppVersion() {
        try {
            PackageInfo packageInfo = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0);
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "Unknown";
        }
    }

    private void clearAppCache() {
        try {
            File cacheDir = requireContext().getCacheDir();
            deleteDir(cacheDir);
            Toast.makeText(requireContext(), "Cache cleared successfully", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error clearing cache", Toast.LENGTH_SHORT).show();
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
} 