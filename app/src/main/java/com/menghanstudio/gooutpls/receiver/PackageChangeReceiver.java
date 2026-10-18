package com.menghanstudio.gooutpls.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.menghanstudio.gooutpls.core.GuardEngine;

/**
 * Manifest receiver for package changes. These are protected system
 * broadcasts, so no extra permission is required.
 */
public class PackageChangeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        Uri data = intent.getData();
        String packageName = data == null ? null : data.getSchemeSpecificPart();
        if (packageName == null || packageName.isEmpty()) {
            return;
        }

        if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())) {
            if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                return;
            }
            GuardEngine.onPackageAdded(context, packageName);
        } else if (Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(intent.getAction())) {
            GuardEngine.onPackageRemoved(context, packageName);
        }
    }
}
