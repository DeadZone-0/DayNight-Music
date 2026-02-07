package com.example.midnightmusic.ui.playlist;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class PlaylistDetailViewModelFactory implements ViewModelProvider.Factory {
    private final Application application;
    private final long playlistId;

    public PlaylistDetailViewModelFactory(Application application, long playlistId) {
        this.application = application;
        this.playlistId = playlistId;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(PlaylistDetailViewModel.class)) {
            return (T) new PlaylistDetailViewModel(application, playlistId);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
} 