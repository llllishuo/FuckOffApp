package com.menghanstudio.gooutpls.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

import com.menghanstudio.gooutpls.core.GuardEngine;

public class UninstallResultReceiver extends BroadcastReceiver {

    public static final String ACTION_UNINSTALL_STATUS =
            "com.menghanstudio.gooutpls.action.UNINSTALL_STATUS";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String packageName = intent.getStringExtra(GuardEngine.EXTRA_PACKAGE);
        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        String label = intent.getStringExtra(GuardEngine.EXTRA_LABEL);
        int status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        GuardEngine.onAutoUninstallResult(context, packageName, label, status);
    }
}
