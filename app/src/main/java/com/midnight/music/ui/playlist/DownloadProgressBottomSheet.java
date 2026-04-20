package com.midnight.music.ui.playlist;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.midnight.music.databinding.FragmentDownloadProgressBinding;
import com.midnight.music.utils.DownloadObserver;

public class DownloadProgressBottomSheet extends BottomSheetDialogFragment {

    private FragmentDownloadProgressBinding binding;
    private boolean isBatch = false;
    private Handler mainHandler;
    private Runnable dismissRunnable;

    public static DownloadProgressBottomSheet newInstance() {
        return new DownloadProgressBottomSheet();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDownloadProgressBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // It should be dismissible so it acts as a floating background process
        setCancelable(true);

        DownloadObserver.getInstance().getDownloadState().observe(getViewLifecycleOwner(), state -> {
            if (binding == null) return;
            
            binding.textDownloadSong.setText(state.title);
            
            if (state.isActive) {
                binding.textDownloadTitle.setText("Downloading...");
                binding.progressBarDownload.setIndeterminate(false);
                binding.progressBarDownload.setProgressCompat(state.progress, true);
                binding.textProgressPercent.setText(state.progress + "%");
            } else if (state.isError) {
                binding.textDownloadTitle.setText("Download Failed");
                binding.progressBarDownload.setIndeterminate(false);
                binding.textProgressPercent.setText("Error");
            } else {
                // Completed
                binding.textDownloadTitle.setText("Download Complete");
                binding.progressBarDownload.setIndeterminate(false);
                binding.progressBarDownload.setProgressCompat(100, true);
                binding.textProgressPercent.setText("100%");
                
                // Auto dismiss after a second
                if (mainHandler != null) {
                    mainHandler.removeCallbacks(dismissRunnable);
                }
                dismissRunnable = () -> {
                    if (isAdded() && !isStateSaved()) {
                        dismissAllowingStateLoss();
                    }
                };
                if (mainHandler == null) {
                    mainHandler = new Handler(Looper.getMainLooper());
                }
                mainHandler.postDelayed(dismissRunnable, 1000);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mainHandler != null) {
            mainHandler.removeCallbacks(dismissRunnable);
        }
        mainHandler = null;
        dismissRunnable = null;
        binding = null;
    }
}
