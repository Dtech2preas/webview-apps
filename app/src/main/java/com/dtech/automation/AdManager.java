package com.dtech.automation;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Random;

public class AdManager {

    private static final String PREFS_NAME = "AdPrefs";
    private static final String KEY_NEXT_AD_TIME = "next_ad_timestamp";
    private static final long INITIAL_INTERVAL = 30 * 60 * 1000; // 30 minutes

    // Ad URLs
    private static final String[] AD_URLS = {
            "https://otieu.com/4/10205357",
            "https://otieu.com/4/9515888",
            "https://otieu.com/4/10250311"
    };

    public static long getNextAdTime(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // If not set, default to now + 30 mins
        long saved = prefs.getLong(KEY_NEXT_AD_TIME, 0);
        if (saved == 0) {
            saved = System.currentTimeMillis() + INITIAL_INTERVAL;
            setNextAdTime(context, saved);
        }
        return saved;
    }

    public static void setNextAdTime(Context context, long timestamp) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_NEXT_AD_TIME, timestamp).apply();
    }

    public static String getRandomAdUrl() {
        return AD_URLS[new Random().nextInt(AD_URLS.length)];
    }

    public static void addRewardTime(Context context) {
        long now = System.currentTimeMillis();
        long target = getNextAdTime(context);

        // If target is in the past (auto ad overdue), base buffer on Now.
        if (target < now) {
            target = now;
        }

        long currentBuffer = target - now;
        long reward;

        // Tiers
        long hours14 = 14 * 60 * 60 * 1000L;
        long hours28 = 28 * 60 * 60 * 1000L;

        if (currentBuffer > hours28) {
            reward = 15 * 60 * 1000L; // 15 mins
        } else if (currentBuffer > hours14) {
            reward = 30 * 60 * 1000L; // 30 mins
        } else {
            reward = 50 * 60 * 1000L; // 50 mins
        }

        setNextAdTime(context, target + reward);
    }

    public static void resetAutoAdTimer(Context context) {
        setNextAdTime(context, System.currentTimeMillis() + INITIAL_INTERVAL);
    }
}
