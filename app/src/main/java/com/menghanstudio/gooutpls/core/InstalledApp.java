package com.menghanstudio.gooutpls.core;

import android.graphics.drawable.Drawable;

import java.util.Locale;

public final class InstalledApp {
    public final String packageName;
    public final String label;
    public final Drawable icon;

    public InstalledApp(String packageName, String label, Drawable icon) {
        this.packageName = packageName;
        this.label = label;
        this.icon = icon;
    }

    public String displayLabel() {
        return label == null || label.trim().isEmpty() ? packageName : label;
    }

    public boolean matches(String query) {
        if (query == null || query.trim().isEmpty()) {
            return true;
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        return displayLabel().toLowerCase(Locale.ROOT).contains(q)
                || packageName.toLowerCase(Locale.ROOT).contains(q);
    }
}
