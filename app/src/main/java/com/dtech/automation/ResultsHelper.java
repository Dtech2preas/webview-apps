package com.dtech.automation;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ResultsHelper {

    private static final String FILENAME = "batch_results.txt";

    public static class ServiceStats {
        public String serviceName;
        public int successCount = 0;
        public int failureCount = 0;
        public List<String> logs = new ArrayList<>();

        public ServiceStats(String serviceName) {
            this.serviceName = serviceName;
        }

        public int getTotal() {
            return successCount + failureCount;
        }

        public int getSuccessRate() {
            int total = getTotal();
            return total == 0 ? 0 : (successCount * 100) / total;
        }
    }

    public static List<ServiceStats> parseResults(Context context) {
        File file = new File(context.getFilesDir(), FILENAME);
        if (!file.exists()) return new ArrayList<>();

        Map<String, ServiceStats> map = new LinkedHashMap<>();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                // Format: STATUS|SERVICE_NAME|DATA
                // Example: SUCCESS|Netflix|email:pass | Data
                String[] parts = line.split("\\|", 3);
                if (parts.length >= 2) {
                    String status = parts[0].trim();
                    String service = parts[1].trim();

                    ServiceStats stats = map.get(service);
                    if (stats == null) {
                        stats = new ServiceStats(service);
                        map.put(service, stats);
                    }

                    stats.logs.add(line);
                    if (status.contains("SUCCESS")) {
                        stats.successCount++;
                    } else {
                        stats.failureCount++;
                    }
                } else {
                    // Fallback for malformed lines or legacy format
                    String service = "Unknown";
                    ServiceStats stats = map.get(service);
                    if (stats == null) {
                        stats = new ServiceStats(service);
                        map.put(service, stats);
                    }
                    stats.logs.add(line);
                    if (line.contains("SUCCESS")) stats.successCount++;
                    else stats.failureCount++;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Reverse logs to show newest first
        List<ServiceStats> result = new ArrayList<>(map.values());
        for (ServiceStats s : result) {
            Collections.reverse(s.logs);
        }
        return result;
    }

    public static ServiceStats getServiceStats(Context context, String serviceName) {
        List<ServiceStats> all = parseResults(context);
        for (ServiceStats s : all) {
            if (s.serviceName.equals(serviceName)) {
                return s;
            }
        }
        return new ServiceStats(serviceName);
    }

    public static void clearAllResults(Context context) {
        File file = new File(context.getFilesDir(), FILENAME);
        if (file.exists()) {
            file.delete();
        }
    }

    public static void clearServiceFailures(Context context, String serviceName) {
        List<String> linesToKeep = new ArrayList<>();
        File file = new File(context.getFilesDir(), FILENAME);
        if (!file.exists()) return;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                // If line belongs to this service AND is failure, skip it.
                // Otherwise keep it.
                boolean isTargetService = false;
                String[] parts = line.split("\\|", 3);
                if (parts.length >= 2) {
                    if (parts[1].trim().equals(serviceName)) {
                        isTargetService = true;
                    }
                }

                if (isTargetService && line.contains("FAILURE")) {
                    continue; // Skip (Delete)
                }
                linesToKeep.add(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        rewriteFile(context, linesToKeep);
    }

    public static void clearServiceResults(Context context, String serviceName) {
        List<String> linesToKeep = new ArrayList<>();
        File file = new File(context.getFilesDir(), FILENAME);
        if (!file.exists()) return;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                // If line belongs to this service, skip it.
                // Otherwise keep it.
                boolean isTargetService = false;
                String[] parts = line.split("\\|", 3);
                if (parts.length >= 2) {
                    if (parts[1].trim().equals(serviceName)) {
                        isTargetService = true;
                    }
                }

                if (isTargetService) {
                    continue; // Skip (Delete)
                }
                linesToKeep.add(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        rewriteFile(context, linesToKeep);
    }

    private static void rewriteFile(Context context, List<String> lines) {
        File file = new File(context.getFilesDir(), FILENAME);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            for (String line : lines) {
                fos.write((line + "\n").getBytes());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getFormattedExport(List<ServiceStats> statsList, boolean onlySuccess, String singleServiceFilter) {
        StringBuilder sb = new StringBuilder();

        for (ServiceStats stats : statsList) {
            if (singleServiceFilter != null && !stats.serviceName.equals(singleServiceFilter)) {
                continue;
            }

            // If we only want success and this service has 0 success, skip header?
            // "Exports a .txt file containing only successful hits from ALL services."
            // If a service has 0 success, it probably shouldn't appear or just have empty section.
            // Let's check if there are any lines to print.
            List<String> linesToPrint = new ArrayList<>();
            for (String log : stats.logs) {
                if (onlySuccess && !log.contains("SUCCESS")) continue;
                linesToPrint.add(log);
            }

            if (linesToPrint.isEmpty()) continue;

            sb.append("====== [ ").append(stats.serviceName.toUpperCase()).append(" ] ======\n");
            for (String log : linesToPrint) {
                // Log format: STATUS|SERVICE|DATA
                // Target format: email:pass | Captured Data
                String[] parts = log.split("\\|", 3);
                if (parts.length >= 3) {
                    sb.append(parts[2].trim()).append("\n");
                } else {
                    sb.append(log).append("\n"); // Fallback
                }
            }
            sb.append("\n");
        }

        sb.append("[ Powered by D-TECH https://t.me/DTECHX24 ]");
        return sb.toString();
    }
}
