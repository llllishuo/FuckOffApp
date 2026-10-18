package com.menghanstudio.gooutpls;

import android.app.Application;

import com.menghanstudio.gooutpls.core.AppPrefs;
import com.menghanstudio.gooutpls.service.ScanJobService;
import com.menghanstudio.gooutpls.util.NotificationHelper;

public class GoOutApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.createChannels(this);
        if (AppPrefs.isMonitoringEnabled(this)) {
            // The foreground service cannot be started from the application
            // class while in the background. MainActivity and BootReceiver
            // handle it; the persisted job is safe to (re)schedule here.
            ScanJobService.schedule(this);
        }
    }
}
