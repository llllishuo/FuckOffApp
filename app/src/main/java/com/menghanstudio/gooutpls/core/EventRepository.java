package com.menghanstudio.gooutpls.core;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class EventRepository {

    public static final String ACTION_EVENTS_CHANGED =
            "com.menghanstudio.gooutpls.action.EVENTS_CHANGED";

    public static final String TYPE_ADD = "add";
    public static final String TYPE_REMOVE = "remove";
    public static final String TYPE_AUTO_SUCCESS = "auto_success";
    public static final String TYPE_AUTO_FAIL = "auto_fail";
    public static final String TYPE_MANUAL_PROMPT = "manual_prompt";
    public static final String TYPE_MANUAL_SUCCESS = "manual_success";
    public static final String TYPE_MANUAL_CANCEL = "manual_cancel";
    public static final String TYPE_MONITOR_ON = "monitor_on";
    public static final String TYPE_MONITOR_OFF = "monitor_off";
    public static final String TYPE_OWNER_READY = "owner_ready";
    public static final String TYPE_OWNER_LOST = "owner_lost";

    private static final String FILE = "gooutpls_events";
    private static final String KEY = "events_v1";
    private static final int MAX_EVENTS = 80;
    private static final Object LOCK = new Object();

    private EventRepository() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static void add(Context context, String type, String packageName, String message) {
        Context app = context.getApplicationContext();
        synchronized (LOCK) {
            JSONArray array = read(app);
            try {
                JSONObject item = new JSONObject();
                item.put("time", System.currentTimeMillis());
                item.put("type", type);
                item.put("package", packageName == null ? "" : packageName);
                item.put("message", message);
                array.put(item);
                while (array.length() > MAX_EVENTS) {
                    array.remove(0);
                }
            } catch (Exception ignored) {
                return;
            }
            prefs(app).edit().putString(KEY, array.toString()).apply();
        }
        app.sendBroadcast(new Intent(ACTION_EVENTS_CHANGED).setPackage(app.getPackageName()));
    }

    public static List<EventEntry> getRecent(Context context, int limit) {
        synchronized (LOCK) {
            JSONArray array = read(context.getApplicationContext());
            List<EventEntry> events = new ArrayList<>();
            int count = 0;
            for (int i = array.length() - 1; i >= 0 && count < limit; i--) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                events.add(new EventEntry(
                        item.optLong("time", 0L),
                        item.optString("type", ""),
                        item.optString("package", ""),
                        item.optString("message", "")));
                count++;
            }
            return events;
        }
    }

    public static int countSuccessfulRemovals(Context context) {
        synchronized (LOCK) {
            JSONArray array = read(context.getApplicationContext());
            int count = 0;
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String type = item.optString("type", "");
                if (TYPE_AUTO_SUCCESS.equals(type) || TYPE_MANUAL_SUCCESS.equals(type)) {
                    count++;
                }
            }
            return count;
        }
    }

    public static void clear(Context context) {
        Context app = context.getApplicationContext();
        synchronized (LOCK) {
            prefs(app).edit().remove(KEY).apply();
        }
        app.sendBroadcast(new Intent(ACTION_EVENTS_CHANGED).setPackage(app.getPackageName()));
    }

    private static JSONArray read(Context context) {
        String raw = prefs(context).getString(KEY, "[]");
        try {
            return new JSONArray(raw);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }
}
