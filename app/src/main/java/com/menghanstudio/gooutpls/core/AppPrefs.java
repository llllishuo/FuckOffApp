package com.menghanstudio.gooutpls.core;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * All locally persisted settings and short-lived operation markers.
 *
 * <p>No data ever leaves the device. The app has no INTERNET permission.</p>
 */
public final class AppPrefs {

    private static final String FILE = "gooutpls_prefs";
    private static final String KEY_MONITORING = "monitoring_enabled";
    private static final String KEY_AUTO = "auto_uninstall_enabled";
    private static final String KEY_CLEANUP_MODE = "cleanup_mode";
    private static final String KEY_DETECTION_NOTIFICATIONS = "detection_notifications_enabled";
    private static final String KEY_ISLAND = "island_enabled";
    private static final String KEY_OWNER_LOGGED = "owner_ready_logged";
    private static final String KEY_REMOVED_COUNT = "removed_count";

    private static final String PENDING_AUTO = "pending_auto_";
    private static final String PENDING_MANUAL = "pending_manual_";
    private static final String LAST_ALERT = "last_alert_";
    private static final String LAST_AUTO_ATTEMPT = "last_auto_attempt_";
    private static final String AUTO_FAILED_AT = "auto_failed_at_";

    private static final long AUTO_PENDING_TTL_MS = 30L * 60L * 1000L;
    private static final long MANUAL_PENDING_TTL_MS = 6L * 60L * 60L * 1000L;
    private static final long ALERT_COOLDOWN_MS = 6L * 60L * 60L * 1000L;
    private static final long AUTO_RETRY_COOLDOWN_MS = 6L * 60L * 60L * 1000L;

    private AppPrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean isMonitoringEnabled(Context context) {
        return prefs(context).getBoolean(KEY_MONITORING, false);
    }

    public static void setMonitoringEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_MONITORING, enabled).apply();
    }

    public static boolean isAutoUninstallEnabled(Context context) {
        return prefs(context).getBoolean(KEY_AUTO, false);
    }

    public static void setAutoUninstallEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_AUTO, enabled).apply();
    }

    public static boolean isDetectionNotificationsEnabled(Context context) {
        return prefs(context).getBoolean(KEY_DETECTION_NOTIFICATIONS, true);
    }

    public static void setDetectionNotificationsEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_DETECTION_NOTIFICATIONS, enabled).apply();
    }

    public static boolean isIslandEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ISLAND, false);
    }

    public static void setIslandEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ISLAND, enabled).apply();
    }

    public static String getCleanupMode(Context context) {
        return CleanupMode.normalize(
                prefs(context).getString(KEY_CLEANUP_MODE, CleanupMode.DEVICE_OWNER));
    }

    public static void setCleanupMode(Context context, String mode) {
        prefs(context).edit()
                .putString(KEY_CLEANUP_MODE, CleanupMode.normalize(mode))
                .apply();
    }

    public static boolean wasOwnerReadyLogged(Context context) {
        return prefs(context).getBoolean(KEY_OWNER_LOGGED, false);
    }

    public static void setOwnerReadyLogged(Context context, boolean logged) {
        prefs(context).edit().putBoolean(KEY_OWNER_LOGGED, logged).apply();
    }

    public static int getRemovedCount(Context context) {
        return prefs(context).getInt(KEY_REMOVED_COUNT, 0);
    }

    public static void incrementRemovedCount(Context context) {
        SharedPreferences p = prefs(context);
        p.edit().putInt(KEY_REMOVED_COUNT, p.getInt(KEY_REMOVED_COUNT, 0) + 1).apply();
    }

    public static void markPendingAuto(Context context, String packageName) {
        long now = System.currentTimeMillis();
        // Commit synchronously: the uninstall status callback can be delivered
        // before an asynchronous apply() reaches disk if the process is killed.
        prefs(context).edit()
                .putLong(PENDING_AUTO + packageName, now)
                .putLong(LAST_AUTO_ATTEMPT + packageName, now)
                .putLong(AUTO_FAILED_AT + packageName, 0L)
                .commit();
    }

    public static boolean hasPendingAuto(Context context, String packageName) {
        return hasFresh(context, PENDING_AUTO + packageName, AUTO_PENDING_TTL_MS);
    }

    public static void clearPendingAuto(Context context, String packageName) {
        prefs(context).edit().remove(PENDING_AUTO + packageName).apply();
    }

    public static void markPendingManual(Context context, String packageName) {
        prefs(context).edit()
                .putLong(PENDING_MANUAL + packageName, System.currentTimeMillis())
                .apply();
    }

    public static boolean hasPendingManual(Context context, String packageName) {
        return hasFresh(context, PENDING_MANUAL + packageName, MANUAL_PENDING_TTL_MS);
    }

    public static void clearPendingManual(Context context, String packageName) {
        prefs(context).edit().remove(PENDING_MANUAL + packageName).apply();
    }

    public static long getLastAutoAttempt(Context context, String packageName) {
        return prefs(context).getLong(LAST_AUTO_ATTEMPT + packageName, 0L);
    }

    public static void markAutoFailed(Context context, String packageName) {
        prefs(context).edit()
                .putLong(AUTO_FAILED_AT + packageName, System.currentTimeMillis())
                .apply();
    }

    public static boolean didAutoFailRecently(Context context, String packageName) {
        long at = prefs(context).getLong(AUTO_FAILED_AT + packageName, 0L);
        return at > 0L && System.currentTimeMillis() - at < AUTO_RETRY_COOLDOWN_MS;
    }

    public static void clearAutoFailure(Context context, String packageName) {
        prefs(context).edit().remove(AUTO_FAILED_AT + packageName).apply();
    }

    public static boolean shouldShowAlert(Context context, String packageName) {
        long at = prefs(context).getLong(LAST_ALERT + packageName, 0L);
        return at == 0L || System.currentTimeMillis() - at >= ALERT_COOLDOWN_MS;
    }

    public static void markAlertShown(Context context, String packageName) {
        prefs(context).edit()
                .putLong(LAST_ALERT + packageName, System.currentTimeMillis())
                .apply();
    }

    private static boolean hasFresh(Context context, String key, long ttlMs) {
        SharedPreferences p = prefs(context);
        long at = p.getLong(key, 0L);
        if (at <= 0L) {
            return false;
        }
        if (System.currentTimeMillis() - at > ttlMs) {
            p.edit().remove(key).apply();
            return false;
        }
        return true;
    }
}
