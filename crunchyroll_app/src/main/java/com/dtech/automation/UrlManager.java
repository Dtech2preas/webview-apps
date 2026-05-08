package com.dtech.automation;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class UrlManager {

    private static final String PREFS_NAME = "UrlManagerPrefs";
    private static final String KEY_URLS = "saved_urls";
    private SharedPreferences prefs;

    public UrlManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static class UrlItem {
        public String url;
        public long lastRunTimestamp;
        public int retryCount = 0; // Transient

        public UrlItem(String url, long lastRunTimestamp) {
            this.url = url;
            this.lastRunTimestamp = lastRunTimestamp;
        }

        public boolean isDue() {
            // 24 hours in milliseconds = 24 * 60 * 60 * 1000 = 86400000
            return (System.currentTimeMillis() - lastRunTimestamp) > 86400000;
        }
    }

    public List<UrlItem> getUrls() {
        List<UrlItem> list = new ArrayList<>();
        String json = prefs.getString(KEY_URLS, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                list.add(new UrlItem(
                    obj.getString("url"),
                    obj.optLong("lastRunTimestamp", 0)
                ));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void addUrl(String url) {
        List<UrlItem> list = getUrls();
        // Check duplicate
        for (UrlItem item : list) {
            if (item.url.equals(url)) return;
        }
        list.add(new UrlItem(url, 0)); // Never run
        saveUrls(list);
    }

    public void deleteUrl(String url) {
        List<UrlItem> list = getUrls();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).url.equals(url)) {
                list.remove(i);
                break;
            }
        }
        saveUrls(list);
    }

    public void updateLastRun(String url) {
        List<UrlItem> list = getUrls();
        boolean changed = false;
        for (UrlItem item : list) {
            if (item.url.equals(url)) {
                item.lastRunTimestamp = System.currentTimeMillis();
                changed = true;
                break;
            }
        }
        if (changed) saveUrls(list);
    }

    private void saveUrls(List<UrlItem> list) {
        JSONArray array = new JSONArray();
        try {
            for (UrlItem item : list) {
                JSONObject obj = new JSONObject();
                obj.put("url", item.url);
                obj.put("lastRunTimestamp", item.lastRunTimestamp);
                array.put(obj);
            }
            prefs.edit().putString(KEY_URLS, array.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
