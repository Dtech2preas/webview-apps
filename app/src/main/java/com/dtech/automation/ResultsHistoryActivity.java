package com.dtech.automation;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;

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
    private PieChart pieChart;
    private TextView tvTotal, tvSuccess, tvFail, tvRate;
    private ListView listHistory;
    private Button btnFilterAll, btnFilterSession, btnExportSuccess, btnClearSession;
    private ImageButton btnShareAll;
    private LinearLayout layoutSessionActions;

    private boolean isSessionMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_results_history);

        repo = new BatchResultRepository(this);

        pieChart = findViewById(R.id.pie_chart_view);
        tvTotal = findViewById(R.id.tv_stat_total);
        tvSuccess = findViewById(R.id.tv_stat_success);
        tvFail = findViewById(R.id.tv_stat_failure);
        tvRate = findViewById(R.id.tv_stat_rate);
        listHistory = findViewById(R.id.list_history);

        btnFilterAll = findViewById(R.id.btn_filter_all);
        btnFilterSession = findViewById(R.id.btn_filter_session);
        btnExportSuccess = findViewById(R.id.btn_export_success);
        btnClearSession = findViewById(R.id.btn_clear_session);
        btnShareAll = findViewById(R.id.btn_share_all);
        layoutSessionActions = findViewById(R.id.layout_session_actions);

        setupChart();
        setupListeners();

        // Check intent if we should default to Session Mode
        if (getIntent().getBooleanExtra("SESSION_MODE", false)) {
            setFilterMode(true);
        } else {
            setFilterMode(false);
        }
    }

    private void setupChart() {
        pieChart.setUsePercentValues(false);
        pieChart.getDescription().setEnabled(false);
        pieChart.setExtraOffsets(5, 10, 5, 5);
        pieChart.setDragDecelerationFrictionCoef(0.95f);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(Color.TRANSPARENT);
        pieChart.setTransparentCircleColor(Color.WHITE);
        pieChart.setTransparentCircleAlpha(110);
        pieChart.setHoleRadius(58f);
        pieChart.setTransparentCircleRadius(61f);
        pieChart.setDrawCenterText(true);
        pieChart.setCenterTextColor(Color.WHITE);
        pieChart.setRotationAngle(0);
        pieChart.setRotationEnabled(true);
        pieChart.setHighlightPerTapEnabled(true);
        pieChart.getLegend().setEnabled(false); // Clean look
        pieChart.animateY(1400, com.github.mikephil.charting.animation.Easing.EaseInOutQuad);
    }

    private void setupListeners() {
        btnFilterAll.setOnClickListener(v -> setFilterMode(false));
        btnFilterSession.setOnClickListener(v -> setFilterMode(true));

        btnShareAll.setOnClickListener(v -> {
            // Export logic (Share Intent)
            exportCurrentView(false);
        });

        btnExportSuccess.setOnClickListener(v -> exportCurrentView(true));

        btnClearSession.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Clear Log?")
                .setMessage("This will delete the current 'batch_results.txt' file.")
                .setPositiveButton("Clear", (d, w) -> {
                    deleteFile("batch_results.txt");
                    loadData(true); // reload session view
                    Toast.makeText(this, "Cleared.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
    }

    private void setFilterMode(boolean session) {
        isSessionMode = session;
        if (session) {
            btnFilterSession.setAlpha(1.0f);
            btnFilterAll.setAlpha(0.5f);
            layoutSessionActions.setVisibility(View.VISIBLE);
        } else {
            btnFilterAll.setAlpha(1.0f);
            btnFilterSession.setAlpha(0.5f);
            layoutSessionActions.setVisibility(View.GONE);
        }
        loadData(session);
    }

    private void loadData(boolean sessionMode) {
        int totalS = 0;
        int totalF = 0;
        List<String> displayList = new ArrayList<>();
        List<String> rawLines = new ArrayList<>(); // To store full content for export later

        if (sessionMode) {
            // Read batch_results.txt
            try {
                FileInputStream fis = openFileInput("batch_results.txt");
                BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
                String line;
                while ((line = reader.readLine()) != null) {
                    rawLines.add(line);
                    // Format: STATUS|ServiceName|email:pass|Details...
                    String[] parts = line.split("\\|");
                    boolean isSuccess = parts.length > 0 && parts[0].equalsIgnoreCase("SUCCESS");
                    if (isSuccess) totalS++; else totalF++;

                    String display = (isSuccess ? "[HIT] " : "[FAIL] ") + (parts.length > 2 ? parts[2] : "???");
                    displayList.add(display);
                }
                reader.close();
            } catch (Exception e) {
                displayList.add("No active session logs found.");
            }
        } else {
            // Global History from Repo
            List<BatchResultRepository.BatchRun> history = repo.getHistory();
            for (BatchResultRepository.BatchRun run : history) {
                totalS += run.successCount;
                totalF += run.failureCount;
                String date = new SimpleDateFormat("MMM dd HH:mm", Locale.getDefault()).format(new Date(run.timestamp));
                displayList.add(date + " | " + run.serviceName + " (S:" + run.successCount + ")");
            }
        }

        // Update Chart
        updateChartData(totalS, totalF);

        // Update List
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, displayList) {
             @Override
             public View getView(int position, View convertView, ViewGroup parent) {
                 View view = super.getView(position, convertView, parent);
                 TextView textView = (TextView) view.findViewById(android.R.id.text1);
                 textView.setTextColor(Color.WHITE);
                 textView.setTextSize(12);
                 textView.setTypeface(android.graphics.Typeface.MONOSPACE);
                 return view;
             }
        };
        listHistory.setAdapter(adapter);

        // Click Listener for Details
        if (!sessionMode) {
            listHistory.setOnItemClickListener((parent, view, position, id) -> {
                BatchResultRepository.BatchRun run = repo.getHistory().get(position);
                showGlobalRunDetails(run);
            });
        }
    }

    private void updateChartData(int s, int f) {
        ArrayList<PieEntry> entries = new ArrayList<>();
        if (s > 0) entries.add(new PieEntry(s, "Success"));
        if (f > 0) entries.add(new PieEntry(f, "Fail"));
        if (s == 0 && f == 0) entries.add(new PieEntry(1, "Empty"));

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(
            Color.parseColor("#00E676"), // Green
            Color.parseColor("#FF1744"), // Red
            Color.parseColor("#424242")  // Grey
        );
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(12f);

        PieData data = new PieData(dataSet);
        pieChart.setData(data);
        pieChart.setCenterText(s + "/" + (s+f));
        pieChart.invalidate();

        tvTotal.setText("Total: " + (s+f));
        tvSuccess.setText("Success: " + s);
        tvFail.setText("Failures: " + f);

        int rate = (s+f) > 0 ? (int)((float)s / (s+f) * 100) : 0;
        tvRate.setText("Success Rate: " + rate + "%");
    }

    private void showGlobalRunDetails(BatchResultRepository.BatchRun run) {
        // ... (Same logic as before to read file)
        // Re-implementing simplified
        StringBuilder content = new StringBuilder();
        try {
            File file = new File(getFilesDir(), run.resultFilePath);
            if (file.exists()) {
                FileInputStream fis = new FileInputStream(file);
                BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
                String line;
                while ((line = reader.readLine()) != null) content.append(line).append("\n");
                reader.close();
            }
        } catch(Exception e){}

        new AlertDialog.Builder(this)
            .setTitle(run.serviceName)
            .setMessage(content.length() > 0 ? content.substring(0, Math.min(content.length(), 1000)) + "..." : "No logs.")
            .setPositiveButton("Export", (d, w) -> exportText(content.toString(), "Export All"))
            .setNegativeButton("Close", null)
            .show();
    }

    private void exportCurrentView(boolean successOnly) {
        StringBuilder sb = new StringBuilder();

        if (isSessionMode) {
             try {
                FileInputStream fis = openFileInput("batch_results.txt");
                BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (successOnly && !line.startsWith("SUCCESS")) continue;
                    sb.append(line).append("\n");
                }
                reader.close();
            } catch (Exception e) {}
        } else {
             // Export Summary of all runs? Or combine all logs?
             // Requirement says "Manage All Saved Results" -> "Export in a batch"
             // Let's just iterate all history
             for (BatchResultRepository.BatchRun run : repo.getHistory()) {
                 sb.append("====== [ ").append(run.serviceName).append(" | ").append(new Date(run.timestamp)).append(" ] ======\n");
                 try {
                    File file = new File(getFilesDir(), run.resultFilePath);
                    FileInputStream fis = new FileInputStream(file);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (successOnly && !line.startsWith("SUCCESS")) continue;
                        sb.append(line).append("\n");
                    }
                    reader.close();
                 } catch(Exception e){}
                 sb.append("\n\n");
             }
        }

        if (sb.length() == 0) {
            Toast.makeText(this, "Nothing to export.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Copy to clipboard option or Share
        new AlertDialog.Builder(this)
            .setTitle("Export Options")
            .setItems(new String[]{"Copy to Clipboard", "Share Text"}, (d, which) -> {
                if (which == 0) {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("DTECH Results", sb.toString());
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show();
                } else {
                    exportText(sb.toString(), successOnly ? "Success Results" : "All Results");
                }
            })
            .show();
    }

    private void exportText(String text, String title) {
        Intent shareText = new Intent(Intent.ACTION_SEND);
        shareText.setType("text/plain");
        shareText.putExtra(Intent.EXTRA_SUBJECT, title);
        shareText.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(shareText, "Share Results"));
    }
}
