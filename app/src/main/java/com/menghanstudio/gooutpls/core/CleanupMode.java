package com.menghanstudio.gooutpls.core;

import android.content.Context;

/**
 * How matching blacklist entries are handled when automatic processing is on.
 */
public final class CleanupMode {

    public static final String MANUAL = "manual";
    public static final String DEVICE_OWNER = "device_owner";
    public static final String SHIZUKU = "shizuku";
    public static final String ACCESSIBILITY = "accessibility";

    private CleanupMode() {
    }

    public static String normalize(String value) {
        if (SHIZUKU.equals(value)) {
            return SHIZUKU;
        }
        if (ACCESSIBILITY.equals(value)) {
            return ACCESSIBILITY;
        }
        if (MANUAL.equals(value)) {
            return MANUAL;
        }
        return DEVICE_OWNER;
    }

    public static boolean isManual(String value) {
        return MANUAL.equals(normalize(value));
    }

    public static boolean isShizuku(String value) {
        return SHIZUKU.equals(normalize(value));
    }

    public static boolean isAccessibility(String value) {
        return ACCESSIBILITY.equals(normalize(value));
    }

    public static boolean isAvailable(Context context) {
        String mode = normalize(AppPrefs.getCleanupMode(context));
        if (SHIZUKU.equals(mode)) {
            return ShizukuManager.canUse();
        }
        if (DEVICE_OWNER.equals(mode)) {
            return DeviceOwnerTools.isDeviceOwner(context);
        }
        return false;
    }
}
