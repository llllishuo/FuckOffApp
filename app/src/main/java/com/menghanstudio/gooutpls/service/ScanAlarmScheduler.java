package com.menghanstudio.gooutpls.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.menghanstudio.gooutpls.core.AppPrefs;

/**
 * Independent watchdog for OEMs (notably Honor) that aggressively kill
 * foreground services and defer JobScheduler. It asks AlarmManager for a
 * wake-up scan roughly every ten minutes; package-added broadcasts remain the
 * fast path when the process is alive.
 */
public final class ScanAlarmScheduler {

    public static final String ACTION_ALARM_SCAN =
            "com.menghanstudio.gooutpls.action.ALARM_SCAN";
    private static final int REQUEST_CODE = 7301;
    private static final long INTERVAL_MS = 10L * 60L * 1000L;
    private static final String TAG = "FuckOffAlarm";

    private ScanAlarmScheduler() {
    }

    public static void schedule(Context context) {
        if (!AppPrefs.isMonitoringEnabled(context)) {
            cancel(context);
            return;
        }
        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        if (alarmManager == null) {
            return;
        }
        try {
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + INTERVAL_MS,
                    pendingIntent(context));
        } catch (Throwable t) {
            Log.w(TAG, "Unable to schedule watchdog scan", t);
        }
    }

    public static void cancel(Context context) {
        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        if (alarmManager == null) {
            return;
        }
        try {
            alarmManager.cancel(pendingIntent(context));
        } catch (Throwable ignored) {
        }
    }

    private static PendingIntent pendingIntent(Context context) {
        Intent intent = new Intent(context, ScanAlarmReceiver.class)
                .setAction(ACTION_ALARM_SCAN);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags);
    }
}
