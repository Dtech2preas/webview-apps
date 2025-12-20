package com.dtech.automation;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SimpleResultsActivity extends Activity {

    private RecyclerView recyclerView;
    private Button btnClear;
    private ResultsAdapter adapter;
    private List<String> resultLines = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_results);

        recyclerView = findViewById(R.id.recycler_simple_results);
        btnClear = findViewById(R.id.btn_clear_history);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ResultsAdapter(this, resultLines);
        recyclerView.setAdapter(adapter);

        loadResults();

        btnClear.setOnClickListener(v -> {
            File file = new File(getFilesDir(), "batch_results.txt");
            if (file.exists()) {
                if (file.delete()) {
                    resultLines.clear();
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, "History Cleared", Toast.LENGTH_SHORT).show();
                } else {
                    try {
                        new FileOutputStream(file).close();
                        resultLines.clear();
                        adapter.notifyDataSetChanged();
                        Toast.makeText(this, "History Cleared", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "Failed to clear", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }

    private void loadResults() {
        resultLines.clear();
        File file = new File(getFilesDir(), "batch_results.txt");
        if (!file.exists()) {
            adapter.notifyDataSetChanged();
            return;
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    resultLines.add(line);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error loading results", Toast.LENGTH_SHORT).show();
        }
        Collections.reverse(resultLines);
        adapter.notifyDataSetChanged();
    }

    private static class ResultsAdapter extends RecyclerView.Adapter<ResultsAdapter.ViewHolder> {
        private final List<String> list;
        private final Context context;

        ResultsAdapter(Context context, List<String> list) {
            this.context = context;
            this.list = list;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            androidx.cardview.widget.CardView card = new androidx.cardview.widget.CardView(parent.getContext());
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );

            int margin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, context.getResources().getDisplayMetrics());
            params.setMargins(0, margin, 0, margin);
            card.setLayoutParams(params);

            card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.dtech_surface_grey));
            card.setRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, context.getResources().getDisplayMetrics()));
            card.setCardElevation(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, context.getResources().getDisplayMetrics()));

            TextView text = new TextView(parent.getContext());
            text.setId(android.R.id.text1);
            int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, context.getResources().getDisplayMetrics());
            text.setPadding(padding, padding, padding, padding);
            text.setTextSize(14);
            text.setTypeface(android.graphics.Typeface.MONOSPACE);

            card.addView(text);
            return new ViewHolder(card);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String line = list.get(position);
            holder.text.setText(line);

            if (line.contains("SUCCESS")) {
                holder.text.setTextColor(ContextCompat.getColor(context, R.color.dtech_green));
            } else if (line.contains("FAIL")) {
                holder.text.setTextColor(ContextCompat.getColor(context, R.color.dtech_red));
            } else {
                holder.text.setTextColor(Color.WHITE);
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView text;
            ViewHolder(View v) {
                super(v);
                text = v.findViewById(android.R.id.text1);
            }
        }
    }
}
