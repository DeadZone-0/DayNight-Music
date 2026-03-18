package com.midnight.music.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.midnight.music.data.auth.SessionManager;
import com.midnight.music.data.network.SupabaseApiClient;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * WorkManager Worker that runs CloudSyncManager in the background.
 * Scheduled periodically (every 30 minutes) and also triggered on-demand after local changes.
 */
public class CloudSyncWorker extends Worker {
    private static final String TAG = "CloudSyncWorker";
    public static final String WORK_NAME_PERIODIC = "daynight_periodic_sync";
    public static final String WORK_NAME_ONE_TIME = "daynight_one_time_sync";

    public CloudSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        SessionManager session = SessionManager.getInstance(getApplicationContext());
        if (!session.isLoggedIn()) {
            Log.d(TAG, "Not logged in, skipping sync");
            return Result.success();
        }

        // Restore token into the API client
        String token = session.getAccessToken();
        if (token != null) {
            SupabaseApiClient.getInstance().setAccessToken(token);
        }

        // Run sync synchronously
        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] success = {false};

        CloudSyncManager.getInstance(getApplicationContext()).sync((isSuccess, message) -> {
            success[0] = isSuccess;
            if (!isSuccess) {
                Log.e(TAG, "Sync failed: " + message);
            }
            latch.countDown();
        });

        try {
            latch.await(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Sync interrupted", e);
            return Result.retry();
        }

        return success[0] ? Result.success() : Result.retry();
    }

    // ============ Static scheduling helpers ============

    /**
     * Schedule periodic sync every 30 minutes (only on network).
     */
    public static void schedulePeriodicSync(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                CloudSyncWorker.class, 30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInitialDelay(5, TimeUnit.MINUTES)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request);

        Log.d(TAG, "Periodic sync scheduled");
    }

    /**
     * Trigger a one-time sync immediately (e.g. after playlist creation/deletion).
     */
    public static void triggerImmediateSync(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(CloudSyncWorker.class)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueue(request);
        Log.d(TAG, "One-time sync triggered");
    }

    /**
     * Cancel all scheduled syncs (e.g. on logout).
     */
    public static void cancelAllSync(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC);
        Log.d(TAG, "All syncs cancelled");
    }
}
