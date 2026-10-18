package com.menghanstudio.gooutpls.core;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;

import com.menghanstudio.gooutpls.R;
import com.menghanstudio.gooutpls.receiver.UninstallResultReceiver;

/**
 * The single place that decides what happens when a blacklisted package is
 * seen. Automatic removal uses PackageInstaller.uninstall(), which Android
 * explicitly permits for the device owner when REQUEST_DELETE_PACKAGES is
 * declared. Manual removal is handled by the system uninstall UI.
 */
public final class GuardEngine {

    public static final String EXTRA_PACKAGE = "extra_package";
    public static final String EXTRA_LABEL = "extra_label";

    private static final Object LOCK = new Object();
    private static final long DUPLICATE_GUARD_MS = 5_000L;

    private GuardEngine() {
    }

    public static void scanAll(Context context) {
        Context app = context.getApplicationContext();
        if (!AppPrefs.isMonitoringEnabled(app)) {
            return;
        }
        DeviceOwnerTools.syncOwnerState(app);
        reconcilePending(app);
        BlacklistRepository repository = new BlacklistRepository(app);
        AppSafetyGuard.Snapshot safety = AppSafetyGuard.snapshot(app);
        for (BlacklistEntry entry : repository.getAll()) {
            if (AppCatalog.isInstalled(app, entry.packageName)
                    && !safety.isProtected(app, entry.packageName)) {
                handleDetected(app, entry, false, safety);
            }
        }
    }

    /**
     * Reconciles operations whose system callback was lost (for example after
     * the process was killed while an uninstall was already in progress).
     */
    public static void reconcilePending(Context context) {
        Context app = context.getApplicationContext();
        synchronized (LOCK) {
            for (BlacklistEntry entry : new BlacklistRepository(app).getAll()) {
                if (AppCatalog.isInstalled(app, entry.packageName)) {
                    continue;
                }
                if (AppPrefs.hasPendingAuto(app, entry.packageName)) {
                    finalizeAutoSuccessLocked(app, entry.packageName, entry.displayLabel());
                }
                if (AppPrefs.hasPendingManual(app, entry.packageName)) {
                    finalizeManualSuccessLocked(app, entry.packageName, entry.displayLabel());
                }
            }
        }
    }

    public static void onPackageAdded(Context context, String packageName) {
        Context app = context.getApplicationContext();
        if (!AppPrefs.isMonitoringEnabled(app)) {
            return;
        }
        BlacklistEntry entry = new BlacklistRepository(app).find(packageName);
        if (entry == null) {
            return;
        }
        AppSafetyGuard.Snapshot safety = AppSafetyGuard.snapshot(app);
        if (AppCatalog.isInstalled(app, packageName)
                && !safety.isProtected(app, packageName)) {
            handleDetected(app, entry, true, safety);
        }
    }

    public static void onPackageRemoved(Context context, String packageName) {
        Context app = context.getApplicationContext();
        NotificationHelperCompat.cancel(app, packageName);
        String label = labelFor(app, packageName);
        synchronized (LOCK) {
            if (AppPrefs.hasPendingAuto(app, packageName)) {
                finalizeAutoSuccessLocked(app, packageName, label);
            }
            if (AppPrefs.hasPendingManual(app, packageName)) {
                finalizeManualSuccessLocked(app, packageName, label);
            }
        }
    }

    private static void handleDetected(Context context, BlacklistEntry entry, boolean force,
                                       AppSafetyGuard.Snapshot safety) {
        synchronized (LOCK) {
            if (!AppPrefs.isMonitoringEnabled(context)) {
                return;
            }
            if (!AppCatalog.isSafeTarget(context, entry.packageName)
                    || safety.isProtected(context, entry.packageName)
                    || !AppCatalog.isInstalled(context, entry.packageName)) {
                return;
            }

            boolean auto = entry.followsSystem()
                    && AppPrefs.isAutoUninstallEnabled(context)
                    && DeviceOwnerTools.isDeviceOwner(context);
            if (auto) {
                if (AppPrefs.hasPendingAuto(context, entry.packageName)) {
                    return;
                }
                if (AppPrefs.didAutoFailRecently(context, entry.packageName) && !force) {
                    return;
                }
                long lastAttempt = AppPrefs.getLastAutoAttempt(context, entry.packageName);
                if (lastAttempt > 0L
                        && System.currentTimeMillis() - lastAttempt < DUPLICATE_GUARD_MS) {
                    return;
                }
                if (force) {
                    AppPrefs.clearAutoFailure(context, entry.packageName);
                }
                startAutoUninstallLocked(context, entry.packageName, entry.displayLabel());
            } else {
                boolean shouldPrompt = force
                        || AppPrefs.shouldShowAlert(context, entry.packageName);
                if (!shouldPrompt) {
                    return;
                }
                EventRepository.add(context, EventRepository.TYPE_MANUAL_PROMPT,
                        entry.packageName,
                        context.getString(R.string.event_manual_prompt, entry.displayLabel()));
                NotificationHelperCompat.showDetection(
                        context, entry.packageName, entry.displayLabel(), true, false);
            }
        }
    }

    /**
     * Starts an automatic uninstall when device-owner mode is available.
     * Returns false when the request could not be submitted.
     */
    public static boolean requestAutoUninstall(Context context, String packageName, String label) {
        Context app = context.getApplicationContext();
        synchronized (LOCK) {
            if (!AppCatalog.isSafeTarget(app, packageName)
                    || AppSafetyGuard.isProtected(app, packageName)
                    || !AppCatalog.isInstalled(app, packageName)) {
                return false;
            }
            BlacklistEntry entry = new BlacklistRepository(app).find(packageName);
            if (entry != null && entry.isManual()) {
                return false;
            }
            if (!AppPrefs.isAutoUninstallEnabled(app)
                    || !DeviceOwnerTools.isDeviceOwner(app)) {
                return false;
            }
            if (AppPrefs.hasPendingAuto(app, packageName)) {
                return true;
            }
            return startAutoUninstallLocked(app, packageName, label);
        }
    }

    private static boolean startAutoUninstallLocked(Context context, String packageName,
                                                    String label) {
        if (!AppCatalog.isSafeTarget(context, packageName)
                || AppSafetyGuard.isProtected(context, packageName)) {
            return false;
        }
        AppPrefs.markPendingAuto(context, packageName);

        Intent statusIntent = new Intent(context, UninstallResultReceiver.class)
                .setAction(UninstallResultReceiver.ACTION_UNINSTALL_STATUS)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_LABEL, label);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingFlags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent status = PendingIntent.getBroadcast(
                context,
                ("uninstall:" + packageName).hashCode() & 0x7FFFFFFF,
                statusIntent,
                pendingFlags);

        try {
            PackageInstaller installer = context.getPackageManager().getPackageInstaller();
            installer.uninstall(packageName, status.getIntentSender());
            return true;
        } catch (SecurityException e) {
            AppPrefs.clearPendingAuto(context, packageName);
            AppPrefs.markAutoFailed(context, packageName);
            String safe = safeLabel(context, packageName, label);
            EventRepository.add(context, EventRepository.TYPE_AUTO_FAIL, packageName,
                    context.getString(R.string.event_auto_fail,
                            safe, context.getString(R.string.reason_security)));
            NotificationHelperCompat.showDetection(
                    context, packageName, safe, true, true);
            return false;
        } catch (Exception e) {
            AppPrefs.clearPendingAuto(context, packageName);
            AppPrefs.markAutoFailed(context, packageName);
            String safe = safeLabel(context, packageName, label);
            EventRepository.add(context, EventRepository.TYPE_AUTO_FAIL, packageName,
                    context.getString(R.string.event_auto_fail,
                            safe, context.getString(R.string.reason_unknown, -1)));
            NotificationHelperCompat.showDetection(
                    context, packageName, safe, true, true);
            return false;
        }
    }

    public static void onAutoUninstallResult(Context context, String packageName,
                                             String label, int status) {
        Context app = context.getApplicationContext();
        synchronized (LOCK) {
            boolean installed = AppCatalog.isInstalled(app, packageName);
            if (status == PackageInstaller.STATUS_SUCCESS || !installed) {
                if (AppPrefs.hasPendingAuto(app, packageName)) {
                    finalizeAutoSuccessLocked(app, packageName,
                            safeLabel(app, packageName, label));
                } else {
                    NotificationHelperCompat.cancel(app, packageName);
                }
                return;
            }

            if (!AppPrefs.hasPendingAuto(app, packageName)) {
                return;
            }
            AppPrefs.clearPendingAuto(app, packageName);
            AppPrefs.markAutoFailed(app, packageName);
            String reason = reasonForStatus(app, status);
            EventRepository.add(app, EventRepository.TYPE_AUTO_FAIL, packageName,
                    app.getString(R.string.event_auto_fail,
                            safeLabel(app, packageName, label), reason));
            NotificationHelperCompat.showDetection(
                    app, packageName, safeLabel(app, packageName, label), true, true);
        }
    }

    public static void finalizeManualResult(Context context, String packageName,
                                            String label, boolean removed) {
        Context app = context.getApplicationContext();
        synchronized (LOCK) {
            if (!AppPrefs.hasPendingManual(app, packageName)) {
                return;
            }
            AppPrefs.clearPendingManual(app, packageName);
            if (removed || !AppCatalog.isInstalled(app, packageName)) {
                finalizeManualSuccessLocked(app, packageName,
                        safeLabel(app, packageName, label));
            } else {
                EventRepository.add(app, EventRepository.TYPE_MANUAL_CANCEL, packageName,
                        app.getString(R.string.event_manual_cancel,
                                safeLabel(app, packageName, label)));
            }
        }
        NotificationHelperCompat.cancel(app, packageName);
    }

    private static void finalizeAutoSuccessLocked(Context context, String packageName,
                                                  String label) {
        if (!AppPrefs.hasPendingAuto(context, packageName)) {
            return;
        }
        AppPrefs.clearPendingAuto(context, packageName);
        AppPrefs.clearAutoFailure(context, packageName);
        AppPrefs.clearPendingManual(context, packageName);
        AppPrefs.incrementRemovedCount(context);
        EventRepository.add(context, EventRepository.TYPE_AUTO_SUCCESS, packageName,
                context.getString(R.string.event_auto_success, label));
        NotificationHelperCompat.cancel(context, packageName);
    }

    private static void finalizeManualSuccessLocked(Context context, String packageName,
                                                    String label) {
        AppPrefs.clearPendingManual(context, packageName);
        AppPrefs.incrementRemovedCount(context);
        EventRepository.add(context, EventRepository.TYPE_MANUAL_SUCCESS, packageName,
                context.getString(R.string.event_manual_success, label));
        NotificationHelperCompat.cancel(context, packageName);
    }

    private static String reasonForStatus(Context context, int status) {
        switch (status) {
            case PackageInstaller.STATUS_FAILURE_BLOCKED:
                return context.getString(R.string.reason_blocked);
            case PackageInstaller.STATUS_FAILURE_INVALID:
                return context.getString(R.string.reason_not_found);
            case PackageInstaller.STATUS_FAILURE_ABORTED:
                return context.getString(R.string.reason_aborted);
            case PackageInstaller.STATUS_FAILURE_STORAGE:
                return context.getString(R.string.reason_storage);
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                return context.getString(R.string.reason_incompatible);
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                return context.getString(R.string.reason_pending_user);
            case PackageInstaller.STATUS_FAILURE:
                return context.getString(R.string.reason_blocked);
            default:
                return context.getString(R.string.reason_unknown, status);
        }
    }

    private static String labelFor(Context context, String packageName) {
        BlacklistEntry entry = new BlacklistRepository(context).find(packageName);
        if (entry != null) {
            return entry.displayLabel();
        }
        return AppCatalog.labelFor(context, packageName);
    }

    private static String safeLabel(Context context, String packageName, String label) {
        if (label != null && !label.trim().isEmpty()) {
            return label;
        }
        return labelFor(context, packageName);
    }

    /**
     * Tiny indirection so GuardEngine stays testable without pulling Android
     * notification plumbing into its decision logic.
     */
    private static final class NotificationHelperCompat {
        static void showDetection(Context context, String pkg, String label,
                                  boolean force, boolean autoFailed) {
            com.menghanstudio.gooutpls.util.NotificationHelper.showDetection(
                    context, pkg, label, force, autoFailed);
        }

        static void cancel(Context context, String pkg) {
            com.menghanstudio.gooutpls.util.NotificationHelper.cancelAlert(context, pkg);
        }
    }
}
