package com.menghanstudio.gooutpls.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;
import java.util.Locale;

/**
 * Optional uninstall-assist service.
 *
 * <p>It never uninstalls anything by itself. It only clicks the positive button
 * of a system package-installer/uninstaller window after FuckOff explicitly
 * queued that exact uninstall from ManualUninstallActivity. The mode is
 * deliberately opt-in because accessibility access is powerful.</p>
 */
public class AccessibilityCleanupService extends AccessibilityService {

    private static final Object LOCK = new Object();
    private static final long PENDING_TIMEOUT_MS = 30_000L;
    private static String pendingPackage;
    private static long pendingAt;
    private static int clicks;

    public static void enqueue(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        synchronized (LOCK) {
            pendingPackage = packageName;
            pendingAt = System.currentTimeMillis();
            clicks = 0;
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            pendingPackage = null;
            pendingAt = 0L;
            clicks = 0;
        }
    }

    public static boolean isEnabled(Context context) {
        try {
            AccessibilityManager manager = (AccessibilityManager)
                    context.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (manager == null) {
                return false;
            }
            List<AccessibilityServiceInfo> services =
                    manager.getEnabledAccessibilityServiceList(
                            AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            if (services == null) {
                return false;
            }
            for (AccessibilityServiceInfo info : services) {
                if (info == null || info.getResolveInfo() == null
                        || info.getResolveInfo().serviceInfo == null) {
                    continue;
                }
                String pkg = info.getResolveInfo().serviceInfo.packageName;
                String name = info.getResolveInfo().serviceInfo.name;
                if (context.getPackageName().equals(pkg)
                        && (name == null || name.contains("AccessibilityCleanupService"))) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static void openSettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        String target;
        synchronized (LOCK) {
            if (pendingPackage == null) {
                return;
            }
            if (System.currentTimeMillis() - pendingAt > PENDING_TIMEOUT_MS) {
                clear();
                return;
            }
            target = pendingPackage;
        }

        CharSequence packageName = event.getPackageName();
        if (packageName == null
                || !isInstallerPackage(packageName.toString())) {
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || !containsUninstallWord(root)) {
            return;
        }
        if (clickPositiveButton(root)) {
            synchronized (LOCK) {
                if (target.equals(pendingPackage)) {
                    clicks++;
                    if (clicks >= 3) {
                        clear();
                    }
                }
            }
        }
    }

    private boolean isInstallerPackage(String packageName) {
        String lower = packageName.toLowerCase(Locale.ROOT);
        return lower.contains("packageinstaller")
                || lower.contains("package.installer")
                || lower.contains("installer");
    }

    private boolean clickPositiveButton(AccessibilityNodeInfo root) {
        return clickNode(root);
    }

    /**
     * Guard against clicking an unrelated installer confirmation (for example
     * an install prompt) while a queued uninstall is still within its timeout.
     */
    private boolean containsUninstallWord(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        CharSequence text = node.getText();
        if (text != null) {
            String value = text.toString().toLowerCase(Locale.ROOT);
            if (value.contains("卸载") || value.contains("uninstall")
                    || value.contains("delete") || value.contains("remove")) {
                return true;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                try {
                    if (containsUninstallWord(child)) {
                        return true;
                    }
                } finally {
                    child.recycle();
                }
            }
        }
        return false;
    }

    private boolean clickNode(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        CharSequence text = node.getText();
        if (text != null) {
            String value = text.toString().trim();
            if (isPositiveLabel(value)) {
                AccessibilityNodeInfo clickable = node;
                for (int i = 0; i < 3 && clickable != null
                        && !clickable.isClickable(); i++) {
                    clickable = clickable.getParent();
                }
                if (clickable != null && clickable.isClickable()
                        && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true;
                }
            }
        }
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                try {
                    if (clickNode(child)) {
                        return true;
                    }
                } finally {
                    child.recycle();
                }
            }
        }
        return false;
    }

    private boolean isPositiveLabel(String value) {
        String text = value.toLowerCase(Locale.ROOT);
        return "卸载".equals(value)
                || "确定".equals(value)
                || "删除".equals(value)
                || "uninstall".equals(text)
                || "ok".equals(text)
                || "delete".equals(text)
                || "remove".equals(text);
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        clear();
        super.onDestroy();
    }
}
