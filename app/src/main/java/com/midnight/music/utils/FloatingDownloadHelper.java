package com.midnight.music.utils;

import android.os.Handler;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.midnight.music.R;
import com.midnight.music.ui.playlist.DownloadProgressBottomSheet;

/**
 * ponytail: Reusable helper for the floating download progress view.
 * Consolidates duplicate logic between MainActivity and PlaylistDetailActivity.
 */
public final class FloatingDownloadHelper {
    private FloatingDownloadHelper() {}

    public static void setup(AppCompatActivity activity, View floatingDownloadView, Handler handler) {
        if (floatingDownloadView == null) return;

        TextView floatingTitle = floatingDownloadView.findViewById(R.id.floatingTitle);
        CircularProgressIndicator floatingProgress = floatingDownloadView.findViewById(R.id.floatingProgress);
        ImageView floatingClose = floatingDownloadView.findViewById(R.id.floatingClose);

        floatingDownloadView.setOnClickListener(v -> {
            DownloadProgressBottomSheet sheet = DownloadProgressBottomSheet.newInstance();
            sheet.show(activity.getSupportFragmentManager(), "DownloadSheet");
        });

        if (floatingClose != null) {
            floatingClose.setOnClickListener(v -> floatingDownloadView.setVisibility(View.GONE));
        }

        final Runnable[] hideRunnable = new Runnable[1];

        DownloadObserver.getInstance().getDownloadState().observe(activity, state -> {
            if (handler != null && hideRunnable[0] != null) {
                handler.removeCallbacks(hideRunnable[0]);
            }
            if (state.isActive) {
                if (floatingDownloadView.getVisibility() != View.VISIBLE) {
                    floatingDownloadView.setAlpha(0f);
                    floatingDownloadView.setVisibility(View.VISIBLE);
                    floatingDownloadView.animate().alpha(1f).setDuration(300).start();
                }
                if (floatingTitle != null) {
                    floatingTitle.setText("Downloading...");
                }
                if (floatingProgress != null) {
                    floatingProgress.setIndeterminate(false);
                    floatingProgress.setProgressCompat(state.progress, true);
                }
            } else if (state.isError) {
                if (floatingTitle != null) {
                    floatingTitle.setText("Failed: " + state.title);
                }
                if (floatingProgress != null) {
                    floatingProgress.setIndeterminate(false);
                }
            } else if (state.progress == 100) {
                if (floatingTitle != null) {
                    floatingTitle.setText("Completed!");
                }
                if (floatingProgress != null) {
                    floatingProgress.setProgressCompat(100, true);
                }
                hideRunnable[0] = () -> {
                    if (!activity.isFinishing()) {
                        floatingDownloadView.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                            floatingDownloadView.setVisibility(View.GONE);
                        }).start();
                    }
                };
                if (handler != null) {
                    handler.postDelayed(hideRunnable[0], 2000);
                }
            }
        });
    }
}
