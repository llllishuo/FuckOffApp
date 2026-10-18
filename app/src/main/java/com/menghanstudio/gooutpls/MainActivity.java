package com.menghanstudio.gooutpls;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.menghanstudio.gooutpls.core.AppCatalog;
import com.menghanstudio.gooutpls.core.AppPrefs;
import com.menghanstudio.gooutpls.core.AppSafetyGuard;
import com.menghanstudio.gooutpls.core.BlacklistEntry;
import com.menghanstudio.gooutpls.core.BlacklistJsonCodec;
import com.menghanstudio.gooutpls.core.BlacklistRepository;
import com.menghanstudio.gooutpls.core.DeviceOwnerTools;
import com.menghanstudio.gooutpls.core.EventEntry;
import com.menghanstudio.gooutpls.core.EventRepository;
import com.menghanstudio.gooutpls.core.GuardEngine;
import com.menghanstudio.gooutpls.service.MonitorService;
import com.menghanstudio.gooutpls.service.ScanJobService;
import com.menghanstudio.gooutpls.ui.BaseActivity;
import com.menghanstudio.gooutpls.util.NotificationHelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends BaseActivity {

    private static final String TAG = "FuckOffJson";
    private static final int REQUEST_NOTIFICATIONS = 7001;
    private static final int REQUEST_EXPORT_JSON = 7101;
    private static final int REQUEST_IMPORT_JSON = 7102;
    private static final int MAX_IMPORT_BYTES = 2 * 1024 * 1024;

    private boolean binding;
    private boolean eventsReceiverRegistered;

    private Switch switchMonitor;
    private Switch switchAuto;
    private TextView tvStatusTitle;
    private TextView tvStatusDesc;
    private TextView tvModeBadge;
    private TextView tvAutoState;
    private TextView tvAutoDesc;
    private TextView tvCountBlacklist;
    private TextView tvCountPending;
    private TextView tvCountRemoved;
    private TextView btnOwner;
    private TextView btnClearLog;
    private TextView tvEmptyEvents;
    private TextView tvFooter;
    private LinearLayout blacklistContainer;
    private LinearLayout eventsContainer;
    private View cardEmptyBlacklist;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver eventsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUi();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        applySystemBarInsets(findViewById(R.id.root));
        bindViews();
        setupListeners();
        constrainDockButton();
        tvFooter.setText(getString(R.string.footer, BuildConfig.VERSION_NAME));
        refreshUi();
    }

    private void bindViews() {
        switchMonitor = findViewById(R.id.switchMonitor);
        switchAuto = findViewById(R.id.switchAuto);
        tvStatusTitle = findViewById(R.id.tvStatusTitle);
        tvStatusDesc = findViewById(R.id.tvStatusDesc);
        tvModeBadge = findViewById(R.id.tvModeBadge);
        tvAutoState = findViewById(R.id.tvAutoState);
        tvAutoDesc = findViewById(R.id.tvAutoDesc);
        tvCountBlacklist = findViewById(R.id.tvCountBlacklist);
        tvCountPending = findViewById(R.id.tvCountPending);
        tvCountRemoved = findViewById(R.id.tvCountRemoved);
        btnOwner = findViewById(R.id.btnOwner);
        btnClearLog = findViewById(R.id.btnClearLog);
        tvEmptyEvents = findViewById(R.id.tvEmptyEvents);
        tvFooter = findViewById(R.id.tvFooter);
        blacklistContainer = findViewById(R.id.blacklistContainer);
        eventsContainer = findViewById(R.id.eventsContainer);
        cardEmptyBlacklist = findViewById(R.id.cardEmptyBlacklist);
    }

    private void setupListeners() {
        switchMonitor.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (binding) {
                return;
            }
            if (isChecked) {
                requestNotificationPermission();
                AppPrefs.setMonitoringEnabled(this, true);
                MonitorService.start(this);
                ScanJobService.schedule(this);
                EventRepository.add(this, EventRepository.TYPE_MONITOR_ON, null,
                        getString(R.string.event_monitor_on));
                Toast.makeText(this, R.string.toast_monitor_on, Toast.LENGTH_SHORT).show();
            } else {
                AppPrefs.setMonitoringEnabled(this, false);
                MonitorService.stop(this);
                ScanJobService.cancel(this);
                EventRepository.add(this, EventRepository.TYPE_MONITOR_OFF, null,
                        getString(R.string.event_monitor_off));
                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm != null) {
                    nm.cancel(NotificationHelper.MONITOR_NOTIFICATION_ID);
                }
                Toast.makeText(this, R.string.toast_monitor_off, Toast.LENGTH_SHORT).show();
            }
            refreshUi();
        });

        switchAuto.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (binding) {
                return;
            }
            if (!isChecked) {
                AppPrefs.setAutoUninstallEnabled(this, false);
                Toast.makeText(this, R.string.toast_auto_off, Toast.LENGTH_SHORT).show();
                refreshUi();
                return;
            }
            if (!DeviceOwnerTools.isDeviceOwner(this)) {
                binding = true;
                switchAuto.setChecked(false);
                binding = false;
                refreshUi();
                startActivity(new Intent(this, DeviceOwnerActivity.class));
                return;
            }
            int installed = countAutoEligibleInstalledBlacklist();
            if (installed > 0) {
                binding = true;
                switchAuto.setChecked(false);
                binding = false;
                showAutoEnableDialog(installed);
            } else {
                enableAutoMode();
            }
        });

        View.OnClickListener pick = v -> startActivity(
                new Intent(this, AppPickerActivity.class));
        findViewById(R.id.btnAddTop).setOnClickListener(pick);
        findViewById(R.id.btnAddBottom).setOnClickListener(pick);
        findViewById(R.id.btnExportTop).setOnClickListener(v -> requestExport());
        findViewById(R.id.btnImportTop).setOnClickListener(v -> requestImport());
        findViewById(R.id.btnSafetyRules).setOnClickListener(v ->
                startActivity(new Intent(this, SafetyRulesActivity.class)));
        tvModeBadge.setOnClickListener(v -> openOwnerScreen());
        btnOwner.setOnClickListener(v -> openOwnerScreen());
        btnClearLog.setOnClickListener(v -> confirmClearLog());
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
        }
    }

    private void enableAutoMode() {
        requestNotificationPermission();
        AppPrefs.setAutoUninstallEnabled(this, true);
        Toast.makeText(this, R.string.toast_auto_on, Toast.LENGTH_SHORT).show();
        refreshUi();
        if (AppPrefs.isMonitoringEnabled(this)) {
            MonitorService.start(this);
            ScanJobService.schedule(this);
        }
    }

    private void showAutoEnableDialog(int installedCount) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.enable_auto_title)
                .setMessage(getString(R.string.enable_auto_msg, installedCount))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_continue, (dialog, which) -> enableAutoMode())
                .show();
    }

    private int countAutoEligibleInstalledBlacklist() {
        AppSafetyGuard.Snapshot safety = AppSafetyGuard.snapshot(this);
        int count = 0;
        for (BlacklistEntry entry : new BlacklistRepository(this).getAll()) {
            if (entry.followsSystem()
                    && AppCatalog.isInstalled(this, entry.packageName)
                    && !safety.isProtected(this, entry.packageName)) {
                count++;
            }
        }
        return count;
    }

    private void openOwnerScreen() {
        startActivity(new Intent(this, DeviceOwnerActivity.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean owner = DeviceOwnerTools.isDeviceOwner(this);
        if (!owner && AppPrefs.isAutoUninstallEnabled(this)) {
            AppPrefs.setAutoUninstallEnabled(this, false);
        }
        DeviceOwnerTools.syncOwnerState(this);
        GuardEngine.reconcilePending(this);
        if (AppPrefs.isMonitoringEnabled(this)) {
            MonitorService.start(this);
            ScanJobService.schedule(this);
        }
        refreshUi();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(EventRepository.ACTION_EVENTS_CHANGED);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(eventsReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(eventsReceiver, filter);
            }
            eventsReceiverRegistered = true;
        } catch (Exception ignored) {
            eventsReceiverRegistered = false;
        }
    }

    @Override
    protected void onStop() {
        if (eventsReceiverRegistered) {
            try {
                unregisterReceiver(eventsReceiver);
            } catch (Exception ignored) {
                // Already unregistered.
            }
            eventsReceiverRegistered = false;
        }
        super.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        refreshUi();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS
                && (grantResults.length == 0
                || grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
            Toast.makeText(this, R.string.toast_notification_denied,
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult request=" + requestCode
                + " result=" + resultCode
                + " uri=" + (data == null ? null : data.getData()));
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        if (requestCode == REQUEST_EXPORT_JSON) {
            writeBlacklist(uri);
        } else if (requestCode == REQUEST_IMPORT_JSON) {
            readBlacklist(uri);
        }
    }

    private void requestExport() {
        String fileName = "FuckOff-blacklist-"
                + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT)
                .format(new Date())
                + ".json";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, fileName);
        try {
            startActivityForResult(intent, REQUEST_EXPORT_JSON);
        } catch (Exception e) {
            Toast.makeText(this, R.string.export_chooser_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void requestImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                        "application/json", "text/json", "text/plain",
                        "application/octet-stream"});
        try {
            startActivityForResult(intent, REQUEST_IMPORT_JSON);
        } catch (Exception e) {
            Toast.makeText(this, R.string.import_chooser_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void writeBlacklist(Uri uri) {
        List<BlacklistEntry> entries = new BlacklistRepository(this).getAll();
        Log.d(TAG, "export entries=" + entries.size() + " uri=" + uri);
        try {
            String json = BlacklistJsonCodec.encode(entries);
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) {
                    throw new IOException("unwriteable");
                }
                out.write(json.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            Toast.makeText(this, getString(R.string.export_success, entries.size()),
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.w(TAG, "export failed", e);
            Toast.makeText(this, R.string.export_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void readBlacklist(Uri uri) {
        String raw;
        try {
            raw = readImportText(uri);
        } catch (Exception e) {
            Log.w(TAG, "import read failed", e);
            int message = "too_large".equals(e.getMessage())
                    ? R.string.import_too_large : R.string.import_read_failed;
            showImportError(message);
            return;
        }

        BlacklistJsonCodec.ImportResult result;
        try {
            result = BlacklistJsonCodec.decode(raw);
        } catch (Exception e) {
            Log.w(TAG, "import parse failed", e);
            String reason = e.getMessage();
            int message;
            if ("too_large".equals(reason)) {
                message = R.string.import_too_large;
            } else if ("too_many_entries".equals(reason)) {
                message = R.string.import_too_many_entries;
            } else {
                message = R.string.import_invalid_json;
            }
            showImportError(message);
            return;
        }

        Log.d(TAG, "import parsed valid=" + result.entries.size()
                + " invalid=" + result.invalidCount
                + " duplicates=" + result.duplicateCount);
        if (result.entries.isEmpty()) {
            showImportError(R.string.import_empty);
            return;
        }
        confirmImport(result);
    }

    private String readImportText(Uri uri) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IOException("unreadable");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_IMPORT_BYTES) {
                    throw new IOException("too_large");
                }
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private void confirmImport(BlacklistJsonCodec.ImportResult result) {
        BlacklistRepository repository = new BlacklistRepository(this);
        Set<String> existing = new HashSet<>();
        for (BlacklistEntry entry : repository.getAll()) {
            existing.add(entry.packageName);
        }
        int already = 0;
        for (BlacklistEntry entry : result.entries) {
            if (existing.contains(entry.packageName)) {
                already++;
            }
        }
        int newCount = result.entries.size() - already;
        int available = Math.max(0,
                BlacklistRepository.MAX_ENTRIES - repository.size());

        String message = getString(R.string.import_confirm_msg,
                result.entries.size(), newCount, already, result.ignoredCount());
        if (newCount > available) {
            message += "\n\n" + getString(R.string.import_limit_notice,
                    BlacklistRepository.MAX_ENTRIES, available);
        }
        message += "\n\n" + getString(R.string.import_manual_notice);

        new AlertDialog.Builder(this)
                .setTitle(R.string.import_confirm_title)
                .setMessage(message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.import_confirm_action,
                        (dialog, which) -> importEntries(result.entries))
                .show();
    }

    private void importEntries(List<BlacklistEntry> entries) {
        BlacklistRepository repository = new BlacklistRepository(this);
        int added = 0;
        int skippedForLimit = 0;
        for (BlacklistEntry entry : entries) {
            boolean existed = repository.contains(entry.packageName);
            boolean isNew = repository.addImported(
                    entry.packageName, entry.displayLabel(), entry.addedAt);
            if (isNew) {
                added++;
            } else if (!existed) {
                skippedForLimit++;
            }
        }

        refreshUi();
        // Deliberately do not start a scan here. Imported entries are all in
        // manual mode, and import must never trigger an immediate cleanup.
        // REFRESH only updates the foreground notification count.
        if (AppPrefs.isMonitoringEnabled(this)) {
            MonitorService.refresh(this);
        }
        String message = added > 0
                ? getString(R.string.import_success, added)
                : getString(R.string.import_none_added);
        if (skippedForLimit > 0) {
            message += "\n" + getString(R.string.blacklist_limit_reached);
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void showImportError(int message) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.import_failed_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void refreshUi() {
        boolean owner = DeviceOwnerTools.isDeviceOwner(this);
        boolean monitoring = AppPrefs.isMonitoringEnabled(this);
        boolean auto = AppPrefs.isAutoUninstallEnabled(this) && owner;

        binding = true;
        try {
            switchMonitor.setChecked(monitoring);
            switchAuto.setChecked(auto);
            switchAuto.setEnabled(owner);
            switchAuto.setAlpha(owner ? 1f : 0.45f);

            tvStatusTitle.setText(monitoring ? R.string.status_on : R.string.status_off);
            tvStatusTitle.setTextColor(getColor(monitoring ? R.color.teal : R.color.text));
            tvStatusDesc.setText(monitoring
                    ? R.string.status_on_desc : R.string.status_off_desc);

            tvModeBadge.setText(auto ? R.string.mode_auto : R.string.mode_manual);
            tvModeBadge.setBackgroundResource(auto
                    ? R.drawable.bg_chip_accent : R.drawable.bg_chip_neutral);
            tvModeBadge.setTextColor(getColor(auto ? R.color.accent : R.color.text_soft));

            if (!owner) {
                tvAutoState.setText(R.string.auto_state_locked);
                tvAutoState.setTextColor(getColor(R.color.amber));
                tvAutoDesc.setText(R.string.auto_requires_owner);
                btnOwner.setText(R.string.auto_owner_open);
            } else if (auto) {
                tvAutoState.setText(R.string.auto_state_on);
                tvAutoState.setTextColor(getColor(R.color.teal));
                tvAutoDesc.setText(R.string.auto_desc_on);
                btnOwner.setText(R.string.auto_owner_manage);
            } else {
                tvAutoState.setText(R.string.auto_state_off);
                tvAutoState.setTextColor(getColor(R.color.text_muted));
                tvAutoDesc.setText(R.string.auto_desc_off);
                btnOwner.setText(R.string.auto_owner_manage);
            }

            List<BlacklistEntry> entries = new BlacklistRepository(this).getAll();
            Set<String> installed = new HashSet<>();
            Set<String> protectedInstalled = new HashSet<>();
            AppSafetyGuard.Snapshot safety = AppSafetyGuard.snapshot(this);
            for (BlacklistEntry entry : entries) {
                if (!AppCatalog.isInstalled(this, entry.packageName)) {
                    continue;
                }
                installed.add(entry.packageName);
                if (safety.isProtected(this, entry.packageName)) {
                    protectedInstalled.add(entry.packageName);
                }
            }
            tvCountBlacklist.setText(String.valueOf(entries.size()));
            tvCountPending.setText(String.valueOf(
                    installed.size() - protectedInstalled.size()));
            tvCountRemoved.setText(String.valueOf(AppPrefs.getRemovedCount(this)));

            rebuildBlacklist(entries, installed, protectedInstalled, owner, auto);
            rebuildEvents();
        } finally {
            binding = false;
        }
    }

    private void rebuildBlacklist(List<BlacklistEntry> entries, Set<String> installedPackages,
                                  Set<String> protectedInstalled, boolean owner, boolean auto) {
        blacklistContainer.removeAllViews();
        cardEmptyBlacklist.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);

        for (BlacklistEntry entry : entries) {
            boolean installed = installedPackages.contains(entry.packageName);
            boolean protectedApp = installed && protectedInstalled.contains(entry.packageName);
            View row = getLayoutInflater().inflate(
                    R.layout.item_blacklist, blacklistContainer, false);

            ImageView icon = row.findViewById(R.id.ivBlacklistIcon);
            Drawable drawable = installed ? AppCatalog.iconFor(this, entry.packageName) : null;
            if (drawable != null) {
                icon.setBackground(null);
                icon.setPadding(0, 0, 0, 0);
                icon.setAlpha(1f);
                icon.setImageDrawable(drawable);
            } else {
                int pad = dp(10);
                icon.setBackgroundResource(R.drawable.bg_icon_button);
                icon.setPadding(pad, pad, pad, pad);
                icon.setAlpha(installed ? 0.8f : 0.4f);
                icon.setImageResource(R.drawable.ic_apps_24);
            }

            TextView name = row.findViewById(R.id.tvBlacklistName);
            TextView pkg = row.findViewById(R.id.tvBlacklistPackage);
            TextView status = row.findViewById(R.id.tvBlacklistStatus);
            TextView mode = row.findViewById(R.id.tvBlacklistMode);
            TextView uninstall = row.findViewById(R.id.btnBlacklistUninstall);
            TextView remove = row.findViewById(R.id.btnBlacklistRemove);

            name.setText(entry.displayLabel());
            pkg.setText(entry.packageName);
            if (protectedApp) {
                status.setText(R.string.status_protected);
                status.setBackgroundResource(R.drawable.bg_chip_neutral);
                status.setTextColor(getColor(R.color.text_soft));
            } else if (installed) {
                status.setText(R.string.status_pending);
                status.setBackgroundResource(R.drawable.bg_chip_warn);
                status.setTextColor(getColor(R.color.amber));
            } else {
                status.setText(R.string.status_removed);
                status.setBackgroundResource(R.drawable.bg_chip_ok);
                status.setTextColor(getColor(R.color.teal));
            }

            mode.setText(entry.followsSystem()
                    ? R.string.mode_follow_system : R.string.mode_manual_only);
            mode.setTextColor(getColor(entry.followsSystem()
                    ? R.color.text_muted : R.color.accent));
            mode.setBackgroundResource(entry.followsSystem()
                    ? R.drawable.bg_chip_neutral : R.drawable.bg_chip_accent);
            mode.setOnClickListener(v -> showModeDialog(entry));

            uninstall.setVisibility(installed && !protectedApp ? View.VISIBLE : View.GONE);
            uninstall.setOnClickListener(v -> showUninstallDialog(
                    entry.packageName, entry.displayLabel(), entry.mode, owner, auto));
            remove.setOnClickListener(v -> confirmRemove(entry));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(10);
            blacklistContainer.addView(row, params);
        }
    }

    private void rebuildEvents() {
        eventsContainer.removeAllViews();
        List<EventEntry> events = EventRepository.getRecent(this, 8);
        LayoutInflater inflater = getLayoutInflater();
        for (EventEntry event : events) {
            View row = inflater.inflate(R.layout.item_event, eventsContainer, false);
            TextView message = row.findViewById(R.id.tvEventMessage);
            TextView time = row.findViewById(R.id.tvEventTime);
            View dot = row.findViewById(R.id.viewEventDot);

            message.setText(event.message);
            time.setText(formatRelativeTime(event.time));
            dot.setBackgroundTintList(ColorStateList.valueOf(colorForEvent(event.type)));
            eventsContainer.addView(row);
        }
        tvEmptyEvents.setVisibility(events.isEmpty() ? View.VISIBLE : View.GONE);
        btnClearLog.setEnabled(!events.isEmpty());
        btnClearLog.setAlpha(events.isEmpty() ? 0.35f : 1f);
    }

    private int colorForEvent(String type) {
        if (EventRepository.TYPE_AUTO_SUCCESS.equals(type)
                || EventRepository.TYPE_MANUAL_SUCCESS.equals(type)) {
            return getColor(R.color.teal);
        }
        if (EventRepository.TYPE_AUTO_FAIL.equals(type)) {
            return getColor(R.color.accent);
        }
        if (EventRepository.TYPE_MANUAL_PROMPT.equals(type)) {
            return getColor(R.color.amber);
        }
        return getColor(R.color.purple);
    }

    private String formatRelativeTime(long time) {
        long diff = Math.max(0L, System.currentTimeMillis() - time);
        long minutes = diff / 60_000L;
        if (minutes < 1L) {
            return getString(R.string.time_just_now);
        }
        if (minutes < 60L) {
            return getString(R.string.time_minutes, (int) minutes);
        }
        long hours = minutes / 60L;
        if (hours < 24L) {
            return getString(R.string.time_hours, (int) hours);
        }
        return getString(R.string.time_days, (int) (hours / 24L));
    }

    private void showModeDialog(BlacklistEntry entry) {
        String[] choices = new String[]{
                getString(R.string.mode_follow_choice),
                getString(R.string.mode_manual_choice)
        };
        int checked = entry.followsSystem() ? 0 : 1;
        new AlertDialog.Builder(this)
                .setTitle(R.string.mode_choose_title)
                .setSingleChoiceItems(choices, checked, (dialog, which) -> {
                    String newMode = which == 0
                            ? BlacklistEntry.MODE_SYSTEM : BlacklistEntry.MODE_MANUAL;
                    if (!newMode.equals(entry.mode)) {
                        new BlacklistRepository(this).setMode(entry.packageName, newMode);
                        Toast.makeText(this, which == 0
                                        ? R.string.mode_changed_follow
                                        : R.string.mode_changed_manual,
                                Toast.LENGTH_SHORT).show();
                        refreshUi();
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showUninstallDialog(String packageName, String label, String mode,
                                     boolean owner, boolean auto) {
        if (!AppCatalog.isInstalled(this, packageName)) {
            Toast.makeText(this, R.string.toast_not_installed, Toast.LENGTH_SHORT).show();
            refreshUi();
            return;
        }
        if (AppSafetyGuard.isProtected(this, packageName)) {
            Toast.makeText(this, R.string.toast_protected_app, Toast.LENGTH_LONG).show();
            refreshUi();
            return;
        }
        boolean autoActive = owner && auto
                && BlacklistEntry.MODE_SYSTEM.equals(mode);
        String message = autoActive
                ? getString(R.string.confirm_auto_uninstall_msg, label)
                : getString(R.string.confirm_uninstall_msg, label);
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_uninstall_title)
                .setMessage(message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_uninstall, (dialog, which) -> {
                    if (autoActive) {
                        boolean submitted = GuardEngine.requestAutoUninstall(
                                this, packageName, label);
                        if (submitted) {
                            handler.postDelayed(this::refreshUi, 1200L);
                        } else {
                            launchManualUninstall(packageName, label);
                        }
                    } else {
                        launchManualUninstall(packageName, label);
                    }
                })
                .show();
    }

    private void launchManualUninstall(String packageName, String label) {
        Intent intent = new Intent(this, ManualUninstallActivity.class)
                .putExtra(ManualUninstallActivity.EXTRA_PACKAGE, packageName)
                .putExtra(ManualUninstallActivity.EXTRA_LABEL, label);
        try {
            startActivity(intent);
            Toast.makeText(this, R.string.toast_uninstall_opened, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.toast_uninstall_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmRemove(BlacklistEntry entry) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_remove_title)
                .setMessage(getString(R.string.confirm_remove_msg, entry.displayLabel()))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_remove, (dialog, which) -> {
                    new BlacklistRepository(this).remove(entry.packageName);
                    EventRepository.add(this, EventRepository.TYPE_REMOVE,
                            entry.packageName,
                            getString(R.string.event_remove, entry.displayLabel()));
                    Toast.makeText(this, R.string.toast_removed, Toast.LENGTH_SHORT).show();
                    if (AppPrefs.isMonitoringEnabled(this)) {
                        MonitorService.refresh(this);
                    }
                    refreshUi();
                })
                .show();
    }

    private void confirmClearLog() {
        if (EventRepository.getRecent(this, 1).isEmpty()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_clear_log_title)
                .setMessage(R.string.confirm_clear_log_msg)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_clear, (dialog, which) -> {
                    EventRepository.clear(this);
                    refreshUi();
                })
                .show();
    }

    private void constrainDockButton() {
        View button = findViewById(R.id.btnAddBottom);
        int available = getResources().getDisplayMetrics().widthPixels - dp(40);
        int target = Math.min(available, dp(520));
        ViewGroup.LayoutParams params = button.getLayoutParams();
        params.width = Math.max(0, target);
        button.setLayoutParams(params);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
