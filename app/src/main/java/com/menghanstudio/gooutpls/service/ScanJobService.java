package com.menghanstudio.gooutpls.service;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.menghanstudio.gooutpls.core.AppPrefs;
import com.menghanstudio.gooutpls.core.GuardEngine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A persisted 15-minute safety net in addition to the package-added broadcast
 * and the foreground service. Android may kill background services; the job
 * survives restarts and catches anything the broadcast missed.
 */
public class ScanJobService extends JobService {

    private static final String TAG = "GoOutPlsJob";
    private static final int JOB_ID = 4201;
    private static final long INTERVAL_MS = 15L * 60L * 1000L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    public static void schedule(Context context) {
        if (!AppPrefs.isMonitoringEnabled(context)) {
            return;
        }
        try {
            JobScheduler scheduler = context.getSystemService(JobScheduler.class);
            if (scheduler == null) {
                return;
            }
            // Re-scheduling an existing job resets its period on some Android
            // versions and can make it run immediately on every activity
            // resume. Keep the existing periodic job when it is already armed.
            if (scheduler.getPendingJob(JOB_ID) != null) {
                return;
            }
            JobInfo job = new JobInfo.Builder(
                    JOB_ID,
                    new ComponentName(context, ScanJobService.class))
                    .setPeriodic(INTERVAL_MS)
                    .setPersisted(true)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                    .build();
            scheduler.schedule(job);
        } catch (Exception e) {
            Log.w(TAG, "Unable to schedule scan job", e);
        }
    }

    public static void cancel(Context context) {
        try {
            JobScheduler scheduler = context.getSystemService(JobScheduler.class);
            if (scheduler != null) {
                scheduler.cancel(JOB_ID);
            }
        } catch (Exception ignored) {
            // Nothing else to do.
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        if (!AppPrefs.isMonitoringEnabled(this)) {
            cancel(this);
            return false;
        }
        executor.execute(() -> {
            try {
                GuardEngine.scanAll(getApplicationContext());
            } catch (Exception e) {
                Log.e(TAG, "Scheduled scan failed", e);
            } finally {
                handler.post(() -> jobFinished(params, false));
            }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
