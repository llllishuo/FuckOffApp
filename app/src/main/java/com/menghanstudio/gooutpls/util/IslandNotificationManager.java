package com.menghanstudio.gooutpls.util;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.util.Log;

import com.menghanstudio.gooutpls.MainActivity;
import com.menghanstudio.gooutpls.R;
import com.menghanstudio.gooutpls.core.AppCatalog;
import com.menghanstudio.gooutpls.core.AppPrefs;
import com.menghanstudio.gooutpls.core.AppSafetyGuard;
import com.menghanstudio.gooutpls.core.BlacklistEntry;
import com.menghanstudio.gooutpls.core.BlacklistRepository;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * One consolidated "island"/live-update card for every installed blacklist
 * app. On Android 16 it asks the system for a promoted ongoing notification;
 * vendors that render those in their capsule can show it there, while other
 * devices fall back to a normal ongoing summary notification.
 *
 * <p>This deliberately uses only public Android notification APIs. Honor's
 * private Magic Capsule service is not used: third-party capsule rendering is
 * controlled by Honor/YOYO and may require business approval.</p>
 */
public final class IslandNotificationManager {

    public static final String CHANNEL_ISLAND = "island";
    public static final int ISLAND_NOTIFICATION_ID = 2001;
    private static final String TAG = "FuckOffIsland";

    private IslandNotificationManager() {
    }

    public static void refresh(Context context) {
        Context app = context.getApplicationContext();
        if (!AppPrefs.isIslandEnabled(app)) {
            cancel(app);
            return;
        }
        NotificationManager nm = app.getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }

        List<BlacklistEntry> detected = detectedInstalled(app);
        if (detected.isEmpty()) {
            nm.cancel(ISLAND_NOTIFICATION_ID);
            return;
        }

        StringBuilder shortText = new StringBuilder();
        StringBuilder longText = new StringBuilder();
        int shown = 0;
        for (BlacklistEntry entry : detected) {
            String label = entry.displayLabel();
            if (shown < 3) {
                if (shortText.length() > 0) {
                    shortText.append("、");
                }
                shortText.append(label);
                shown++;
            }
            if (longText.length() > 0) {
                longText.append('\n');
            }
            longText.append("• ").append(label);
        }
        int count = detected.size();
        if (count > 3) {
            shortText.append(app.getString(R.string.island_text_more, count));
        }

        String title = app.getString(R.string.island_title, count);
        String actionLabel = count == 1
                ? app.getString(R.string.island_action_single, detected.get(0).displayLabel())
                : app.getString(R.string.island_action_all);

        Intent open = new Intent(app, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(
                app, 2101, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent cleanup = new Intent(app, com.menghanstudio.gooutpls.BatchUninstallActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent cleanupIntent = PendingIntent.getActivity(
                app, 2102, cleanup,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = new Notification.Builder(app, CHANNEL_ISLAND)
                .setSmallIcon(R.drawable.ic_notification_24)
                .setContentTitle(title)
                .setContentText(shortText)
                .setStyle(new Notification.BigTextStyle().bigText(longText))
                .setContentIntent(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setAutoCancel(false)
                .setCategory(Notification.CATEGORY_STATUS)
                .setColor(app.getColor(R.color.accent))
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(app, R.drawable.ic_trash_20),
                        actionLabel, cleanupIntent).build())
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(app, R.drawable.ic_apps_24),
                        app.getString(R.string.island_action_open), content).build());

        boolean promoted = requestPromotedOngoing(app, builder, count);
        try {
            nm.notify(ISLAND_NOTIFICATION_ID, builder.build());
        } catch (SecurityException e) {
            Log.w(TAG, "Notification permission denied", e);
        } catch (Throwable t) {
            Log.w(TAG, "Unable to post island summary", t);
        }
        if (!promoted && Build.VERSION.SDK_INT >= 36) {
            // Normal summary remains available as a fallback.
            Log.i(TAG, "System did not accept a promoted ongoing notification");
        }
    }

    public static void cancel(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.cancel(ISLAND_NOTIFICATION_ID);
        }
    }

    /**
     * True when the OS advertises support for promoted ongoing notifications.
     * This is the standards-based surface used by Android 16 live updates.
     */
    public static boolean isPromotedSupported(Context context) {
        if (Build.VERSION.SDK_INT < 36) {
            return false;
        }
        try {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm == null) {
                return false;
            }
            Method method = NotificationManager.class.getMethod("canPostPromotedNotifications");
            Object result = method.invoke(nm);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean requestPromotedOngoing(Context context, Notification.Builder builder,
                                                  int count) {
        if (!isPromotedSupported(context)) {
            return false;
        }
        try {
            Method request = Notification.Builder.class.getMethod(
                    "setRequestPromotedOngoing", boolean.class);
            request.invoke(builder, true);
            try {
                Method shortText = Notification.Builder.class.getMethod(
                        "setShortCriticalText", String.class);
                shortText.invoke(builder, context.getString(R.string.island_title, count));
            } catch (Throwable ignored) {
                // Optional cosmetic field.
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static List<BlacklistEntry> detectedInstalled(Context context) {
        List<BlacklistEntry> result = new ArrayList<>();
        BlacklistRepository repository = new BlacklistRepository(context);
        AppSafetyGuard.Snapshot safety = AppSafetyGuard.snapshot(context);
        for (BlacklistEntry entry : repository.getAll()) {
            if (AppCatalog.isInstalled(context, entry.packageName)
                    && !safety.isProtected(context, entry.packageName)) {
                result.add(entry);
            }
        }
        return result;
    }
}
