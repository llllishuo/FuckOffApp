package com.menghanstudio.gooutpls.receiver;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.menghanstudio.gooutpls.BatchUninstallActivity;
import com.menghanstudio.gooutpls.R;
import com.menghanstudio.gooutpls.core.AppPrefs;
import com.menghanstudio.gooutpls.core.EventRepository;
import com.menghanstudio.gooutpls.service.MonitorService;
import com.menghanstudio.gooutpls.service.ScanAlarmScheduler;
import com.menghanstudio.gooutpls.service.ScanJobService;
import com.menghanstudio.gooutpls.util.NotificationHelper;

public class NotificationActionReceiver extends BroadcastReceiver {

    public static final String ACTION_PAUSE =
            "com.menghanstudio.gooutpls.action.PAUSE_MONITORING";
    public static final String ACTION_CLEANUP_ALL =
            "com.menghanstudio.gooutpls.action.CLEANUP_ALL";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        if (ACTION_CLEANUP_ALL.equals(intent.getAction())) {
            try {
                Intent batch = new Intent(context, BatchUninstallActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(batch);
            } catch (Exception ignored) {
                // The summary notification is still tappable to open the app.
            }
            return;
        }
        if (!ACTION_PAUSE.equals(intent.getAction())) {
            return;
        }
        AppPrefs.setMonitoringEnabled(context, false);
        MonitorService.stop(context);
        ScanJobService.cancel(context);
        ScanAlarmScheduler.cancel(context);
        EventRepository.add(context, EventRepository.TYPE_MONITOR_OFF, null,
                context.getString(R.string.event_monitor_off));
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.cancel(NotificationHelper.MONITOR_NOTIFICATION_ID);
        }
    }
}
