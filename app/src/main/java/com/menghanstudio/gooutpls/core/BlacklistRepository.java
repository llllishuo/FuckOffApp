package com.menghanstudio.gooutpls.core;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BlacklistRepository {

    /**
     * Rendering and scanning are intentionally bounded. A pathological import
     * must not be able to inflate the home screen until the process crashes.
     */
    public static final int MAX_ENTRIES = 200;

    private static final String FILE = "gooutpls_blacklist";
    private static final String KEY = "entries_v1";
    private static final Object LOCK = new Object();

    private final Context context;

    public BlacklistRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public List<BlacklistEntry> getAll() {
        synchronized (LOCK) {
            List<BlacklistEntry> result = new ArrayList<>();
            String raw = prefs().getString(KEY, "[]");
            try {
                JSONArray array = new JSONArray(raw);
                for (int i = 0; i < array.length() && i < MAX_ENTRIES; i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    String pkg = item.optString("package", "");
                    if (pkg.isEmpty()) {
                        continue;
                    }
                    String label = item.optString("label", pkg);
                    long addedAt = item.optLong("addedAt", 0L);
                    String mode = item.optString("mode", BlacklistEntry.MODE_SYSTEM);
                    result.add(new BlacklistEntry(pkg, label, addedAt, mode));
                }
            } catch (Exception ignored) {
                // A corrupt store should never prevent the app from starting.
            }
            Collections.sort(result, (a, b) -> Long.compare(b.addedAt, a.addedAt));
            return result;
        }
    }

    public BlacklistEntry find(String packageName) {
        for (BlacklistEntry entry : getAll()) {
            if (entry.packageName.equals(packageName)) {
                return entry;
            }
        }
        return null;
    }

    public boolean contains(String packageName) {
        return find(packageName) != null;
    }

    public int size() {
        return getAll().size();
    }

    /**
     * Adds a normal entry, defaulting new entries to "follow system".
     * Existing entries keep their own mode and add time.
     */
    public boolean add(String packageName, String label) {
        return addInternal(packageName, label, System.currentTimeMillis(),
                BlacklistEntry.MODE_SYSTEM, false);
    }

    /**
     * Imported entries always start in manual mode. Existing entries with the
     * same package are also switched to manual so an import cannot immediately
     * trigger an automatic uninstall.
     */
    public boolean addImported(String packageName, String label, long addedAt) {
        return addInternal(packageName, label, addedAt,
                BlacklistEntry.MODE_MANUAL, true);
    }

    private boolean addInternal(String packageName, String label, long addedAt,
                                String newMode, boolean overrideExistingMode) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return false;
        }
        synchronized (LOCK) {
            List<BlacklistEntry> entries = getAll();
            boolean existed = false;
            long effectiveAddedAt = addedAt > 0L ? addedAt : System.currentTimeMillis();
            List<BlacklistEntry> updated = new ArrayList<>();
            for (BlacklistEntry entry : entries) {
                if (entry.packageName.equals(packageName)) {
                    existed = true;
                    updated.add(new BlacklistEntry(
                            packageName,
                            label,
                            entry.addedAt,
                            overrideExistingMode ? newMode : entry.mode));
                } else {
                    updated.add(entry);
                }
            }
            if (!existed) {
                if (updated.size() >= MAX_ENTRIES) {
                    return false;
                }
                updated.add(new BlacklistEntry(packageName, label, effectiveAddedAt, newMode));
            }
            save(updated);
            return !existed;
        }
    }

    public void setMode(String packageName, String mode) {
        synchronized (LOCK) {
            boolean changed = false;
            List<BlacklistEntry> updated = new ArrayList<>();
            for (BlacklistEntry entry : getAll()) {
                if (entry.packageName.equals(packageName)) {
                    changed = true;
                    updated.add(new BlacklistEntry(
                            entry.packageName, entry.label, entry.addedAt, mode));
                } else {
                    updated.add(entry);
                }
            }
            if (changed) {
                save(updated);
            }
        }
    }

    public void remove(String packageName) {
        synchronized (LOCK) {
            List<BlacklistEntry> updated = new ArrayList<>();
            for (BlacklistEntry entry : getAll()) {
                if (!entry.packageName.equals(packageName)) {
                    updated.add(entry);
                }
            }
            save(updated);
        }
    }

    private void save(List<BlacklistEntry> entries) {
        JSONArray array = new JSONArray();
        for (BlacklistEntry entry : entries) {
            JSONObject item = new JSONObject();
            try {
                item.put("package", entry.packageName);
                item.put("label", entry.label);
                item.put("addedAt", entry.addedAt);
                item.put("mode", entry.mode);
                array.put(item);
            } catch (Exception ignored) {
                // JSONObject.put with string/long values cannot fail in practice.
            }
        }
        prefs().edit().putString(KEY, array.toString()).apply();
    }
}
