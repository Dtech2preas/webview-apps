import re

file_path = "app/src/main/java/com/dtech/automation/ServiceRepository.java"
with open(file_path, "r") as f:
    content = f.read()

import_str = """
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.content.res.AssetManager;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
"""

content = re.sub(r'import android.content.Context;\nimport android.content.SharedPreferences;\nimport android.util.Log;', import_str.strip(), content)


load_services_code = """
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
                                sb.append(line).append("\\n");
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
"""

content = re.sub(r'    private void loadServices\(\) \{.*?\n    \}', load_services_code.strip(), content, flags=re.DOTALL)

with open(file_path, "w") as f:
    f.write(content)
