package com.menghanstudio.gooutpls.core;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Locally stored user rules. Rules can only add protection; there is
 * deliberately no "allow/unprotect" action that could bypass the fixed
 * baseline checks.
 */
public final class RuleRepository {

    public static final int MAX_RULES = 50;
    public static final int MAX_NAME_LENGTH = 60;
    public static final int MAX_EXPRESSION_LENGTH = 800;

    private static final String FILE = "gooutpls_rules";
    private static final String KEY = "rules_v1";
    private static final Object LOCK = new Object();

    private final Context context;

    public RuleRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public List<Rule> getAll() {
        synchronized (LOCK) {
            List<Rule> result = new ArrayList<>();
            String raw = prefs().getString(KEY, "[]");
            try {
                JSONArray array = new JSONArray(raw);
                for (int i = 0; i < array.length() && i < MAX_RULES; i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    String id = item.optString("id", "");
                    String name = item.optString("name", "");
                    String expression = item.optString("expr", "");
                    if (id.isEmpty() || expression.trim().isEmpty()) {
                        continue;
                    }
                    boolean enabled = item.optBoolean("enabled", true);
                    long createdAt = item.optLong("createdAt", 0L);
                    result.add(new Rule(id, name, expression, enabled, createdAt));
                }
            } catch (Exception ignored) {
                // Corrupt rules are skipped; baseline protection still applies.
            }
            return result;
        }
    }

    public List<Rule> getEnabled() {
        List<Rule> result = new ArrayList<>();
        for (Rule rule : getAll()) {
            if (rule.enabled) {
                result.add(rule);
            }
        }
        return result;
    }

    public int count() {
        return getAll().size();
    }

    public Rule find(String id) {
        if (id == null) {
            return null;
        }
        for (Rule rule : getAll()) {
            if (id.equals(rule.id)) {
                return rule;
            }
        }
        return null;
    }

    /**
     * Inserts a new rule or updates an existing one.
     *
     * @return false when the rule limit has been reached for a new rule
     */
    public boolean upsert(Rule rule) {
        if (rule == null || rule.id == null || rule.expression.trim().isEmpty()) {
            return false;
        }
        synchronized (LOCK) {
            List<Rule> rules = getAll();
            boolean existed = false;
            List<Rule> updated = new ArrayList<>();
            for (Rule existing : rules) {
                if (existing.id.equals(rule.id)) {
                    existed = true;
                    updated.add(rule);
                } else {
                    updated.add(existing);
                }
            }
            if (!existed) {
                if (updated.size() >= MAX_RULES) {
                    return false;
                }
                updated.add(rule);
            }
            save(updated);
            return true;
        }
    }

    public void setEnabled(String id, boolean enabled) {
        synchronized (LOCK) {
            List<Rule> rules = getAll();
            List<Rule> updated = new ArrayList<>();
            for (Rule rule : rules) {
                if (rule.id.equals(id)) {
                    updated.add(new Rule(rule.id, rule.name, rule.expression,
                            enabled, rule.createdAt));
                } else {
                    updated.add(rule);
                }
            }
            save(updated);
        }
    }

    public void delete(String id) {
        synchronized (LOCK) {
            List<Rule> updated = new ArrayList<>();
            for (Rule rule : getAll()) {
                if (!rule.id.equals(id)) {
                    updated.add(rule);
                }
            }
            save(updated);
        }
    }

    private void save(List<Rule> rules) {
        JSONArray array = new JSONArray();
        for (Rule rule : rules) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", rule.id);
                item.put("name", rule.name);
                item.put("expr", rule.expression);
                item.put("enabled", rule.enabled);
                item.put("createdAt", rule.createdAt);
                array.put(item);
            } catch (Exception ignored) {
                // String/boolean/long puts cannot fail in practice.
            }
        }
        prefs().edit().putString(KEY, array.toString()).apply();
    }

    public static final class Rule {
        public final String id;
        public final String name;
        public final String expression;
        public final boolean enabled;
        public final long createdAt;

        public Rule(String id, String name, String expression,
                    boolean enabled, long createdAt) {
            this.id = id;
            this.name = name == null ? "" : name;
            this.expression = expression == null ? "" : expression;
            this.enabled = enabled;
            this.createdAt = createdAt;
        }

        public Rule withEnabled(boolean enabled) {
            return new Rule(id, name, expression, enabled, createdAt);
        }
    }
}
