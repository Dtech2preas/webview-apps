package com.dtech.automation;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ResultsHistoryActivity extends Activity {

    private BatchResultRepository repo;
    private PieChartView pieChart;
    private TextView tvTotal, tvSuccess, tvFail;
    private ListView listHistory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_results_history);

        repo = new BatchResultRepository(this);

        pieChart = findViewById(R.id.pie_chart);
        tvTotal = findViewById(R.id.tv_total_stats);
        tvSuccess = findViewById(R.id.tv_success_stats);
        tvFail = findViewById(R.id.tv_fail_stats);
        listHistory = findViewById(R.id.list_history);

        loadData();
    }

    private void loadData() {
        List<BatchResultRepository.BatchRun> history = repo.getHistory();

        int totalS = 0;
        int totalF = 0;

        List<String> displayList = new ArrayList<>();
        for (BatchResultRepository.BatchRun run : history) {
            totalS += run.successCount;
            totalF += run.failureCount;
            String date = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(new Date(run.timestamp));
            displayList.add(date + " - " + run.serviceName + " (S:" + run.successCount + " F:" + run.failureCount + ")");
        }

        updateChart(totalS, totalF);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, displayList);
        listHistory.setAdapter(adapter);

        listHistory.setOnItemClickListener((parent, view, position, id) -> {
            BatchResultRepository.BatchRun run = history.get(position);
            showRunDetails(run);
        });
    }

    private void updateChart(int s, int f) {
        pieChart.setData(s, f);
        tvTotal.setText("Total Runs: " + repo.getHistory().size());
        tvSuccess.setText("Success: " + s);
        tvFail.setText("Failures: " + f);
    }

    private void showRunDetails(BatchResultRepository.BatchRun run) {
        StringBuilder content = new StringBuilder();
        try {
            File file = new File(getFilesDir(), run.resultFilePath);
            if (file.exists()) {
                FileInputStream fis = new FileInputStream(file);
                BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
                String line;
                while ((line = reader.readLine()) != null) content.append(line).append("\n");
                reader.close();
            } else {
                content.append("Log file not found.");
            }
        } catch (Exception e) { content.append("Error reading log."); }

        new AlertDialog.Builder(this)
            .setTitle(run.serviceName + " Results")
            .setMessage("Stats: S=" + run.successCount + " F=" + run.failureCount + "\n\n" + (content.length() > 500 ? "Displaying first 500 chars...\n" + content.substring(0, 500) : content))
            .setPositiveButton("Export CSV", (d, w) -> exportToCSV(run, content.toString()))
            .setNegativeButton("Close", null)
            .show();
    }

    private void exportToCSV(BatchResultRepository.BatchRun run, String rawContent) {
        StringBuilder csv = new StringBuilder();
        csv.append("Status,Service,Credentials,Details\n");

        // Parse raw content which is pipe separated: STATUS|SERVICE|CREDS|DETAILS
        for (String line : rawContent.split("\n")) {
            String[] parts = line.split("\\|");
            for (int i=0; i<parts.length; i++) {
                csv.append("\"").append(parts[i].replace("\"", "\"\"")).append("\"");
                if (i < parts.length - 1) csv.append(",");
            }
            csv.append("\n");
        }

        try {
            // Save to cache/public
            String filename = "results_" + run.timestamp + ".csv";
            File file = new File(getExternalCacheDir(), filename);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
            fos.write(csv.toString().getBytes());
            fos.close();

            // Share Intent
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/csv");
            intent.putExtra(Intent.EXTRA_STREAM, android.net.Uri.fromFile(file));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // Note: fromFile is deprecated and triggers FileUriExposedException on newer Android (24+)
            // But since we are targeting SDK 34, we must use FileProvider.
            // However, setting up FileProvider requires Manifest edits and XML resources which is risky to get right blindly.
            // Strategy: Use plain text sharing if CSV file sharing is complex without FileProvider.
            // Or try to share as text but with .csv extension hint?
            // Let's fallback to sharing TEXT content if we can't easily do file uri.
            // Actually, we can use StrictMode hack or just share text content.
            // Given "Export Results" usually implies a file, let's try to just write to a public path if permission allows? No, Scoped Storage.
            // Safest: Share as Text, user can save as CSV.
            // OR: Action Create Document.

            // Let's use Action Send with text content, but title it CSV.
            Intent shareText = new Intent(Intent.ACTION_SEND);
            shareText.setType("text/plain");
            shareText.putExtra(Intent.EXTRA_SUBJECT, "Export.csv");
            shareText.putExtra(Intent.EXTRA_TEXT, csv.toString());
            startActivity(Intent.createChooser(shareText, "Save CSV"));

        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
