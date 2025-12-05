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

public class ServiceRepository {
    private static final String PREFS_NAME = "ServicePrefs";
    private static final String KEY_SERVICES = "services_list";
    private static final String KEY_LAST_SERVICE_ID = "last_service_id";
    private static final String TAG = "ServiceRepo";

    private final Context context;
    private List<ServiceData> services = new ArrayList<>();

    public ServiceRepository(Context context) {
        this.context = context;
        loadServices();
    }

    private void loadServices() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_SERVICES, "[]");
        services.clear();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                services.add(ServiceData.fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error loading services", e);
        }
    }

    public void saveServices() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        JSONArray array = new JSONArray();
        for (ServiceData service : services) {
            try {
                array.put(service.toJson());
            } catch (JSONException e) {
                Log.e(TAG, "Error saving service", e);
            }
        }
        prefs.edit().putString(KEY_SERVICES, array.toString()).apply();
    }

    public List<ServiceData> getAllServices() {
        return Collections.unmodifiableList(services);
    }

    public void addOrUpdateService(ServiceData service) {
        // Remove existing with same ID if exists
        for (int i = 0; i < services.size(); i++) {
            if (services.get(i).getId().equals(service.getId())) {
                services.set(i, service);
                saveServices();
                return;
            }
        }
        services.add(service);
        saveServices();
    }

    public void deleteService(String id) {
        for (int i = 0; i < services.size(); i++) {
            if (services.get(i).getId().equals(id)) {
                services.remove(i);
                saveServices();
                return;
            }
        }
    }

    public ServiceData getServiceById(String id) {
        for (ServiceData s : services) {
            if (s.getId().equals(id)) return s;
        }
        return null;
    }

    public void setLastUsedServiceId(String id) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_LAST_SERVICE_ID, id).apply();
    }

    public String getLastUsedServiceId() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LAST_SERVICE_ID, null);
    }

    public static class ServiceData {
        private String id;
        private String name;
        private String loginUrl;
        private String successUrl;
        private List<String> failureKeywords;
        private String scriptJson; // Stored as string to avoid repeated parsing

        public ServiceData(String id, String name, String loginUrl) {
            this.id = id;
            this.name = name;
            this.loginUrl = loginUrl;
            this.failureKeywords = new ArrayList<>();
            this.scriptJson = "[]";
        }

        public JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("id", id);
            obj.put("name", name);
            obj.put("loginUrl", loginUrl);
            obj.put("successUrl", successUrl);
            obj.put("scriptJson", scriptJson);

            JSONArray keywords = new JSONArray();
            for(String k : failureKeywords) keywords.put(k);
            obj.put("failureKeywords", keywords);

            return obj;
        }

        public static ServiceData fromJson(JSONObject obj) throws JSONException {
            ServiceData s = new ServiceData(
                    obj.getString("id"),
                    obj.getString("name"),
                    obj.getString("loginUrl")
            );
            if (obj.has("successUrl")) s.successUrl = obj.getString("successUrl");
            if (obj.has("scriptJson")) s.scriptJson = obj.getString("scriptJson");

            if (obj.has("failureKeywords")) {
                JSONArray k = obj.getJSONArray("failureKeywords");
                for (int i = 0; i < k.length(); i++) {
                    s.failureKeywords.add(k.getString(i));
                }
            }
            return s;
        }

        // Getters and Setters
        public String getId() { return id; }
        public String getName() { return name; }
        public String getLoginUrl() { return loginUrl; }
        public String getSuccessUrl() { return successUrl; }
        public void setSuccessUrl(String url) { this.successUrl = url; }
        public List<String> getFailureKeywords() { return failureKeywords; }
        public void setFailureKeywords(List<String> keywords) { this.failureKeywords = keywords; }
        public String getScriptJson() { return scriptJson; }
        public void setScriptJson(String json) { this.scriptJson = json; }
    }
}
