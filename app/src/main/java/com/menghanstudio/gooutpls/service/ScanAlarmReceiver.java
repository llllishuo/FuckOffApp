package com.menghanstudio.gooutpls.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.menghanstudio.gooutpls.core.AppPrefs;
import com.menghanstudio.gooutpls.core.GuardEngine;

/**
 * Alarm watchdog entry point. A short goAsync scan can still notify/uninstall
 * even when the foreground service was killed by an OEM power manager.
 */
public class ScanAlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ScanAlarmScheduler.ACTION_ALARM_SCAN.equals(intent.getAction())) {
            return;
        }
        if (!AppPrefs.isMonitoringEnabled(context)) {
            ScanAlarmScheduler.cancel(context);
            return;
        }
        PendingResult pending = goAsync();
        Context app = context.getApplicationContext();
        ScanAlarmScheduler.schedule(app);
        new Thread(() -> {
            try {
                GuardEngine.scanAll(app);
            } catch (Throwable ignored) {
                // The next watchdog tick or JobScheduler run will retry.
            } finally {
                pending.finish();
            }
        }, "fuckoff-alarm-scan").start();
    }
}
