package com.dtech.automation;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BatchResultRepository {
    private static final String PREFS_NAME = "BatchHistoryPrefs";
    private static final String KEY_HISTORY = "history_list";

    private final Context context;
    private List<BatchRun> history = new ArrayList<>();

    public BatchResultRepository(Context context) {
        this.context = context;
        loadHistory();
    }

    private void loadHistory() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_HISTORY, "[]");
        history.clear();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                history.add(BatchRun.fromJson(array.getJSONObject(i)));
            }
            // Sort by latest first
            Collections.sort(history, (o1, o2) -> Long.compare(o2.timestamp, o1.timestamp));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void saveRun(BatchRun run) {
        history.add(0, run);
        if (history.size() > 50) history.remove(history.size() - 1); // Keep last 50
        saveToPrefs();
    }

    private void saveToPrefs() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        JSONArray array = new JSONArray();
        for (BatchRun run : history) {
            try {
                array.put(run.toJson());
            } catch (JSONException e) {}
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply();
    }

    public List<BatchRun> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public static class BatchRun {
        public long timestamp;
        public String serviceName;
        public int successCount;
        public int failureCount;
        public String resultFilePath; // We will save the detailed log to a unique file

        public BatchRun(long timestamp, String serviceName, int successCount, int failureCount, String resultFilePath) {
            this.timestamp = timestamp;
            this.serviceName = serviceName;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.resultFilePath = resultFilePath;
        }

        public JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("timestamp", timestamp);
            obj.put("serviceName", serviceName);
            obj.put("successCount", successCount);
            obj.put("failureCount", failureCount);
            obj.put("resultFilePath", resultFilePath);
            return obj;
        }

        public static BatchRun fromJson(JSONObject obj) throws JSONException {
            return new BatchRun(
                obj.getLong("timestamp"),
                obj.getString("serviceName"),
                obj.getInt("successCount"),
                obj.getInt("failureCount"),
                obj.optString("resultFilePath", "")
            );
        }
    }
}
