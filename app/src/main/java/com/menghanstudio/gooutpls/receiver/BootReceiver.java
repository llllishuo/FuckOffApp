package com.menghanstudio.gooutpls.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.menghanstudio.gooutpls.core.AppPrefs;
import com.menghanstudio.gooutpls.service.MonitorService;
import com.menghanstudio.gooutpls.service.ScanJobService;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }
        if (!AppPrefs.isMonitoringEnabled(context)) {
            return;
        }
        ScanJobService.schedule(context);
        MonitorService.start(context);
    }
}
