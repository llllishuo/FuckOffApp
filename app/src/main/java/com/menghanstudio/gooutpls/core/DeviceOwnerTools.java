package com.menghanstudio.gooutpls.core;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;

import com.menghanstudio.gooutpls.R;
import com.menghanstudio.gooutpls.admin.GoOutDeviceAdminReceiver;

public final class DeviceOwnerTools {

    private DeviceOwnerTools() {
    }

    public static DevicePolicyManager dpm(Context context) {
        return (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    public static ComponentName adminComponent(Context context) {
        return new ComponentName(context.getPackageName(),
                GoOutDeviceAdminReceiver.class.getName());
    }

    public static boolean isDeviceOwner(Context context) {
        DevicePolicyManager manager = dpm(context);
        return manager != null && manager.isDeviceOwnerApp(context.getPackageName());
    }

    public static boolean isAdminActive(Context context) {
        DevicePolicyManager manager = dpm(context);
        return manager != null && manager.isAdminActive(adminComponent(context));
    }

    public static String provisioningCommand(Context context) {
        return "adb shell dpm set-device-owner "
                + adminComponent(context).flattenToString();
    }

    /**
     * Writes an event when the device-owner state changes. Safe to call often;
     * the persistent flag prevents duplicate log entries.
     */
    public static void syncOwnerState(Context context) {
        boolean ready = isDeviceOwner(context);
        boolean logged = AppPrefs.wasOwnerReadyLogged(context);
        if (ready && !logged) {
            AppPrefs.setOwnerReadyLogged(context, true);
            EventRepository.add(context, EventRepository.TYPE_OWNER_READY, null,
                    context.getString(R.string.event_owner_ready));
        } else if (!ready && logged) {
            AppPrefs.setOwnerReadyLogged(context, false);
            EventRepository.add(context, EventRepository.TYPE_OWNER_LOST, null,
                    context.getString(R.string.event_owner_lost));
        }
    }

    /**
     * Gives up device-owner status from inside the app. Returns true when the
     * system confirmed the change.
     */
    public static boolean clearDeviceOwner(Context context) {
        DevicePolicyManager manager = dpm(context);
        if (manager == null || !manager.isDeviceOwnerApp(context.getPackageName())) {
            return false;
        }
        try {
            manager.clearDeviceOwnerApp(context.getPackageName());
        } catch (Exception e) {
            return false;
        }
        try {
            if (manager.isAdminActive(adminComponent(context))) {
                manager.removeActiveAdmin(adminComponent(context));
            }
        } catch (Exception ignored) {
            // Device-owner removal is the important part; active admin removal
            // is best-effort.
        }
        AppPrefs.setOwnerReadyLogged(context, false);
        AppPrefs.setAutoUninstallEnabled(context, false);
        return !manager.isDeviceOwnerApp(context.getPackageName());
    }
}
