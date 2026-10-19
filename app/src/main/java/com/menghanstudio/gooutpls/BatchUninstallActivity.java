package com.menghanstudio.gooutpls;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.menghanstudio.gooutpls.core.AppCatalog;
import com.menghanstudio.gooutpls.core.AppSafetyGuard;
import com.menghanstudio.gooutpls.core.BlacklistEntry;
import com.menghanstudio.gooutpls.core.BlacklistRepository;
import com.menghanstudio.gooutpls.util.IslandNotificationManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Sequential system-uninstall trampoline used by the island/live-update
 * "uninstall all" action. It never bypasses the system UI: manual mode still
 * asks for every confirmation, while the accessibility assist can confirm the
 * dialogs automatically. DO/Shizuku users normally use their silent engine
 * instead and do not need this batch.
 */
public class BatchUninstallActivity extends Activity {

    private static final int REQUEST_UNINSTALL = 601;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<String> packages = new ArrayList<>();
    private int index;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        packages.addAll(installedBlacklistPackages());
        if (packages.isEmpty()) {
            Toast.makeText(this, R.string.batch_empty, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (savedInstanceState != null) {
            index = savedInstanceState.getInt("index", 0);
        }
        Toast.makeText(this, getString(R.string.batch_started, packages.size()),
                Toast.LENGTH_SHORT).show();
        handler.postDelayed(this::launchNext, 250L);
    }

    private void launchNext() {
        if (index >= packages.size()) {
            IslandNotificationManager.refresh(this);
            finish();
            return;
        }
        String packageName = packages.get(index++);
        Intent intent = new Intent(this, ManualUninstallActivity.class)
                .putExtra(ManualUninstallActivity.EXTRA_PACKAGE, packageName)
                .putExtra(ManualUninstallActivity.EXTRA_LABEL,
                        AppCatalog.labelFor(this, packageName));
        try {
            startActivityForResult(intent, REQUEST_UNINSTALL);
        } catch (Exception e) {
            handler.postDelayed(this::launchNext, 300L);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_UNINSTALL) {
            handler.postDelayed(this::launchNext, 300L);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("index", index);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private List<String> installedBlacklistPackages() {
        List<String> result = new ArrayList<>();
        AppSafetyGuard.Snapshot safety = AppSafetyGuard.snapshot(this);
        for (BlacklistEntry entry : new BlacklistRepository(this).getAll()) {
            if (AppCatalog.isInstalled(this, entry.packageName)
                    && !safety.isProtected(this, entry.packageName)) {
                result.add(entry.packageName);
            }
        }
        return result;
    }
}
