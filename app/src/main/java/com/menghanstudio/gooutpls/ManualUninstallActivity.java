package com.menghanstudio.gooutpls;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import com.menghanstudio.gooutpls.core.AppCatalog;
import com.menghanstudio.gooutpls.core.AppPrefs;
import com.menghanstudio.gooutpls.core.AppSafetyGuard;
import com.menghanstudio.gooutpls.core.GuardEngine;

/**
 * Invisible trampoline that opens the system uninstall confirmation. Keeping
 * the activity around lets the app log whether the user confirmed or
 * cancelled; the PACKAGE_FULLY_REMOVED broadcast is a second safety net.
 */
public class ManualUninstallActivity extends Activity {

    public static final String EXTRA_PACKAGE = "extra_package";
    public static final String EXTRA_LABEL = "extra_label";

    private static final int REQUEST_UNINSTALL = 501;
    private static final String STATE_PACKAGE = "state_package";
    private static final String STATE_LABEL = "state_label";

    private String packageName;
    private String label;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            packageName = savedInstanceState.getString(STATE_PACKAGE);
            label = savedInstanceState.getString(STATE_LABEL);
        } else {
            Intent source = getIntent();
            packageName = source == null ? null : source.getStringExtra(EXTRA_PACKAGE);
            label = source == null ? null : source.getStringExtra(EXTRA_LABEL);
        }

        if (packageName == null || packageName.isEmpty()) {
            finish();
            return;
        }
        if (label == null || label.trim().isEmpty()) {
            label = AppCatalog.labelFor(this, packageName);
        }
        if (!AppCatalog.isInstalled(this, packageName)) {
            Toast.makeText(this, R.string.toast_not_installed, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (AppSafetyGuard.isProtected(this, packageName)) {
            Toast.makeText(this, R.string.toast_protected_app, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        AppPrefs.markPendingManual(this, packageName);
        Intent uninstall = new Intent(Intent.ACTION_DELETE,
                Uri.parse("package:" + packageName));
        uninstall.putExtra(Intent.EXTRA_RETURN_RESULT, true);
        try {
            startActivityForResult(uninstall, REQUEST_UNINSTALL);
        } catch (ActivityNotFoundException e) {
            AppPrefs.clearPendingManual(this, packageName);
            Toast.makeText(this, R.string.toast_uninstall_failed, Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) {
            AppPrefs.clearPendingManual(this, packageName);
            Toast.makeText(this, R.string.toast_uninstall_failed, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_PACKAGE, packageName);
        outState.putString(STATE_LABEL, label);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_UNINSTALL) {
            return;
        }
        boolean removed = !AppCatalog.isInstalled(this, packageName);
        GuardEngine.finalizeManualResult(this, packageName, label, removed);
        finish();
    }
}
