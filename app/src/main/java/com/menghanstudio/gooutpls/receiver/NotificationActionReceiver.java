package com.menghanstudio.gooutpls.receiver;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.menghanstudio.gooutpls.R;
import com.menghanstudio.gooutpls.core.AppPrefs;
import com.menghanstudio.gooutpls.core.EventRepository;
import com.menghanstudio.gooutpls.service.MonitorService;
import com.menghanstudio.gooutpls.service.ScanJobService;
import com.menghanstudio.gooutpls.util.NotificationHelper;

public class NotificationActionReceiver extends BroadcastReceiver {

    public static final String ACTION_PAUSE =
            "com.menghanstudio.gooutpls.action.PAUSE_MONITORING";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_PAUSE.equals(intent.getAction())) {
            return;
        }
        AppPrefs.setMonitoringEnabled(context, false);
        MonitorService.stop(context);
        ScanJobService.cancel(context);
        EventRepository.add(context, EventRepository.TYPE_MONITOR_OFF, null,
                context.getString(R.string.event_monitor_off));
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.cancel(NotificationHelper.MONITOR_NOTIFICATION_ID);
        }
    }
}
