package com.menghanstudio.gooutpls;

import android.app.Application;
import android.util.Log;

import com.menghanstudio.gooutpls.core.AppPrefs;
import com.menghanstudio.gooutpls.service.ScanJobService;
import com.menghanstudio.gooutpls.util.NotificationHelper;

public class GoOutApp extends Application {

    private static final String TAG = "FuckOffApp";

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.createChannels(this);
        if (AppPrefs.isMonitoringEnabled(this)) {
            // The foreground service cannot be started from the application
            // class while in the background. MainActivity and BootReceiver
            // handle it; the persisted job is safe to (re)schedule here.
            try {
                ScanJobService.schedule(this);
            } catch (Throwable t) {
                Log.e(TAG, "Unable to reschedule scan job", t);
            }
        }
    }
}
