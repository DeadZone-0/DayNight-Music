package com.midnight.music.utils;

import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class DownloadObserver {
    private static volatile DownloadObserver instance;

    public static class DownloadState {
        public boolean isActive;
        public String title;
        public int progress;
        public boolean isError;

        public DownloadState(boolean isActive, String title, int progress, boolean isError) {
            this.isActive = isActive;
            this.title = title;
            this.progress = progress;
            this.isError = isError;
        }
    }

    private final MutableLiveData<DownloadState> downloadState = new MutableLiveData<>(new DownloadState(false, "", 0, false));

    private DownloadObserver() {}

    public static DownloadObserver getInstance() {
        if (instance == null) {
            synchronized (DownloadObserver.class) {
                if (instance == null) {
                    instance = new DownloadObserver();
                }
            }
        }
        return instance;
    }

    public LiveData<DownloadState> getDownloadState() {
        return downloadState;
    }

    public void updateProgress(String title, int progress) {
        if (progress < 0 || progress > 100) {
            Log.w("DownloadObserver", "Progress " + progress + " out of range, clamping to [0,100]");
        }
        int boundedProgress = Math.max(0, Math.min(100, progress));
        downloadState.postValue(new DownloadState(true, title, boundedProgress, false));
    }

    public void setComplete() {
        // Can optionally set isActive to false immediately, 
        // or let the UI handle a delayed fade-out
        downloadState.postValue(new DownloadState(false, "Complete", 100, false));
    }

    public void setError() {
        downloadState.postValue(new DownloadState(false, "Failed", 0, true));
    }
}
