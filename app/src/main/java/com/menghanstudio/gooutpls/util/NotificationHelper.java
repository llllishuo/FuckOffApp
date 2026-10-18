package com.menghanstudio.gooutpls.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.util.Log;

import com.menghanstudio.gooutpls.MainActivity;
import com.menghanstudio.gooutpls.ManualUninstallActivity;
import com.menghanstudio.gooutpls.R;
import com.menghanstudio.gooutpls.core.AppPrefs;
import com.menghanstudio.gooutpls.core.BlacklistRepository;
import com.menghanstudio.gooutpls.receiver.NotificationActionReceiver;

public final class NotificationHelper {

    private static final String TAG = "FuckOffNotifications";

    public static final String CHANNEL_MONITOR = "monitor";
    public static final String CHANNEL_ALERT = "alerts";
    public static final int MONITOR_NOTIFICATION_ID = 1001;
    private static final int ALERT_ID_BASE = 10000;

    private NotificationHelper() {
    }

    public static void createChannels(Context context) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return;
            }
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm == null) {
                return;
            }

            NotificationChannel monitor = new NotificationChannel(
                    CHANNEL_MONITOR,
                    context.getString(R.string.notif_channel_monitor),
                    NotificationManager.IMPORTANCE_LOW);
            monitor.setDescription(context.getString(R.string.notif_channel_monitor_desc));
            monitor.setShowBadge(false);
            monitor.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            nm.createNotificationChannel(monitor);

            NotificationChannel alert = new NotificationChannel(
                    CHANNEL_ALERT,
                    context.getString(R.string.notif_channel_alert),
                    NotificationManager.IMPORTANCE_HIGH);
            alert.setDescription(context.getString(R.string.notif_channel_alert_desc));
            alert.enableVibration(true);
            alert.setVibrationPattern(new long[]{0L, 180L, 100L, 180L});
            alert.setShowBadge(true);
            alert.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            nm.createNotificationChannel(alert);
        } catch (Throwable t) {
            // A vendor NotificationManager must never take down app startup.
            Log.e(TAG, "Unable to create notification channels", t);
        }
    }

    public static Notification buildMonitorNotification(Context context) {
        int count = new BlacklistRepository(context).size();
        String text = count == 0
                ? context.getString(R.string.notif_monitor_text_zero)
                : context.getString(R.string.notif_monitor_text, count);

        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(
                context, 10, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent pause = new Intent(context, NotificationActionReceiver.class)
                .setAction(NotificationActionReceiver.ACTION_PAUSE);
        PendingIntent pauseIntent = PendingIntent.getBroadcast(
                context, 11, pause,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = new Notification.Builder(context, CHANNEL_MONITOR)
                .setSmallIcon(R.drawable.ic_notification_24)
                .setContentTitle(context.getString(R.string.notif_monitor_title))
                .setContentText(text)
                .setContentIntent(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setColor(context.getColor(R.color.accent))
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(context, R.drawable.ic_pause_18),
                        context.getString(R.string.notif_monitor_pause),
                        pauseIntent).build());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return builder.build();
    }

    public static void updateMonitorNotification(Context context) {
        if (!AppPrefs.isMonitoringEnabled(context)) {
            return;
        }
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        try {
            nm.notify(MONITOR_NOTIFICATION_ID, buildMonitorNotification(context));
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS may be denied; the foreground service still runs.
        }
    }

    public static void showDetection(Context context, String packageName, String label,
                                     boolean force, boolean autoFailed) {
        if (!force && !AppPrefs.shouldShowAlert(context, packageName)) {
            return;
        }
        AppPrefs.markAlertShown(context, packageName);

        String safeLabel = (label == null || label.trim().isEmpty()) ? packageName : label;
        String text = autoFailed
                ? context.getString(R.string.notif_alert_text_failed, safeLabel)
                : context.getString(R.string.notif_alert_text, safeLabel);

        Intent open = new Intent(context, ManualUninstallActivity.class)
                .putExtra(ManualUninstallActivity.EXTRA_PACKAGE, packageName)
                .putExtra(ManualUninstallActivity.EXTRA_LABEL, safeLabel)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(
                context, requestCode("open", packageName), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = new Notification.Builder(context, CHANNEL_ALERT)
                .setSmallIcon(R.drawable.ic_notification_24)
                .setContentTitle(context.getString(R.string.notif_alert_title))
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(content)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .setColor(context.getColor(R.color.accent))
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(context, R.drawable.ic_notification_24),
                        context.getString(R.string.notif_alert_action),
                        content).build());

        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        try {
            nm.notify(alertId(packageName), builder.build());
        } catch (SecurityException ignored) {
            // The event log still records the detection.
        }
    }

    public static void cancelAlert(Context context, String packageName) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.cancel(alertId(packageName));
        }
    }

    private static int alertId(String packageName) {
        return ALERT_ID_BASE + (packageName.hashCode() & 0xFFFFF);
    }

    private static int requestCode(String kind, String packageName) {
        return (kind + ":" + packageName).hashCode() & 0x7FFFFFFF;
    }
}
