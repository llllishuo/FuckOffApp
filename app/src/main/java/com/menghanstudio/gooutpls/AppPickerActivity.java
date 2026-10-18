package com.menghanstudio.gooutpls;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.menghanstudio.gooutpls.core.AppCatalog;
import com.menghanstudio.gooutpls.core.AppPrefs;
import com.menghanstudio.gooutpls.core.BlacklistEntry;
import com.menghanstudio.gooutpls.core.BlacklistRepository;
import com.menghanstudio.gooutpls.core.CleanupMode;
import com.menghanstudio.gooutpls.core.EventRepository;
import com.menghanstudio.gooutpls.core.GuardEngine;
import com.menghanstudio.gooutpls.core.InstalledApp;
import com.menghanstudio.gooutpls.service.MonitorService;
import com.menghanstudio.gooutpls.ui.BaseActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppPickerActivity extends BaseActivity {

    private final List<InstalledApp> allApps = new ArrayList<>();
    private final List<InstalledApp> filteredApps = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();

    private ExecutorService executor;
    private AppAdapter adapter;
    private ListView listApps;
    private LinearLayout emptyApps;
    private TextView tvEmptyApps;
    private TextView tvPickerSubtitle;
    private TextView btnConfirm;
    private ProgressBar progressApps;
    private EditText etSearch;
    private boolean loaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_picker);
        applySystemBarInsets(findViewById(R.id.root));

        executor = Executors.newSingleThreadExecutor();
        listApps = findViewById(R.id.listApps);
        emptyApps = findViewById(R.id.emptyApps);
        tvEmptyApps = findViewById(R.id.tvEmptyApps);
        tvPickerSubtitle = findViewById(R.id.tvPickerSubtitle);
        btnConfirm = findViewById(R.id.btnConfirm);
        progressApps = findViewById(R.id.progressApps);
        etSearch = findViewById(R.id.etSearch);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnConfirm.setOnClickListener(v -> confirmSelection());
        listApps.setOnItemClickListener((parent, view, position, id) -> {
            InstalledApp app = (InstalledApp) parent.getItemAtPosition(position);
            if (selected.contains(app.packageName)) {
                selected.remove(app.packageName);
            } else {
                selected.add(app.packageName);
            }
            adapter.notifyDataSetChanged();
            updateBottomBar();
        });
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        loadApps();
    }

    private void loadApps() {
        progressApps.setVisibility(View.VISIBLE);
        emptyApps.setVisibility(View.GONE);
        listApps.setVisibility(View.INVISIBLE);
        updateBottomBar();

        executor.execute(() -> {
            List<InstalledApp> apps = AppCatalog.loadUserApps(getApplicationContext());
            Set<String> blocked = new HashSet<>();
            for (BlacklistEntry entry : new BlacklistRepository(getApplicationContext()).getAll()) {
                blocked.add(entry.packageName);
            }
            List<InstalledApp> available = new ArrayList<>();
            for (InstalledApp app : apps) {
                if (!blocked.contains(app.packageName)) {
                    available.add(app);
                }
            }
            runOnUiThread(() -> {
                allApps.clear();
                allApps.addAll(available);
                loaded = true;
                progressApps.setVisibility(View.GONE);
                adapter = new AppAdapter();
                listApps.setAdapter(adapter);
                applyFilter();
            });
        });
    }

    private void applyFilter() {
        if (!loaded || adapter == null) {
            return;
        }
        String query = etSearch.getText() == null ? "" : etSearch.getText().toString();
        filteredApps.clear();
        for (InstalledApp app : allApps) {
            if (app.matches(query)) {
                filteredApps.add(app);
            }
        }
        adapter.notifyDataSetChanged();
        if (filteredApps.isEmpty()) {
            emptyApps.setVisibility(View.VISIBLE);
            listApps.setVisibility(View.GONE);
            tvEmptyApps.setText(allApps.isEmpty()
                    ? R.string.picker_empty : R.string.picker_no_result);
        } else {
            emptyApps.setVisibility(View.GONE);
            listApps.setVisibility(View.VISIBLE);
        }
        updateBottomBar();
    }

    private void updateBottomBar() {
        int count = selected.size();
        tvPickerSubtitle.setText(count == 0
                ? getString(R.string.picker_subtitle)
                : getString(R.string.picker_selected, count));
        btnConfirm.setText(count == 0
                ? getString(R.string.picker_confirm)
                : getString(R.string.picker_confirm_count, count));
        boolean enabled = loaded && count > 0;
        btnConfirm.setEnabled(enabled);
        btnConfirm.setAlpha(enabled ? 1f : 0.35f);
    }

    private void confirmSelection() {
        if (!loaded || selected.isEmpty()) {
            return;
        }
        List<InstalledApp> chosen = new ArrayList<>();
        for (InstalledApp app : allApps) {
            if (selected.contains(app.packageName)) {
                chosen.add(app);
            }
        }
        if (chosen.isEmpty()) {
            return;
        }

        boolean autoClean = AppPrefs.isMonitoringEnabled(this)
                && AppPrefs.isAutoUninstallEnabled(this)
                && CleanupMode.isAvailable(this);
        if (autoClean) {
            showAutoCleanWarning(chosen);
        } else {
            commit(chosen, false);
        }
    }

    private void showAutoCleanWarning(List<InstalledApp> chosen) {
        StringBuilder names = new StringBuilder();
        int limit = Math.min(8, chosen.size());
        for (int i = 0; i < limit; i++) {
            names.append("• ").append(chosen.get(i).displayLabel()).append('\n');
        }
        if (chosen.size() > limit) {
            names.append("…");
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.picker_auto_warning_title)
                .setMessage(getString(R.string.picker_auto_warning_msg, names.toString().trim()))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_continue,
                        (dialog, which) -> commit(chosen, true))
                .show();
    }

    private void commit(List<InstalledApp> chosen, boolean autoClean) {
        BlacklistRepository repository = new BlacklistRepository(this);
        List<InstalledApp> addedApps = new ArrayList<>();
        int skippedForLimit = 0;
        for (InstalledApp app : chosen) {
            boolean existed = repository.contains(app.packageName);
            if (repository.add(app.packageName, app.displayLabel())) {
                addedApps.add(app);
                EventRepository.add(this, EventRepository.TYPE_ADD, app.packageName,
                        getString(R.string.event_add, app.displayLabel()));
            } else if (!existed) {
                skippedForLimit++;
            }
        }

        if (autoClean) {
            for (InstalledApp app : addedApps) {
                GuardEngine.requestAutoUninstall(this, app.packageName, app.displayLabel());
            }
        }

        if (AppPrefs.isMonitoringEnabled(this)) {
            MonitorService.refresh(this);
        }

        int count = addedApps.size();
        String message;
        if (autoClean && count > 0) {
            message = getString(R.string.picker_auto_started);
        } else if (!AppPrefs.isMonitoringEnabled(this)) {
            message = getString(R.string.picker_added_paused, count);
        } else {
            message = getString(R.string.picker_added_manual, count);
        }
        if (skippedForLimit > 0) {
            message = count == 0
                    ? getString(R.string.blacklist_limit_reached)
                    : message + "\n" + getString(R.string.blacklist_limit_reached);
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();

        setResult(RESULT_OK, new Intent());
        finish();
    }

    @Override
    protected void onDestroy() {
        if (executor != null) {
            executor.shutdownNow();
        }
        super.onDestroy();
    }

    private final class AppAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return filteredApps.size();
        }

        @Override
        public Object getItem(int position) {
            return filteredApps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(AppPickerActivity.this)
                        .inflate(R.layout.item_app, parent, false);
                holder = new ViewHolder();
                holder.icon = convertView.findViewById(R.id.ivAppIcon);
                holder.name = convertView.findViewById(R.id.tvAppName);
                holder.pkg = convertView.findViewById(R.id.tvAppPackage);
                holder.check = convertView.findViewById(R.id.checkApp);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            InstalledApp app = filteredApps.get(position);
            holder.name.setText(app.displayLabel());
            holder.pkg.setText(app.packageName);
            Drawable icon = app.icon;
            if (icon != null) {
                holder.icon.setImageDrawable(icon);
            } else {
                holder.icon.setImageResource(R.drawable.ic_apps_24);
            }
            holder.check.setChecked(selected.contains(app.packageName));
            return convertView;
        }
    }

    private static final class ViewHolder {
        ImageView icon;
        TextView name;
        TextView pkg;
        CheckBox check;
    }
}
