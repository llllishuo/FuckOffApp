package com.menghanstudio.gooutpls.core;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.provider.Telephony;
import android.telecom.TelecomManager;
import android.view.accessibility.AccessibilityManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Last line of defence before a package can be offered or sent to the
 * uninstaller. Some apps are user-installed but still indispensable: the
 * current home app, input method, dialer/SMS handler, device administrator or
 * an enabled accessibility service. Removing one of these can leave the phone
 * without a usable home screen, keyboard, calling ability or accessibility.
 *
 * <p>A snapshot collects the current role holders once so a scan over 200
 * blacklist entries does not re-query the system for every item.</p>
 */
public final class AppSafetyGuard {

    private AppSafetyGuard() {
    }

    public static Snapshot snapshot(Context context) {
        return new Snapshot(context.getApplicationContext());
    }

    public static boolean isProtected(Context context, String packageName) {
        return snapshot(context).isProtected(context, packageName);
    }

    /**
     * Optional, user-defined protection. Kept independent of AppSafetyGuard's
     * field types so a broken/unsupported optional rules runtime can never
     * prevent the baseline checks or the home screen from loading.
     */
    public interface CustomProtection {
        boolean isProtected(String packageName);
    }

    public static final class Snapshot {

        private final Set<String> rolePackages = new HashSet<>();
        private final CustomProtection customRules;

        private Snapshot(Context context) {
            collectDefaultHome(context);
            collectDefaultInputMethod(context);
            collectDefaultDialer(context);
            collectDefaultSms(context);
            collectActiveAdmins(context);
            collectAccessibilityServices(context);
            customRules = CustomRuleSupport.create(context);
        }

        public boolean isProtected(Context context, String packageName) {
            return isProtected(context, packageName, null);
        }

        /**
         * Same checks as {@link #isProtected(Context, String)}, but callers
         * that already hold an ApplicationInfo can avoid a second
         * PackageManager binder round-trip. This matters when scanning the
         * installed-app list.
         */
        public boolean isProtected(Context context, String packageName,
                                   ApplicationInfo knownInfo) {
            // The baseline checks run first and can never be overridden by a
            // custom rule. Custom rules are only allowed to add protection.
            if (AppCatalog.isProtectedPackageName(packageName)) {
                return true;
            }
            if (rolePackages.contains(packageName)) {
                return true;
            }
            ApplicationInfo info = knownInfo;
            if (info == null) {
                try {
                    info = context.getPackageManager()
                            .getApplicationInfo(packageName, 0);
                } catch (Exception ignored) {
                    // Not installed or not visible; custom rules may still
                    // apply to package names imported for future use.
                }
            }
            if (info != null && (info.flags & (ApplicationInfo.FLAG_SYSTEM
                    | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) {
                return true;
            }
            return customRules != null && customRules.isProtected(packageName);
        }

        private void collectDefaultHome(Context context) {
            try {
                Intent intent = new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME);
                ResolveInfo info = context.getPackageManager().resolveActivity(
                        intent, PackageManager.MATCH_DEFAULT_ONLY);
                if (info != null && info.activityInfo != null) {
                    add(info.activityInfo.packageName);
                }
            } catch (Exception ignored) {
                // A role lookup must never block the rest of the guard.
            }
        }

        private void collectDefaultInputMethod(Context context) {
            try {
                String value = Settings.Secure.getString(
                        context.getContentResolver(),
                        Settings.Secure.DEFAULT_INPUT_METHOD);
                if (value != null) {
                    int slash = value.indexOf('/');
                    add(slash > 0 ? value.substring(0, slash) : value);
                }
            } catch (Exception ignored) {
            }
        }

        private void collectDefaultDialer(Context context) {
            try {
                TelecomManager telecom = context.getSystemService(TelecomManager.class);
                if (telecom != null) {
                    add(telecom.getDefaultDialerPackage());
                }
            } catch (Exception ignored) {
            }
        }

        private void collectDefaultSms(Context context) {
            try {
                add(Telephony.Sms.getDefaultSmsPackage(context));
            } catch (Exception ignored) {
            }
        }

        private void collectActiveAdmins(Context context) {
            try {
                DevicePolicyManager dpm = (DevicePolicyManager)
                        context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                if (dpm == null) {
                    return;
                }
                List<ComponentName> admins = dpm.getActiveAdmins();
                if (admins != null) {
                    for (ComponentName admin : admins) {
                        if (admin != null) {
                            add(admin.getPackageName());
                        }
                    }
                }
                // Profile owners are active admins, so the list above covers
                // them as well. getProfileOwner() is not part of the public SDK.
            } catch (Exception ignored) {
            }
        }

        private void collectAccessibilityServices(Context context) {
            try {
                AccessibilityManager manager = (AccessibilityManager)
                        context.getSystemService(Context.ACCESSIBILITY_SERVICE);
                if (manager == null) {
                    return;
                }
                List<AccessibilityServiceInfo> services =
                        manager.getEnabledAccessibilityServiceList(
                                AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
                if (services != null) {
                    for (AccessibilityServiceInfo service : services) {
                        if (service != null && service.getResolveInfo() != null
                                && service.getResolveInfo().serviceInfo != null) {
                            add(service.getResolveInfo().serviceInfo.packageName);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        private void add(String packageName) {
            if (packageName != null && !packageName.isEmpty()
                    && !"android".equals(packageName)) {
                rolePackages.add(packageName);
            }
        }
    }
}
