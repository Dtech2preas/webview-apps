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
        String b64 = "ewogICJpZCI6ICJiM2U5N2ViNC0zN2QzLTRiMTEtOGRiZS1jYmUxZTU4NDE5YzAiLAogICJuYW1lIjogIkNydW5jaHlyb2xsIiwKICAibG9naW5VcmwiOiAiaHR0cHM6Ly9zc28uY3J1bmNoeXJvbGwuY29tL2xvZ2luIiwKICAic3VjY2Vzc1VybCI6ICJodHRwczovL3d3dy5jcnVuY2h5cm9sbC5jb20vZGlzY292ZXIiLAogICJmb3JjZVJlZGlyZWN0VXJsIjogImh0dHBzOi8vd3d3LmNydW5jaHlyb2xsLmNvbS9hY2NvdW50L21lbWJlcnNoaXAiLAogICJzdWNjZXNzVmFsaWRhdGlvblVybCI6ICJodHRwczovL3d3dy5jcnVuY2h5cm9sbC5jb20vZGlzY292ZXIiLAogICJzY3JpcHRKc29uIjogIlt7XCJ0eXBlXCI6XCJjbGlja1wiLFwic2VsZWN0b3JcIjpcIltuYW1lPVxcXCJlbWFpbFxcXCJdXCIsXCJ4cGF0aFwiOlwiXFwvaHRtbFxcL2JvZHlcXC9kaXZbMl1cXC9kaXZcXC9kaXZbMl1cXC9kaXZbMl1cXC9tYWluXFwvZGl2XFwvZm9ybVxcL2RpdlsxXVxcL3NwYW5bMV1cXC9kaXZcXC9sYWJlbFxcL2lucHV0XCIsXCJ0aW1lXCI6MzQ4MCxcInVybFwiOlwiaHR0cHM6XFwvXFwvc3NvLmNydW5jaHlyb2xsLmNvbVxcL2xvZ2luXCIsXCJ2YWx1ZVwiOm51bGwsXCJ0YWdOYW1lXCI6XCJpbnB1dFwiLFwiaWRcIjpcIlwiLFwibmFtZVwiOlwiZW1haWxcIixcImNsYXNzTmFtZVwiOlwiaW5wdXQtLW1PbjY0IGVtYWlsLWlucHV0ZmllbGQtLUg0ZlJXXCIsXCJpbm5lclRleHRcIjpcIlwiLFwidGV4dENvbnRlbnRcIjpcIlwiLFwicGxhY2Vob2xkZXJcIjpcIlwiLFwiaHJlZlwiOlwiXCIsXCJpbnB1dFR5cGVcIjpcImVtYWlsXCIsXCJ4XCI6NzMsXCJ5XCI6MjIyLFwiY2xpZW50WFwiOjczLFwiY2xpZW50WVwiOjIyMixcInJlY3RcIjp7XCJ0b3BcIjoyMDMuOTg2OTIzMjE3NzczNDQsXCJsZWZ0XCI6MzkuOTk5OTk2MTg1MzAyNzM0LFwid2lkdGhcIjoyOTYuNDcwNTgxMDU0Njg3NSxcImhlaWdodFwiOjMzLjk4NjkyNzAzMjQ3MDd9fSx7XCJ0eXBlXCI6XCJpbnB1dFwiLFwic2VsZWN0b3JcIjpcIltuYW1lPVxcXCJlbWFpbFxcXCJdXCIsXCJ4cGF0aFwiOlwiXFwvaHRtbFxcL2JvZHlcXC9kaXZbMl1cXC9kaXZcXC9kaXZbMl1cXC9kaXZbMl1cXC9tYWluXFwvZGl2XFwvZm9ybVxcL2RpdlsxXVxcL3NwYW5bMV1cXC9kaXZcXC9sYWJlbFxcL2lucHV0XCIsXCJ0aW1lXCI6NzIyOSxcInVybFwiOlwiaHR0cHM6XFwvXFwvc3NvLmNydW5jaHlyb2xsLmNvbVxcL2xvZ2luXCIsXCJ2YWx1ZVwiOlwidGVzdGR4MjRAZ21haWwuY29tXCIsXCJ0YWdOYW1lXCI6XCJpbnB1dFwiLFwiaWRcIjpcIlwiLFwibmFtZVwiOlwiZW1haWxcIixcImNsYXNzTmFtZVwiOlwiaW5wdXQtLW1PbjY0IGVtYWlsLWlucHV0ZmllbGQtLUg0ZlJXXCIsXCJpbm5lclRleHRcIjpcIlwiLFwidGV4dENvbnRlbnRcIjpcIlwiLFwicGxhY2Vob2xkZXJcIjpcIlwiLFwiaHJlZlwiOlwiXCIsXCJpbnB1dFR5cGVcIjpcImVtYWlsXCJ9LHtcInR5cGVcIjpcImNsaWNrXCIsXCJzZWxlY3RvclwiOlwiW25hbWU9XFxcInBhc3N3b3JkXFxcIl1cIixcInhwYXRoXCI6XCJcXC9odG1sXFwvYm9keVxcL2RpdlsyXVxcL2RpdlxcL2RpdlsyXVxcL2RpdlsyXVxcL21haW5cXC9kaXZcXC9mb3JtXFwvZGl2WzFdXFwvc3BhblsyXVxcL2RpdlsyXVxcL2xhYmVsXFwvaW5wdXRcIixcInRpbWVcIjo3NjY0LFwidXJsXCI6XCJodHRwczpcXC9cXC9zc28uY3J1bmNoeXJvbGwuY29tXFwvbG9naW5cIixcInZhbHVlXCI6bnVsbCxcInRhZ05hbWVcIjpcImlucHV0XCIsXCJpZFwiOlwiXCIsXCJuYW1lXCI6XCJwYXNzd29yZFwiLFwiY2xhc3NOYW1lXCI6XCJpbnB1dC0tbU9uNjQgYmFzaWMtaW5wdXRmaWVsZC0tYlBrNTUgcGFzc3dvcmQtaW5wdXRmaWVsZC0tUWdvZTBcIixcImlubmVyVGV4dFwiOlwiXCIsXCJ0ZXh0Q29udGVudFwiOlwiXCIsXCJwbGFjZWhvbGRlclwiOlwiXCIsXCJocmVmXCI6XCJcIixcImlucHV0VHlwZVwiOlwicGFzc3dvcmRcIixcInhcIjo2MSxcInlcIjoyODcsXCJjbGllbnRYXCI6NjEsXCJjbGllbnRZXCI6Mjg3LFwicmVjdFwiOntcInRvcFwiOjI3OS45ODM2NDI1NzgxMjUsXCJsZWZ0XCI6MzkuOTk5OTk2MTg1MzAyNzM0LFwid2lkdGhcIjoyMzYuNDc4NzQ0NTA2ODM1OTQsXCJoZWlnaHRcIjozMy45ODY5MjcwMzI0NzA3fX0se1widHlwZVwiOlwiaW5wdXRcIixcInNlbGVjdG9yXCI6XCJbbmFtZT1cXFwicGFzc3dvcmRcXFwiXVwiLFwieHBhdGhcIjpcIlxcL2h0bWxcXC9ib2R5XFwvZGl2WzJdXFwvZGl2XFwvZGl2WzJdXFwvZGl2WzJdXFwvbWFpblxcL2RpdlxcL2Zvcm1cXC9kaXZbMV1cXC9zcGFuWzJdXFwvZGl2WzJdXFwvbGFiZWxcXC9pbnB1dFwiLFwidGltZVwiOjEwNjkyLFwidXJsXCI6XCJodHRwczpcXC9cXC9zc28uY3J1bmNoeXJvbGwuY29tXFwvbG9naW5cIixcInZhbHVlXCI6XCJMZWZhbGVmYWxlZmFAMVwiLFwidGFnTmFtZVwiOlwiaW5wdXRcIixcImlkXCI6XCJcIixcIm5hbWVcIjpcInBhc3N3b3JkXCIsXCJjbGFzc05hbWVcIjpcImlucHV0LS1tT242NCBiYXNpYy1pbnB1dGZpZWxkLS1iUGs1NSBwYXNzd29yZC1pbnB1dGZpZWxkLS1RZ29lMFwiLFwiaW5uZXJUZXh0XCI6XCJcIixcInRleHRDb250ZW50XCI6XCJcIixcInBsYWNlaG9sZGVyXCI6XCJcIixcImhyZWZcIjpcIlwiLFwiaW5wdXRUeXBlXCI6XCJwYXNzd29yZFwifSx7XCJ0eXBlXCI6XCJjbGlja1wiLFwic2VsZWN0b3JcIjpcImh0bWwgPiBib2R5ID4gZGl2Om50aC1vZi10eXBlKDIpID4gZGl2ID4gZGl2Om50aC1vZi10eXBlKDIpID4gZGl2Om50aC1vZi10eXBlKDIpID4gbWFpbiA+IGRpdiA+IGZvcm0gPiBkaXY6bnRoLW9mLXR5cGUoMikgPiBidXR0b25cIixcInhwYXRoXCI6XCJcXC9odG1sXFwvYm9keVxcL2RpdlsyXVxcL2RpdlxcL2RpdlsyXVxcL2RpdlsyXVxcL21haW5cXC9kaXZcXC9mb3JtXFwvZGl2WzJdXFwvYnV0dG9uXCIsXCJ0aW1lXCI6MTI3MzIsXCJ1cmxcIjpcImh0dHBzOlxcL1xcL3Nzby5jcnVuY2h5cm9sbC5jb21cXC9sb2dpblwiLFwidmFsdWVcIjpudWxsLFwidGFnTmFtZVwiOlwiYnV0dG9uXCIsXCJpZFwiOlwiXCIsXCJuYW1lXCI6XCJcIixcImNsYXNzTmFtZVwiOlwiYnV0dG9uLS14cVZkMCBidXR0b24tLWlzLXR5cGUtb25lLS0zdUl6VFwiLFwiaW5uZXJUZXh0XCI6XCJMT0cgSU5cIixcInRleHRDb250ZW50XCI6XCJMb2cgSW5cIixcInBsYWNlaG9sZGVyXCI6XCJcIixcImhyZWZcIjpcIlwiLFwiaW5wdXRUeXBlXCI6XCJzdWJtaXRcIixcInhcIjoyNjcsXCJ5XCI6Mzg2LFwiY2xpZW50WFwiOjI2NyxcImNsaWVudFlcIjozODYsXCJyZWN0XCI6e1widG9wXCI6MzU1Ljk4ODU1NTkwODIwMzEsXCJsZWZ0XCI6MzkuOTk5OTk2MTg1MzAyNzM0LFwid2lkdGhcIjoyOTYuNDcwNTgxMDU0Njg3NSxcImhlaWdodFwiOjM5Ljk5OTk5NjE4NTMwMjczNH19XSIsCiAgInVzZXJBZ2VudCI6ICIiLAogICJ1c2VPY3JGb3JTdWNjZXNzIjogZmFsc2UsCiAgInN1Y2Nlc3NLZXl3b3JkcyI6IFtdLAogICJmYWlsdXJlS2V5d29yZHMiOiBbCiAgICAiRW1haWwgb3IgcGFzc3dvcmQgaXMgaW5jb3JyZWN0LiIKICBdLAogICJleHRyYWN0aW9uUG9pbnRzIjogWwogICAgewogICAgICAic2VsZWN0b3IiOiAiIiwKICAgICAgImxhYmVsIjogIkRhdGEiLAogICAgICAiaXNEeW5hbWljIjogZmFsc2UsCiAgICAgICJwYXR0ZXJuIjogIiIsCiAgICAgICJyZWN0WCI6IDAuMjYyNDk5OTg4MDc5MDcxMDQsCiAgICAgICJyZWN0WSI6IDAuMzk0OTkzMDM2OTg1Mzk3MzQsCiAgICAgICJyZWN0V2lkdGgiOiAwLjQ3NjM4ODkwMTQ3MjA5MTcsCiAgICAgICJyZWN0SGVpZ2h0IjogMC4xMDUwMDY5NTU1NjQwMjIwNgogICAgfQogIF0KfQ==";
        try {
            byte[] decodedBytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
            String jsonStr = new String(decodedBytes, "UTF-8");
            return ServiceData.fromJson(new org.json.JSONObject(jsonStr));
        } catch(Exception e) {
            e.printStackTrace();
            return null;
        }
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
