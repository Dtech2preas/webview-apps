package com.dtech.automation;

import android.content.Context;
import android.content.SharedPreferences;

public class QuotaManager {

    private static final String PREF_NAME = "DTechQuotaPrefs";
    private static final String KEY_QUOTA = "remaining_quota";
    private static final String KEY_INITIALIZED = "quota_initialized";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void initializeIfNeeded(Context context) {
        SharedPreferences prefs = getPrefs(context);
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            // Give 50 free tests on first install
            prefs.edit()
                 .putInt(KEY_QUOTA, 50)
                 .putBoolean(KEY_INITIALIZED, true)
                 .apply();
        }
    }

    public static int getQuota(Context context) {
        initializeIfNeeded(context);
        return getPrefs(context).getInt(KEY_QUOTA, 0);
    }

    public static boolean deductQuota(Context context) {
        int current = getQuota(context);
        if (current > 0) {
            getPrefs(context).edit().putInt(KEY_QUOTA, current - 1).apply();
            return true;
        }
        return false;
    }

    public static void addQuota(Context context, int amountToAdd) {
        int current = getQuota(context);
        getPrefs(context).edit().putInt(KEY_QUOTA, current + amountToAdd).apply();
    }
}
