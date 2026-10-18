package com.menghanstudio.gooutpls.core;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuService;
import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;

/**
 * Small wrapper around the optional Shizuku shell provider.
 *
 * <p>FuckOff never uses Shizuku unless the user selects the Shizuku cleanup
 * mode. If Shizuku is not installed, not running, or permission was not
 * granted, the caller falls back to the normal notification/manual flow.</p>
 */
public final class ShizukuManager {

    public static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";
    private static final String TAG = "FuckOffShizuku";

    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "fuckoff-shizuku");
                thread.setDaemon(true);
                return thread;
            });

    private ShizukuManager() {
    }

    public static boolean isBinderAvailable() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean hasPermission() {
        try {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean canUse() {
        return isBinderAvailable() && hasPermission();
    }

    public static void requestPermission(int requestCode) {
        try {
            Shizuku.requestPermission(requestCode);
        } catch (Throwable t) {
            Log.w(TAG, "Unable to request Shizuku permission", t);
        }
    }

    public static boolean isInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(SHIZUKU_PACKAGE, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void open(Context context) {
        Context app = context.getApplicationContext();
        try {
            Intent launch = app.getPackageManager()
                    .getLaunchIntentForPackage(SHIZUKU_PACKAGE);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                app.startActivity(launch);
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            Intent web = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://shizuku.rikka.app/"));
            web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            app.startActivity(web);
        } catch (Throwable t) {
            Log.w(TAG, "Unable to open Shizuku", t);
        }
    }

    /**
     * Runs "pm uninstall --user 0 <package>" through Shizuku's shell identity.
     * The callback is invoked on a background thread.
     */
    public static void uninstall(Context context, String packageName, Callback callback) {
        final String packageToRemove = packageName == null ? "" : packageName.trim();
        if (!isValidPackage(packageToRemove)) {
            callback.onResult(false, "invalid package name");
            return;
        }
        EXECUTOR.execute(() -> {
            IRemoteProcess process = null;
            String output = "";
            boolean success = false;
            int exitCode = -1;
            try {
                if (!canUse()) {
                    callback.onResult(false, "Shizuku is not available");
                    return;
                }
                IShizukuService service = IShizukuService.Stub.asInterface(
                        new ShizukuBinderWrapper(Shizuku.getBinder()));
                process = service.newProcess(
                        new String[]{"/system/bin/pm", "uninstall", "--user", "0",
                                packageToRemove},
                        null, null);
                StringBuilder builder = new StringBuilder();
                readStream(process.getInputStream(), builder);
                readStream(process.getErrorStream(), builder);
                exitCode = process.waitFor();
                output = builder.toString().trim();
                success = exitCode == 0
                        && !output.toLowerCase().contains("failure")
                        && !output.toLowerCase().contains("exception");
            } catch (Throwable t) {
                output = t.toString();
                Log.w(TAG, "Shizuku uninstall failed", t);
            } finally {
                if (process != null) {
                    try {
                        process.destroy();
                    } catch (Throwable ignored) {
                    }
                }
            }
            callback.onResult(success, output + (exitCode >= 0 ? " (exit " + exitCode + ")" : ""));
        });
    }

    private static void readStream(ParcelFileDescriptor descriptor,
                                   StringBuilder builder) throws Exception {
        if (descriptor == null) {
            return;
        }
        try (ParcelFileDescriptor ignored = descriptor;
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     new FileInputStream(descriptor.getFileDescriptor()),
                     StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
    }

    private static boolean isValidPackage(String packageName) {
        if (packageName.isEmpty() || packageName.length() > 255) {
            return false;
        }
        for (int i = 0; i < packageName.length(); i++) {
            char c = packageName.charAt(i);
            if (!(c == '.' || c == '_'
                    || (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9'))) {
                return false;
            }
        }
        return true;
    }

    public interface Callback {
        void onResult(boolean success, String output);
    }
}
