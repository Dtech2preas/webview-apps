package com.dtech.automation;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.content.res.AssetManager;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;

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
        boolean preloaded = prefs.getBoolean("preloaded_services_installed_v1", false);

        if (!preloaded) {
            loadPreloadedServicesFromAssets();
            prefs.edit().putBoolean("preloaded_services_installed_v1", true).apply();
        }

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

    private void loadPreloadedServicesFromAssets() {
        try {
            AssetManager assetManager = context.getAssets();
            String[] files = assetManager.list("preloaded_services");
            if (files != null) {
                // To avoid duplicate appends on first launch (since loadServices also loads from prefs after this),
                // we'll actually read the current prefs string, parse it, append, and save it,
                // OR we just parse them, add them to `services`, and then `saveServices()` at the end.
                // Wait, since this is the first run, `services` should be empty.
                for (String filename : files) {
                    if (filename.endsWith(".json")) {
                        try {
                            InputStream is = assetManager.open("preloaded_services/" + filename);
                            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                sb.append(line).append("\n");
                            }
                            reader.close();

                            JSONObject obj = new JSONObject(sb.toString());
                            ServiceData s = ServiceData.fromJson(obj);
                            // Ensure it's not a duplicate if already somehow exists
                            boolean exists = false;
                            for (ServiceData existing : services) {
                                if (existing.getId().equals(s.getId())) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) {
                                services.add(s);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error loading preloaded service " + filename, e);
                        }
                    }
                }
                saveServices(); // save the newly added ones
            }
        } catch (Exception e) {
            Log.e(TAG, "Error listing preloaded_services", e);
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

    public void updateServiceRemote(ServiceData service) {
        // Update existing by name (override), or add if not found
        for (int i = 0; i < services.size(); i++) {
            if (services.get(i).getName().equals(service.getName())) {
                // Keep the same ID so selection logic doesn't break
                service.id = services.get(i).getId();
                services.set(i, service);
                saveServices();
                return;
            }
        }
        // If not found by name, just add it
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

    public String exportService(String id) {
        ServiceData s = getServiceById(id);
        if (s == null) return null;
        try {
            return s.toJson().toString(2);
        } catch (JSONException e) { return null; }
    }

    public boolean importService(String jsonString) {
        try {
            JSONObject obj = new JSONObject(jsonString);
            ServiceData s = ServiceData.fromJson(obj);
            // Regenerate ID to avoid conflicts or keep it? User wants to import "a service".
            // If I import, I probably want a copy.
            // Let's modify the ID and append "(Imported)" to name to be safe.
            s.id = java.util.UUID.randomUUID().toString();
            s.name = s.name + " (Imported)";
            addOrUpdateService(s);
            return true;
        } catch (JSONException e) {
            return false;
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
        private String forceRedirectUrl;
        private String successValidationUrl; // Method A URL-based success verification
        private String successSelector;
        private List<String> successKeywords;
        private List<String> failureKeywords;
        private List<ExtractionPoint> extractionPoints;
        private String scriptJson; // Stored as string to avoid repeated parsing
        private String userAgent;
        private boolean useOcrForSuccess = false;

        // OCR Validation Fields
        private String successOcrText;
        private float successOcrX, successOcrY, successOcrW, successOcrH;

        public ServiceData(String id, String name, String loginUrl) {
            this.id = id;
            this.name = name;
            this.loginUrl = loginUrl;
            this.successKeywords = new ArrayList<>();
            this.failureKeywords = new ArrayList<>();
            this.extractionPoints = new ArrayList<>();
            this.scriptJson = "[]";
            this.userAgent = "";
        }

        public JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("id", id);
            obj.put("name", name);
            obj.put("loginUrl", loginUrl);
            obj.put("successUrl", successUrl);
            obj.put("forceRedirectUrl", forceRedirectUrl);
            obj.put("successValidationUrl", successValidationUrl);
            obj.put("successSelector", successSelector);
            obj.put("scriptJson", scriptJson);
            obj.put("userAgent", userAgent);
            obj.put("useOcrForSuccess", useOcrForSuccess);

            if (successOcrText != null) {
                obj.put("successOcrText", successOcrText);
                obj.put("successOcrX", (double)successOcrX);
                obj.put("successOcrY", (double)successOcrY);
                obj.put("successOcrW", (double)successOcrW);
                obj.put("successOcrH", (double)successOcrH);
            }

            JSONArray sKeywords = new JSONArray();
            for(String k : successKeywords) sKeywords.put(k);
            obj.put("successKeywords", sKeywords);

            JSONArray keywords = new JSONArray();
            for(String k : failureKeywords) keywords.put(k);
            obj.put("failureKeywords", keywords);

            JSONArray ePoints = new JSONArray();
            for(ExtractionPoint p : extractionPoints) ePoints.put(p.toJson());
            obj.put("extractionPoints", ePoints);

            return obj;
        }

        public static ServiceData fromJson(JSONObject obj) throws JSONException {
            ServiceData s = new ServiceData(
                    obj.getString("id"),
                    obj.getString("name"),
                    obj.getString("loginUrl")
            );
            if (obj.has("successUrl")) s.successUrl = obj.getString("successUrl");
            if (obj.has("forceRedirectUrl")) s.forceRedirectUrl = obj.getString("forceRedirectUrl");
            if (obj.has("successValidationUrl")) s.successValidationUrl = obj.getString("successValidationUrl");
            if (obj.has("successSelector")) s.successSelector = obj.getString("successSelector");
            if (obj.has("scriptJson")) s.scriptJson = obj.getString("scriptJson");
            if (obj.has("userAgent")) s.userAgent = obj.getString("userAgent");
            if (obj.has("useOcrForSuccess")) s.useOcrForSuccess = obj.getBoolean("useOcrForSuccess");

            if (obj.has("successOcrText")) {
                s.successOcrText = obj.getString("successOcrText");
                s.successOcrX = (float)obj.optDouble("successOcrX", 0);
                s.successOcrY = (float)obj.optDouble("successOcrY", 0);
                s.successOcrW = (float)obj.optDouble("successOcrW", 0);
                s.successOcrH = (float)obj.optDouble("successOcrH", 0);
            }

            if (obj.has("successKeywords")) {
                JSONArray k = obj.getJSONArray("successKeywords");
                for (int i = 0; i < k.length(); i++) {
                    s.successKeywords.add(k.getString(i));
                }
            }

            if (obj.has("failureKeywords")) {
                JSONArray k = obj.getJSONArray("failureKeywords");
                for (int i = 0; i < k.length(); i++) {
                    s.failureKeywords.add(k.getString(i));
                }
            }

            if (obj.has("extractionPoints")) {
                JSONArray k = obj.getJSONArray("extractionPoints");
                for (int i = 0; i < k.length(); i++) {
                    s.extractionPoints.add(ExtractionPoint.fromJson(k.getJSONObject(i)));
                }
            }
            return s;
        }

        // Getters and Setters
        public String getId() { return id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getLoginUrl() { return loginUrl; }
        public String getSuccessUrl() { return successUrl; }
        public void setSuccessUrl(String url) { this.successUrl = url; }
        public String getForceRedirectUrl() { return forceRedirectUrl; }
        public void setForceRedirectUrl(String url) { this.forceRedirectUrl = url; }
        public String getSuccessValidationUrl() { return successValidationUrl; }
        public void setSuccessValidationUrl(String url) { this.successValidationUrl = url; }
        public String getSuccessSelector() { return successSelector; }
        public void setSuccessSelector(String selector) { this.successSelector = selector; }
        public List<String> getSuccessKeywords() { return successKeywords; }
        public void setSuccessKeywords(List<String> keywords) { this.successKeywords = keywords; }
        public List<String> getFailureKeywords() { return failureKeywords; }
        public void setFailureKeywords(List<String> keywords) { this.failureKeywords = keywords; }
        public List<ExtractionPoint> getExtractionPoints() { return extractionPoints; }
        public void setExtractionPoints(List<ExtractionPoint> points) { this.extractionPoints = points; }
        public String getScriptJson() { return scriptJson; }
        public void setScriptJson(String json) { this.scriptJson = json; }
        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String ua) { this.userAgent = ua; }

        public boolean isUseOcrForSuccess() { return useOcrForSuccess; }
        public void setUseOcrForSuccess(boolean useOcr) { this.useOcrForSuccess = useOcr; }

        public String getSuccessOcrText() { return successOcrText; }
        public void setSuccessOcrText(String text) { this.successOcrText = text; }
        public float getSuccessOcrX() { return successOcrX; }
        public float getSuccessOcrY() { return successOcrY; }
        public float getSuccessOcrW() { return successOcrW; }
        public float getSuccessOcrH() { return successOcrH; }
        public void setSuccessOcrRect(float x, float y, float w, float h) {
            this.successOcrX = x;
            this.successOcrY = y;
            this.successOcrW = w;
            this.successOcrH = h;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static class ExtractionPoint {
        private String selector;
        private String label;
        private boolean isDynamic;
        private String pattern;
        // OCR Coordinates (Percentages of WebView)
        private float rectX;
        private float rectY;
        private float rectWidth;
        private float rectHeight;

        public ExtractionPoint(String selector, String label, boolean isDynamic, String pattern) {
            this(selector, label, isDynamic, pattern, 0, 0, 0, 0);
        }

        public ExtractionPoint(String selector, String label, boolean isDynamic, String pattern, float x, float y, float w, float h) {
            this.selector = selector;
            this.label = label;
            this.isDynamic = isDynamic;
            this.pattern = pattern;
            this.rectX = x;
            this.rectY = y;
            this.rectWidth = w;
            this.rectHeight = h;
        }

        public String getSelector() { return selector; }
        public String getLabel() { return label; }
        public boolean isDynamic() { return isDynamic; }
        public String getPattern() { return pattern; }
        public float getRectX() { return rectX; }
        public float getRectY() { return rectY; }
        public float getRectWidth() { return rectWidth; }
        public float getRectHeight() { return rectHeight; }
        public boolean isOcr() { return rectWidth > 0; }

        public JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("selector", selector);
            obj.put("label", label);
            obj.put("isDynamic", isDynamic);
            obj.put("pattern", pattern);
            obj.put("rectX", (double)rectX);
            obj.put("rectY", (double)rectY);
            obj.put("rectWidth", (double)rectWidth);
            obj.put("rectHeight", (double)rectHeight);
            return obj;
        }

        public static ExtractionPoint fromJson(JSONObject obj) throws JSONException {
            return new ExtractionPoint(
                    obj.optString("selector", ""),
                    obj.getString("label"),
                    obj.optBoolean("isDynamic", false),
                    obj.optString("pattern", ""),
                    (float)obj.optDouble("rectX", 0),
                    (float)obj.optDouble("rectY", 0),
                    (float)obj.optDouble("rectWidth", 0),
                    (float)obj.optDouble("rectHeight", 0)
            );
        }
    }
}
