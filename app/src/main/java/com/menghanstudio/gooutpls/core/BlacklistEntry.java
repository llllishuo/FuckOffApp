package com.menghanstudio.gooutpls.core;

public final class BlacklistEntry {

    /** Follow the global auto-uninstall setting. */
    public static final String MODE_SYSTEM = "system";
    /** Always use the manual system uninstall confirmation. */
    public static final String MODE_MANUAL = "manual";

    public final String packageName;
    public final String label;
    public final long addedAt;
    public final String mode;

    public BlacklistEntry(String packageName, String label, long addedAt) {
        this(packageName, label, addedAt, MODE_SYSTEM);
    }

    public BlacklistEntry(String packageName, String label, long addedAt, String mode) {
        this.packageName = packageName;
        this.label = label == null ? packageName : label;
        this.addedAt = addedAt;
        this.mode = MODE_MANUAL.equals(mode) ? MODE_MANUAL : MODE_SYSTEM;
    }

    public boolean followsSystem() {
        return MODE_SYSTEM.equals(mode);
    }

    public boolean isManual() {
        return MODE_MANUAL.equals(mode);
    }

    public String displayLabel() {
        return label == null || label.trim().isEmpty() ? packageName : label;
    }
}
