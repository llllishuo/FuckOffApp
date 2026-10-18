package com.menghanstudio.gooutpls.core;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Package visibility and safety checks. Only normal user-installed apps are
 * ever offered as uninstall targets, and a small set of critical packages is
 * always excluded.
 */
public final class AppCatalog {

    private static final String SELF_PREFIX = "com.menghanstudio.gooutpls";

    private static final Set<String> CRITICAL_PACKAGES = new HashSet<>(Arrays.asList(
            "android",
            "com.android.settings",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.systemui",
            "com.android.shell",
            "com.android.providers.settings",
            "com.android.providers.media",
            "com.android.providers.downloads",
            "com.google.android.gms",
            "com.google.android.gsf"
    ));

    private AppCatalog() {
    }

    /**
     * Names that must never be accepted from a picker or an imported JSON
     * file: this app itself and packages whose removal would break the device.
     */
    public static boolean isProtectedPackageName(String packageName) {
        return packageName == null
                || packageName.isEmpty()
                || packageName.startsWith(SELF_PREFIX)
                || CRITICAL_PACKAGES.contains(packageName);
    }

    public static List<InstalledApp> loadUserApps(Context context) {
        PackageManager pm = context.getPackageManager();
        List<InstalledApp> result = new ArrayList<>();
        List<ApplicationInfo> installed;
        try {
            installed = pm.getInstalledApplications(0);
        } catch (Exception e) {
            return result;
        }

        AppSafetyGuard.Snapshot safety = AppSafetyGuard.snapshot(context);
        for (ApplicationInfo info : installed) {
            if (!isSafeTarget(context, info)
                    || safety.isProtected(context, info.packageName)) {
                continue;
            }
            String label;
            try {
                label = pm.getApplicationLabel(info).toString();
            } catch (Exception e) {
                label = info.packageName;
            }
            Drawable icon;
            try {
                icon = pm.getApplicationIcon(info);
            } catch (Exception e) {
                icon = null;
            }
            result.add(new InstalledApp(info.packageName, label, icon));
        }

        final Collator collator = Collator.getInstance(Locale.SIMPLIFIED_CHINESE);
        Collections.sort(result, (a, b) -> collator.compare(a.displayLabel(), b.displayLabel()));
        return result;
    }

    public static boolean isInstalled(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        try {
            context.getPackageManager().getApplicationInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isSafeTarget(Context context, String packageName) {
        if (isProtectedPackageName(packageName)) {
            return false;
        }
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(packageName, 0);
            return isSafeTarget(context, info);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isSafeTarget(Context context, ApplicationInfo info) {
        if (info == null || info.packageName == null) {
            return false;
        }
        if (info.packageName.equals(context.getPackageName())
                || isProtectedPackageName(info.packageName)) {
            return false;
        }
        // Never silently touch system apps, including updated system apps.
        if ((info.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) {
            return false;
        }
        return true;
    }

    public static String labelFor(Context context, String packageName) {
        if (packageName == null) {
            return "";
        }
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    public static Drawable iconFor(Context context, String packageName) {
        try {
            return context.getPackageManager().getApplicationIcon(packageName);
        } catch (Exception e) {
            return null;
        }
    }
}
