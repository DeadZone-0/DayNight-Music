package com.example.midnightmusic.utils;

import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Manages caching of audio files to disk.
 * Uses the application's cache directory to store downloaded audio files.
 */
public class AudioCacheManager {
    private static final String TAG = "AudioCacheManager";
    private static final String CACHE_DIR = "audio_cache";
    private static final long MAX_CACHE_SIZE = 100 * 1024 * 1024; // 100 MB
    private static final Executor diskIO = Executors.newSingleThreadExecutor();
    
    private final Context context;
    private final File cacheDir;
    
    private static volatile AudioCacheManager instance;
    
    /**
     * Gets the singleton instance of AudioCacheManager
     * @param context Application context
     * @return AudioCacheManager instance
     */
    public static AudioCacheManager getInstance(Context context) {
        if (instance == null) {
            synchronized (AudioCacheManager.class) {
                if (instance == null) {
                    instance = new AudioCacheManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }
    
    private AudioCacheManager(Context context) {
        this.context = context.getApplicationContext();
        this.cacheDir = new File(context.getCacheDir(), CACHE_DIR);
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        
        // Run initial cleanup to ensure we're under the size limit
        cleanupCache();
    }
    
    /**
     * Gets the local file path for a cached audio URL.
     * @param url The URL of the audio file
     * @return The local file path if cached, null otherwise
     */
    public String getCachedFilePath(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        
        String fileName = getFileNameForUrl(url);
        File cachedFile = new File(cacheDir, fileName);
        
        return cachedFile.exists() ? cachedFile.getAbsolutePath() : null;
    }
    
    /**
     * Checks if an audio URL is cached.
     * @param url The URL to check
     * @return true if cached, false otherwise
     */
    public boolean isUrlCached(String url) {
        return getCachedFilePath(url) != null;
    }
    
    /**
     * Downloads an audio file and caches it.
     * @param url The URL to download and cache
     * @param listener Listener for download completion
     */
    public void cacheAudioFile(String url, CacheListener listener) {
        if (url == null || url.isEmpty()) {
            if (listener != null) {
                listener.onCacheError(new IllegalArgumentException("URL cannot be null or empty"));
            }
            return;
        }
        
        // Check if already cached
        String cachedPath = getCachedFilePath(url);
        if (cachedPath != null) {
            if (listener != null) {
                listener.onCacheComplete(cachedPath);
            }
            return;
        }
        
        // Download and cache in background
        diskIO.execute(() -> {
            String fileName = getFileNameForUrl(url);
            File outputFile = new File(cacheDir, fileName);
            InputStream inputStream = null;
            FileOutputStream outputStream = null;
            
            try {
                // Create a temporary file first to avoid partially written files
                File tempFile = new File(cacheDir, fileName + ".temp");
                URL audioUrl = new URL(url);
                inputStream = new BufferedInputStream(audioUrl.openStream());
                outputStream = new FileOutputStream(tempFile);
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
                
                // Rename temp file to final file
                if (tempFile.renameTo(outputFile)) {
                    // Run cleanup in case we exceeded cache size
                    cleanupCache();
                    
                    if (listener != null) {
                        listener.onCacheComplete(outputFile.getAbsolutePath());
                    }
                } else {
                    if (listener != null) {
                        listener.onCacheError(new IOException("Failed to rename temp file"));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Error caching audio file", e);
                if (listener != null) {
                    listener.onCacheError(e);
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
     * Cleans up cache to keep it under the size limit.
     * Deletes oldest files first.
     */
    private void cleanupCache() {
        diskIO.execute(() -> {
            try {
                File[] files = cacheDir.listFiles();
                if (files == null || files.length == 0) {
                    return;
                }
                
                // Calculate current cache size
                long totalSize = 0;
                for (File file : files) {
                    totalSize += file.length();
                }
                
                // If under the limit, no cleanup needed
                if (totalSize < MAX_CACHE_SIZE) {
                    return;
                }
                
                // Sort files by last modified time (oldest first)
                Arrays.sort(files, Comparator.comparingLong(File::lastModified));
                
                // Delete oldest files until we're under the limit
                for (File file : files) {
                    if (totalSize < MAX_CACHE_SIZE) {
                        break;
                    }
                    
                    long fileSize = file.length();
                    if (file.delete()) {
                        totalSize -= fileSize;
                        Log.d(TAG, "Deleted cached file: " + file.getName());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error cleaning cache", e);
            }
        });
    }
    
    /**
     * Gets a cache file name from a URL by hashing it.
     * @param url The URL to hash
     * @return Hashed file name
     */
    private String getFileNameForUrl(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes());
            StringBuilder hexString = new StringBuilder();
            
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback to a simple hash if SHA-256 is not available
            return String.valueOf(url.hashCode());
        }
    }
    
    /**
     * Clears all cached audio files.
     */
    public void clearCache() {
        diskIO.execute(() -> {
            File[] files = cacheDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
        });
    }
    
    /**
     * Listener for cache operations.
     */
    public interface CacheListener {
        void onCacheComplete(String filePath);
        void onCacheError(Exception e);
    }
} 