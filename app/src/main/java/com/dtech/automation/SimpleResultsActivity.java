package com.dtech.automation;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class SimpleResultsActivity extends Activity {

    private TextView tvGlobalRate, tvGlobalSuccess, tvGlobalFail;
    private View viewGlobalSuccessBar, viewGlobalFailBar;
    private ExpandableListView expandableListView;
    private Button btnShareAll, btnClearAll;

    private ResultsAdapter adapter;
    private List<ResultsHelper.ServiceStats> statsList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_results);

        // Header Views
        tvGlobalRate = findViewById(R.id.tv_global_rate);
        tvGlobalSuccess = findViewById(R.id.tv_global_success_count);
        tvGlobalFail = findViewById(R.id.tv_global_fail_count);
        viewGlobalSuccessBar = findViewById(R.id.view_global_success_bar);
        viewGlobalFailBar = findViewById(R.id.view_global_fail_bar);

        // List
        expandableListView = findViewById(R.id.expandable_results);

        // Footer Actions
        btnShareAll = findViewById(R.id.btn_share_all_success);
        btnClearAll = findViewById(R.id.btn_clear_all_global);

        adapter = new ResultsAdapter(this, statsList);
        expandableListView.setAdapter(adapter);

        setupListeners();
        loadData();
    }

    private void setupListeners() {
        btnShareAll.setOnClickListener(v -> shareAllSuccess());

        btnClearAll.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Clear All History?")
                .setMessage("This will wipe the entire batch results file.")
                .setPositiveButton("Clear Everything", (d, w) -> {
                    ResultsHelper.clearAllResults(this);
                    loadData();
                    Toast.makeText(this, "All history cleared.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
    }

    private void loadData() {
        new Thread(() -> {
            List<ResultsHelper.ServiceStats> results = ResultsHelper.parseResults(this);

            runOnUiThread(() -> {
                statsList.clear();
                statsList.addAll(results);

                // Calculate Global Stats
                int totalS = 0;
                int totalF = 0;
                for (ResultsHelper.ServiceStats s : statsList) {
                    totalS += s.successCount;
                    totalF += s.failureCount;
                }
                int total = totalS + totalF;
                int rate = total == 0 ? 0 : (totalS * 100) / total;

                tvGlobalRate.setText("Global Success Rate: " + rate + "%");
                tvGlobalSuccess.setText("S: " + totalS);
                tvGlobalFail.setText("F: " + totalF);

                // Update Bar Graph Weights
                LinearLayout.LayoutParams pS = (LinearLayout.LayoutParams) viewGlobalSuccessBar.getLayoutParams();
                LinearLayout.LayoutParams pF = (LinearLayout.LayoutParams) viewGlobalFailBar.getLayoutParams();

                if (total == 0) {
                    pS.weight = 1;
                    pF.weight = 1;
                } else {
                    pS.weight = totalS == 0 ? 0.01f : totalS;
                    pF.weight = totalF == 0 ? 0.01f : totalF;
                }
                viewGlobalSuccessBar.setLayoutParams(pS);
                viewGlobalFailBar.setLayoutParams(pF);

                adapter.notifyDataSetChanged();
            });
        }).start();
    }

    private void shareAllSuccess() {
        String content = ResultsHelper.getFormattedExport(statsList, true, null);
        shareTextFile(content, "all_success_results.txt");
    }

    private void shareTextFile(String content, String fileName) {
        try {
            File shareDir = new File(getCacheDir(), "shared_exports");
            if (!shareDir.exists()) {
                shareDir.mkdirs();
            }
            File file = new File(shareDir, fileName);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(content.getBytes());
            fos.close();

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share Results"));
        } catch (Exception e) {
            Toast.makeText(this, "Export Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    // --- Adapter ---

    private class ResultsAdapter extends BaseExpandableListAdapter {
        private final Context context;
        private final List<ResultsHelper.ServiceStats> groups;

        ResultsAdapter(Context context, List<ResultsHelper.ServiceStats> groups) {
            this.context = context;
            this.groups = groups;
        }

        @Override
        public int getGroupCount() {
            return groups.size();
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            // Logs + 1 (for the Actions row at top)
            return groups.get(groupPosition).logs.size() + 1;
        }

        @Override
        public Object getGroup(int groupPosition) {
            return groups.get(groupPosition);
        }

        @Override
        public Object getChild(int groupPosition, int childPosition) {
            if (childPosition == 0) return null; // Actions row
            return groups.get(groupPosition).logs.get(childPosition - 1);
        }

        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_result_group, parent, false);
            }

            ResultsHelper.ServiceStats stats = groups.get(groupPosition);

            TextView tvName = convertView.findViewById(R.id.tv_group_service);
            TextView tvStats = convertView.findViewById(R.id.tv_group_stats);
            View barS = convertView.findViewById(R.id.view_group_success_bar);
            View barF = convertView.findViewById(R.id.view_group_fail_bar);
            ImageView ivArrow = convertView.findViewById(R.id.iv_group_indicator);

            tvName.setText(stats.serviceName.toUpperCase());
            tvStats.setText("S:" + stats.successCount + "  F:" + stats.failureCount);

            LinearLayout.LayoutParams pS = (LinearLayout.LayoutParams) barS.getLayoutParams();
            LinearLayout.LayoutParams pF = (LinearLayout.LayoutParams) barF.getLayoutParams();
            if (stats.getTotal() == 0) {
                pS.weight = 1;
                pF.weight = 1;
            } else {
                pS.weight = stats.successCount == 0 ? 0.01f : stats.successCount;
                pF.weight = stats.failureCount == 0 ? 0.01f : stats.failureCount;
            }
            barS.setLayoutParams(pS);
            barF.setLayoutParams(pF);

            ivArrow.setRotation(isExpanded ? 180 : 0);

            return convertView;
        }

        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
            ResultsHelper.ServiceStats groupStats = groups.get(groupPosition);

            if (childPosition == 0) {
                // Actions Row
                // Use a simple layout dynamically or inflate a special one?
                // I'll create a simple horizontal layout programmatically or use item_result_child and modify it?
                // Better to inflate a dedicated actions layout, but I didn't create one.
                // I will create a simple one programmatically here to avoid extra files if allowed,
                // or just re-use a vertical layout.
                // Let's inflate a custom View for actions.
                LinearLayout actionsLayout = new LinearLayout(context);
                actionsLayout.setOrientation(LinearLayout.HORIZONTAL);
                actionsLayout.setPadding(16, 8, 16, 8);
                actionsLayout.setBackgroundColor(0xFF202020); // Dark grey

                Button btnShare = new Button(context);
                btnShare.setText("Share Success");
                btnShare.setTextSize(10);
                LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                p1.setMargins(0,0,4,0);
                btnShare.setLayoutParams(p1);
                btnShare.setBackgroundColor(0xFF00E5FF); // Cyan
                btnShare.setTextColor(0xFF000000);

                Button btnClearFail = new Button(context);
                btnClearFail.setText("Clear Failed");
                btnClearFail.setTextSize(10);
                LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                p2.setMargins(4,0,0,0);
                btnClearFail.setLayoutParams(p2);
                btnClearFail.setBackgroundColor(0xFFD50000); // Red
                btnClearFail.setTextColor(0xFFFFFFFF);

                actionsLayout.addView(btnShare);
                actionsLayout.addView(btnClearFail);

                // Listeners
                btnShare.setOnClickListener(v -> {
                    String c = ResultsHelper.getFormattedExport(statsList, true, groupStats.serviceName);
                    shareTextFile(c, groupStats.serviceName + "_success.txt");
                });

                btnClearFail.setOnClickListener(v -> {
                    ResultsHelper.clearServiceFailures(context, groupStats.serviceName);
                    loadData(); // Refresh global list
                    Toast.makeText(context, "Cleared failures for " + groupStats.serviceName, Toast.LENGTH_SHORT).show();
                });

                return actionsLayout;
            } else {
                // Log Row
                if (convertView == null || convertView instanceof LinearLayout && ((LinearLayout)convertView).getChildCount() > 1) {
                    // Make sure we are not reusing the actions view
                    convertView = LayoutInflater.from(context).inflate(R.layout.item_result_child, parent, false);
                }

                // Double check it's the right layout (has tv_child_log)
                TextView tvLog = convertView.findViewById(R.id.tv_child_log);
                if (tvLog == null) {
                     convertView = LayoutInflater.from(context).inflate(R.layout.item_result_child, parent, false);
                     tvLog = convertView.findViewById(R.id.tv_child_log);
                }

                String log = groupStats.logs.get(childPosition - 1);
                tvLog.setText(log);

                if (log.contains("SUCCESS")) tvLog.setTextColor(0xFF69F0AE); // Green
                else if (log.contains("FAILURE")) tvLog.setTextColor(0xFFFF5252); // Red
                else tvLog.setTextColor(0xFFFFFFFF);

                return convertView;
            }
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }
    }
}
