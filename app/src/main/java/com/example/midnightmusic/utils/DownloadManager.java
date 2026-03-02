package com.example.midnightmusic.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.midnightmusic.data.db.AppDatabase;
import com.example.midnightmusic.data.model.Song;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Manages downloading songs to permanent storage (not cache).
 * Downloaded files persist until the user explicitly deletes them.
 */
public class DownloadManager {
    private static final String TAG = "DownloadManager";
    private static final String DOWNLOAD_DIR = "downloads";
    private static volatile DownloadManager instance;

    private final Context context;
    private final File downloadDir;
    private final Executor diskIO = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface DownloadListener {
        void onProgress(int percent);
        void onComplete(String filePath);
        void onError(Exception e);
    }

    private DownloadManager(Context context) {
        this.context = context.getApplicationContext();
        this.downloadDir = new File(context.getFilesDir(), DOWNLOAD_DIR);
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }
    }

    public static DownloadManager getInstance(Context context) {
        if (instance == null) {
            synchronized (DownloadManager.class) {
                if (instance == null) {
                    instance = new DownloadManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /**
     * Downloads a song's audio file to permanent storage and updates the database.
     */
    public void downloadSong(Song song, DownloadListener listener) {
        if (song == null || song.getMediaUrl() == null) {
            if (listener != null) {
                mainHandler.post(() -> listener.onError(
                        new IllegalArgumentException("Song or media URL is null")));
            }
            return;
        }

        // Check if already downloaded
        if (song.isDownloaded() && song.getLocalPath() != null) {
            File existing = new File(song.getLocalPath());
            if (existing.exists()) {
                if (listener != null) {
                    mainHandler.post(() -> listener.onComplete(song.getLocalPath()));
                }
                return;
            }
        }

        diskIO.execute(() -> {
            String fileName = sanitizeFileName(song.getId(), song.getSong());
            File outputFile = new File(downloadDir, fileName);
            File tempFile = new File(downloadDir, fileName + ".tmp");
            InputStream inputStream = null;
            FileOutputStream outputStream = null;

            try {
                URL audioUrl = new URL(song.getMediaUrl());
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) audioUrl.openConnection();
                connection.connect();

                int fileLength = connection.getContentLength();
                inputStream = new BufferedInputStream(connection.getInputStream());
                outputStream = new FileOutputStream(tempFile);

                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytesRead = 0;
                int lastPercent = 0;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;

                    if (fileLength > 0 && listener != null) {
                        int percent = (int) (totalBytesRead * 100 / fileLength);
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            final int p = percent;
                            mainHandler.post(() -> listener.onProgress(p));
                        }
                    }
                }
                outputStream.flush();

                // Rename temp to final
                if (tempFile.renameTo(outputFile)) {
                    String localPath = outputFile.getAbsolutePath();

                    // Update database
                    AppDatabase db = AppDatabase.getInstance(context);
                    song.setDownloaded(true);
                    song.setLocalPath(localPath);
                    song.setTimestamp(System.currentTimeMillis());
                    db.songDao().insert(song);

                    if (listener != null) {
                        mainHandler.post(() -> listener.onComplete(localPath));
                    }
                } else {
                    tempFile.delete();
                    if (listener != null) {
                        mainHandler.post(() -> listener.onError(
                                new IOException("Failed to save downloaded file")));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Download failed for: " + song.getSong(), e);
                tempFile.delete();
                if (listener != null) {
                    mainHandler.post(() -> listener.onError(e));
                }
            } finally {
                try {
                    if (inputStream != null) inputStream.close();
                    if (outputStream != null) outputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing streams", e);
                }
            }
        });
    }

    /**
     * Deletes a downloaded song file and updates the database.
     */
    public void deleteDownload(Song song, Runnable onComplete) {
        diskIO.execute(() -> {
            try {
                if (song.getLocalPath() != null) {
                    File file = new File(song.getLocalPath());
                    file.delete();
                }

                AppDatabase db = AppDatabase.getInstance(context);
                db.songDao().updateDownloadStatus(song.getId(), false, null);

                if (onComplete != null) {
                    mainHandler.post(onComplete);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error deleting download", e);
            }
        });
    }

    /**
     * Checks if a song file exists on disk.
     */
    public boolean isDownloadedOnDisk(Song song) {
        if (song == null || song.getLocalPath() == null) return false;
        return new File(song.getLocalPath()).exists();
    }

    private String sanitizeFileName(String id, String name) {
        // Use ID + sanitized name to avoid collisions
        String safe = (name != null ? name : "unknown")
                .replaceAll("[^a-zA-Z0-9._\\- ]", "")
                .trim()
                .replace(" ", "_");
        if (safe.length() > 50) safe = safe.substring(0, 50);
        return id + "_" + safe + ".m4a";
    }
}
