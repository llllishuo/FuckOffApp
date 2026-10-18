package com.menghanstudio.gooutpls.core;

public final class EventEntry {
    public final long time;
    public final String type;
    public final String packageName;
    public final String message;

    public EventEntry(long time, String type, String packageName, String message) {
        this.time = time;
        this.type = type;
        this.packageName = packageName;
        this.message = message;
    }
}
