package com.menghanstudio.gooutpls.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.menghanstudio.gooutpls.core.AppPrefs;
import com.menghanstudio.gooutpls.core.GuardEngine;
import com.menghanstudio.gooutpls.util.NotificationHelper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MonitorService extends Service {

    private static final String TAG = "GoOutPlsMonitor";
    private static final long PERIODIC_SCAN_MS = 15L * 60L * 1000L;
    private static final long PACKAGE_POLL_MS = 3_000L;

    public static final String ACTION_START =
            "com.menghanstudio.gooutpls.action.START_MONITOR";
    public static final String ACTION_REFRESH =
            "com.menghanstudio.gooutpls.action.REFRESH_MONITOR";

    private ExecutorService executor;
    private Handler handler;
    private final AtomicBoolean scanning = new AtomicBoolean(false);
    private boolean packageReceiverRegistered;
    private java.util.Set<String> knownPackages;

    /**
     * Dynamic receivers are exempt from Android 8's implicit-broadcast
     * restrictions, unlike the manifest receiver. The foreground service is
     * therefore the primary source of immediate package-change events.
     */
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }
            Uri data = intent.getData();
            String pkg = data == null ? null : data.getSchemeSpecificPart();
            if (pkg == null || pkg.isEmpty()) {
                return;
            }
            if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())) {
                if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    GuardEngine.onPackageAdded(getApplicationContext(), pkg);
                }
            } else if (Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(intent.getAction())) {
                GuardEngine.onPackageRemoved(getApplicationContext(), pkg);
            }
        }
    };

    private final Runnable periodicScan = new Runnable() {
        @Override
        public void run() {
            runScan();
            if (handler != null) {
                handler.postDelayed(this, PERIODIC_SCAN_MS);
            }
        }
    };

    /**
     * Honor/MagicOS and Android background limits can drop PACKAGE_ADDED
     * manifest broadcasts while the app is not in the foreground. Polling
     * PackageManager's change sequence from the existing FGS is cheap enough
     * and gives a 3-second detection path even when the broadcast never lands.
     */
    private final Runnable packagePoll = new Runnable() {
        @Override
        public void run() {
            if (!AppPrefs.isMonitoringEnabled(MonitorService.this)) {
                return;
            }
            executor.execute(() -> {
                try {
                    pollPackageChanges();
                } catch (Throwable t) {
                    Log.w(TAG, "Package poll failed", t);
                } finally {
                    if (handler != null) {
                        handler.postDelayed(this, PACKAGE_POLL_MS);
                    }
                }
            });
        }
    };

    public static void start(Context context) {
        if (!AppPrefs.isMonitoringEnabled(context)) {
            return;
        }
        sendStart(context, ACTION_START);
    }

    public static void refresh(Context context) {
        if (!AppPrefs.isMonitoringEnabled(context)) {
            return;
        }
        sendStart(context, ACTION_REFRESH);
    }

    public static void stop(Context context) {
        ScanAlarmScheduler.cancel(context);
        try {
            context.stopService(new Intent(context, MonitorService.class));
        } catch (Exception ignored) {
            // Nothing else to do.
        }
    }

    private static void sendStart(Context context, String action) {
        Intent intent = new Intent(context, MonitorService.class).setAction(action);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            // Background start restrictions can reject this. JobScheduler and
            // the manifest package receiver remain as fallbacks.
            Log.w(TAG, "Unable to start monitor service", e);
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.createChannels(this);
        executor = Executors.newSingleThreadExecutor();
        handler = new Handler(Looper.getMainLooper());

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        filter.addDataScheme("package");
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(packageReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(packageReceiver, filter);
            }
            packageReceiverRegistered = true;
        } catch (Exception e) {
            Log.w(TAG, "Unable to register package receiver", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!AppPrefs.isMonitoringEnabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        Notification notification = NotificationHelper.buildMonitorNotification(this);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                        NotificationHelper.MONITOR_NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NotificationHelper.MONITOR_NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to enter foreground", e);
            stopSelf();
            return START_NOT_STICKY;
        }

        ScanAlarmScheduler.schedule(this);
        if (handler != null) {
            handler.removeCallbacks(packagePoll);
            handler.postDelayed(packagePoll, PACKAGE_POLL_MS);
        }
        if (intent != null && ACTION_REFRESH.equals(intent.getAction())) {
            NotificationHelper.updateMonitorNotification(this);
            if (handler != null) {
                handler.removeCallbacks(periodicScan);
                handler.postDelayed(periodicScan, PERIODIC_SCAN_MS);
            }
            return START_STICKY;
        }

        runScan();
        if (handler != null) {
            handler.removeCallbacks(periodicScan);
            handler.postDelayed(periodicScan, PERIODIC_SCAN_MS);
        }
        return START_STICKY;
    }

    private void pollPackageChanges() {
        PackageManager pm = getPackageManager();
        java.util.Set<String> current = new java.util.HashSet<>();
        for (PackageInfo info : pm.getInstalledPackages(0)) {
            if (info != null && info.packageName != null) {
                current.add(info.packageName);
            }
        }
        if (knownPackages == null) {
            knownPackages = current;
            Log.i(TAG, "Package poll initialized: " + current.size() + " packages");
            return;
        }
        for (String packageName : current) {
            if (!knownPackages.contains(packageName)) {
                Log.i(TAG, "Package poll detected add: " + packageName);
                GuardEngine.onPackageAdded(getApplicationContext(), packageName);
            }
        }
        for (String packageName : knownPackages) {
            if (!current.contains(packageName)) {
                Log.i(TAG, "Package poll detected remove: " + packageName);
                GuardEngine.onPackageRemoved(getApplicationContext(), packageName);
            }
        }
        knownPackages = current;
    }

    private void runScan() {
        if (!AppPrefs.isMonitoringEnabled(this)) {
            stopSelf();
            return;
        }
        if (!scanning.compareAndSet(false, true)) {
            return;
        }
        executor.execute(() -> {
            try {
                GuardEngine.scanAll(getApplicationContext());
            } catch (Exception e) {
                Log.e(TAG, "Background scan failed", e);
            } finally {
                scanning.set(false);
                if (handler != null) {
                    handler.post(() -> NotificationHelper.updateMonitorNotification(
                            getApplicationContext()));
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        if (packageReceiverRegistered) {
            try {
                unregisterReceiver(packageReceiver);
            } catch (Exception ignored) {
                // Already unregistered.
            }
            packageReceiverRegistered = false;
        }
        if (handler != null) {
            handler.removeCallbacks(packagePoll);
            handler.removeCallbacksAndMessages(null);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
