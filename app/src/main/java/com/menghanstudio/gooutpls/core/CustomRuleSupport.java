package com.menghanstudio.gooutpls.core;

import android.content.Context;
import android.util.Log;

/**
 * Optional bridge to the Lua rule engine.
 *
 * <p>The Lua runtime is deliberately not touched when there are no enabled
 * rules. This keeps the fixed baseline available on devices/ROMs where the
 * optional third-party runtime is unavailable or blocked, and avoids loading
 * Lua classes during a fresh install's first-frame startup.</p>
 */
public final class CustomRuleSupport {

    private static final String TAG = "FuckOffRules";

    private CustomRuleSupport() {
    }

    public static AppSafetyGuard.CustomProtection create(Context context) {
        try {
            if (new RuleRepository(context).getEnabled().isEmpty()) {
                return null;
            }
            return RuleEngine.create(context);
        } catch (Throwable t) {
            Log.w(TAG, "Custom rules unavailable; baseline protection remains", t);
            return null;
        }
    }
}
