package com.midnight.music.utils;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.midnight.music.BuildConfig;
import com.midnight.music.R;
import com.midnight.music.data.api.GitHubApiService;
import com.midnight.music.data.model.github.GitHubAsset;
import com.midnight.music.data.model.github.GitHubRelease;

import java.io.File;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class UpdateManager {
    private static final String TAG = "UpdateManager";
    private static final String REPO_OWNER = "DeadZone-0";
    private static final String REPO_NAME = "DayNight-Music";
    private static final String PREF_NAME = "UpdatePrefs";
    private static final String KEY_SKIPPED_VERSION = "skipped_version";

    private final Activity activity;
    private final GitHubApiService apiService;
    private long downloadId = -1;

    public UpdateManager(Activity activity) {
        this.activity = activity;
        
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.github.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
                
        apiService = retrofit.create(GitHubApiService.class);
    }

    public void checkForUpdates(boolean manualCheck) {
        if (manualCheck) {
            Toast.makeText(activity, "Checking for updates...", Toast.LENGTH_SHORT).show();
        }

        apiService.getLatestRelease(REPO_OWNER, REPO_NAME).enqueue(new Callback<GitHubRelease>() {
            @Override
            public void onResponse(Call<GitHubRelease> call, Response<GitHubRelease> response) {
                if (response.isSuccessful() && response.body() != null) {
                    handleReleaseInfo(response.body(), manualCheck);
                } else if (manualCheck) {
                    Toast.makeText(activity, "Failed to check for updates.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<GitHubRelease> call, Throwable t) {
                Log.e(TAG, "API call failed", t);
                if (manualCheck) {
                    Toast.makeText(activity, "Network error checking updates.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void handleReleaseInfo(GitHubRelease release, boolean manualCheck) {
        String latestVersion = release.getTagName();
        if (latestVersion == null) return;
        
        // Strip 'v' prefixes if they exist (e.g., 'v1.0' -> '1.0')
        String cleanLatest = latestVersion.replace("v", "").replace("V", "").trim();
        String currentVersion = BuildConfig.VERSION_NAME.replace("v", "").replace("V", "").trim();

        if (isNewerVersion(currentVersion, cleanLatest)) {
            // New version available!
            boolean isMandatory = release.isMandatory();
            
            // If it's not a manual check, not mandatory, and the user already skipped this version, ignore it.
            if (!manualCheck && !isMandatory && hasSkippedVersion(latestVersion)) {
                return;
            }

            if (release.getAssets() != null && !release.getAssets().isEmpty()) {
                // Find the APK asset
                GitHubAsset apkAsset = null;
                for (GitHubAsset asset : release.getAssets()) {
                    if (asset.getName() != null && asset.getName().endsWith(".apk")) {
                        apkAsset = asset;
                        break;
                    }
                }

                if (apkAsset != null) {
                    showUpdateDialog(release, apkAsset.getBrowserDownloadUrl(), isMandatory);
                } else if (manualCheck) {
                    Toast.makeText(activity, "New update found, but no APK is attached to the release.", Toast.LENGTH_LONG).show();
                }
            } else if (manualCheck) {
                Toast.makeText(activity, "New update found, but no files are attached.", Toast.LENGTH_SHORT).show();
            }

        } else if (manualCheck) {
            Toast.makeText(activity, "You are on the latest version!", Toast.LENGTH_SHORT).show();
        }
    }

    private void showUpdateDialog(GitHubRelease release, String downloadUrl, boolean isMandatory) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.PlaylistDialogStyle)
                .setTitle("New Update Available: " + release.getTagName())
                .setMessage("A new version of DayNight Music is ready to install!\n\n" + (release.getBody() != null ? release.getBody() : ""));

        // Download button
        builder.setPositiveButton("Update Now", (dialog, which) -> {
            downloadAndInstallApk(downloadUrl, release.getTagName());
        });

        if (isMandatory) {
            builder.setCancelable(false);
            // No negative button for mandatory updates
        } else {
            builder.setCancelable(true);
            builder.setNegativeButton("Skip for now", (dialog, which) -> {
                saveSkippedVersion(release.getTagName());
            });
        }

        activity.runOnUiThread(() -> builder.show());
    }

    private void downloadAndInstallApk(String url, String versionName) {
        Toast.makeText(activity, "Downloading update...", Toast.LENGTH_SHORT).show();

        // 1. Create DownloadManager Request
        String fileName = "DayNightMusic-" + versionName + ".apk";
        
        // Remove old file to prevent renaming (e.g., DayNightMusic-1.0-1.apk)
        File downloadDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (downloadDir != null) {
            File existingFile = new File(downloadDir, fileName);
            if (existingFile.exists()) {
                existingFile.delete();
            }
        }

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle("DayNight Music Update");
        request.setDescription("Downloading version " + versionName);
        request.setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, fileName);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        // 2. Enqueue the download
        DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager != null) {
            downloadId = manager.enqueue(request);

            // 3. Register Receiver to listen for completion
            BroadcastReceiver onComplete = new BroadcastReceiver() {
                public void onReceive(Context ctxt, Intent intent) {
                    long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (id == downloadId) {
                        try {
                            activity.unregisterReceiver(this);
                        } catch (Exception e) {
                            Log.e(TAG, "Receiver already unregistered", e);
                        }
                        
                        // Proceed to install
                        installApk(id, fileName);
                    }
                }
            };
            
            // Use correct flags for newer Android versions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED);
            } else {
                activity.registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            }
        }
    }

    private void installApk(long downloadId, String fileName) {
        File downloadDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (downloadDir == null) return;
        
        File file = new File(downloadDir, fileName);
        if (!file.exists()) {
            activity.runOnUiThread(() -> Toast.makeText(activity, "Error locating downloaded update.", Toast.LENGTH_LONG).show());
            return;
        }

        Uri downloadedUri = FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID + ".fileprovider", file);

        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setDataAndType(downloadedUri, "application/vnd.android.package-archive");
        install.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        
        try {
            activity.startActivity(install);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start install intent", e);
            activity.runOnUiThread(() -> Toast.makeText(activity, "Failed to launch installer. Please open your Downloads folder and tap the APK manually.", Toast.LENGTH_LONG).show());
        }
    }

    // Helper: Compare "1.0.1" vs "1.1" etc.
    private boolean isNewerVersion(String current, String latest) {
        String[] currentParts = current.split("\\.");
        String[] latestParts = latest.split("\\.");
        int length = Math.max(currentParts.length, latestParts.length);

        for (int i = 0; i < length; i++) {
            int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
            int latestPart = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;
            if (latestPart > currentPart) {
                return true;
            } else if (latestPart < currentPart) {
                return false;
            }
        }
        return false;
    }

    private boolean hasSkippedVersion(String version) {
        SharedPreferences prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String skipped = prefs.getString(KEY_SKIPPED_VERSION, "");
        return skipped.equals(version);
    }

    private void saveSkippedVersion(String version) {
        SharedPreferences prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_SKIPPED_VERSION, version).apply();
    }
}
