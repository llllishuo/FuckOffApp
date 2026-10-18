package com.menghanstudio.gooutpls.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * JSON transport format for the blacklist.
 *
 * <pre>
 * {
 *   "app": "FuckOff",
 *   "schema": "fuckoff.blacklist",
 *   "version": 1,
 *   "exportedAt": 1792334013758,
 *   "count": 1,
 *   "entries": [
 *     {"packageName":"com.example.app","label":"Example","addedAt":1792334013758}
 *   ]
 * }
 * </pre>
 *
 * <p>Importing is deliberately forgiving about the surrounding object so a
 * hand-written array of package names or objects also works. Entries are
 * deduplicated and checked before they can reach the uninstall engine.</p>
 */
public final class BlacklistJsonCodec {

    public static final String SCHEMA = "fuckoff.blacklist";
    public static final int VERSION = 1;

    private static final int MAX_SOURCE_CHARS = 2 * 1024 * 1024;
    private static final int MAX_ENTRIES = BlacklistRepository.MAX_ENTRIES;
    private static final int MAX_LABEL_LENGTH = 160;
    private static final Pattern PACKAGE_NAME = Pattern.compile(
            "^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$");

    private BlacklistJsonCodec() {
    }

    public static String encode(List<BlacklistEntry> entries) {
        try {
            JSONObject root = new JSONObject();
            root.put("app", "FuckOff");
            root.put("schema", SCHEMA);
            root.put("version", VERSION);
            root.put("exportedAt", System.currentTimeMillis());
            root.put("count", entries.size());

            JSONArray array = new JSONArray();
            for (BlacklistEntry entry : entries) {
                JSONObject item = new JSONObject();
                item.put("packageName", entry.packageName);
                item.put("label", entry.displayLabel());
                item.put("addedAt", entry.addedAt);
                item.put("mode", entry.mode);
                array.put(item);
            }
            root.put("entries", array);
            return root.toString(2);
        } catch (JSONException e) {
            throw new IllegalStateException("Unable to encode blacklist", e);
        }
    }

    /**
     * @throws JSONException if the file is not JSON
     * @throws IllegalArgumentException if it is structurally unusable or too large
     */
    public static ImportResult decode(String raw) throws JSONException {
        if (raw == null) {
            throw new IllegalArgumentException("empty");
        }
        String text = raw;
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        text = text.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("empty");
        }
        if (text.length() > MAX_SOURCE_CHARS) {
            throw new IllegalArgumentException("too_large");
        }

        JSONArray source;
        if (text.charAt(0) == '[') {
            source = new JSONArray(text);
        } else {
            JSONObject root = new JSONObject(text);
            source = root.optJSONArray("entries");
            if (source == null) {
                source = root.optJSONArray("blacklist");
            }
            if (source == null) {
                throw new IllegalArgumentException("missing_entries");
            }
        }

        if (source.length() > MAX_ENTRIES) {
            throw new IllegalArgumentException("too_many_entries");
        }

        long now = System.currentTimeMillis();
        int invalid = 0;
        int duplicates = 0;
        Map<String, BlacklistEntry> unique = new LinkedHashMap<>();

        for (int i = 0; i < source.length(); i++) {
            String packageName = null;
            String label = null;
            long addedAt = now;

            Object value = source.opt(i);
            if (value instanceof String) {
                packageName = ((String) value).trim();
                label = packageName;
            } else if (value instanceof JSONObject) {
                JSONObject item = (JSONObject) value;
                packageName = item.optString("packageName",
                        item.optString("package", item.optString("pkg", ""))).trim();
                label = item.optString("label", item.optString("name", "")).trim();
                addedAt = item.optLong("addedAt", now);
            }

            if (!isValidPackageName(packageName)
                    || AppCatalog.isProtectedPackageName(packageName)) {
                invalid++;
                continue;
            }
            if (label == null || label.isEmpty()) {
                label = packageName;
            }
            if (label.length() > MAX_LABEL_LENGTH) {
                label = label.substring(0, MAX_LABEL_LENGTH);
            }
            if (addedAt <= 0L || addedAt > now + 86_400_000L) {
                addedAt = now;
            }

            if (unique.containsKey(packageName)) {
                duplicates++;
                continue;
            }
            // Deliberately ignore any "mode" in the file: an imported entry
            // must never immediately participate in automatic uninstall.
            unique.put(packageName, new BlacklistEntry(
                    packageName, label, addedAt, BlacklistEntry.MODE_MANUAL));
        }

        return new ImportResult(
                new ArrayList<>(unique.values()),
                source.length(),
                invalid,
                duplicates);
    }

    private static boolean isValidPackageName(String packageName) {
        return packageName != null
                && packageName.length() >= 3
                && packageName.length() <= 255
                && PACKAGE_NAME.matcher(packageName).matches();
    }

    public static final class ImportResult {
        public final List<BlacklistEntry> entries;
        public final int sourceCount;
        public final int invalidCount;
        public final int duplicateCount;

        ImportResult(List<BlacklistEntry> entries, int sourceCount,
                     int invalidCount, int duplicateCount) {
            this.entries = entries;
            this.sourceCount = sourceCount;
            this.invalidCount = invalidCount;
            this.duplicateCount = duplicateCount;
        }

        public int ignoredCount() {
            return invalidCount + duplicateCount;
        }
    }
}
