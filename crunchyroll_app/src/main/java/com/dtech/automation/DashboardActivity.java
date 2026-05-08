package com.dtech.automation;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class DashboardActivity extends Activity {

    private CardView cardQuickResume;
    private TextView tvLastService;
    private Button btnQuickStart;
    private RecyclerView recyclerMenu;
    private View viewStatusPulse;
    private TextView tvQuota;
    private ServiceRepository serviceRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        serviceRepo = new ServiceRepository(this);

        cardQuickResume = findViewById(R.id.card_quick_resume);
        tvLastService = findViewById(R.id.tv_last_service);
        btnQuickStart = findViewById(R.id.btn_quick_start);
        recyclerMenu = findViewById(R.id.recycler_menu);
        viewStatusPulse = findViewById(R.id.view_status_pulse);
        tvQuota = findViewById(R.id.tv_quota);

        setupGrid();
        setupPulseAnimation();

    }

    @Override
    protected void onResume() {
        super.onResume();

        updateQuotaDisplay();
    }

    private void updateQuotaDisplay() {
        int quota = QuotaManager.getQuota(this);
        tvQuota.setText("Remaining Tests: " + quota);
    }

    private void setupPulseAnimation() {
        ObjectAnimator scaleDown = ObjectAnimator.ofPropertyValuesHolder(
                viewStatusPulse,
                PropertyValuesHolder.ofFloat("scaleX", 1.2f),
                PropertyValuesHolder.ofFloat("scaleY", 1.2f),
                PropertyValuesHolder.ofFloat("alpha", 0.5f));
        scaleDown.setDuration(1000);
        scaleDown.setRepeatCount(ObjectAnimator.INFINITE);
        scaleDown.setRepeatMode(ObjectAnimator.REVERSE);
        scaleDown.setInterpolator(new AccelerateDecelerateInterpolator());
        scaleDown.start();
    }


    private void setupGrid() {
        List<MenuOption> options = new ArrayList<>();
        options.add(new MenuOption("Start Testing", android.R.drawable.ic_menu_compass, () -> {
            startActivity(new Intent(DashboardActivity.this, MainActivity.class));
        }));
        options.add(new MenuOption("Results History", android.R.drawable.ic_menu_recent_history, () -> {
            startActivity(new Intent(DashboardActivity.this, SimpleResultsActivity.class));
        }));
        options.add(new MenuOption("Top Up Quota", android.R.drawable.ic_menu_slideshow, () -> {
            startActivity(new Intent(DashboardActivity.this, PaymentActivity.class));
        }));
        options.add(new MenuOption("Info & Help", android.R.drawable.ic_menu_help, () -> {
            startActivity(new Intent(DashboardActivity.this, InfoActivity.class));
        }));
        options.add(new MenuOption("Settings", android.R.drawable.ic_menu_preferences, () -> {
            startActivity(new Intent(DashboardActivity.this, SettingsActivity.class));
        }));

        MenuAdapter adapter = new MenuAdapter(options);
        recyclerMenu.setLayoutManager(new GridLayoutManager(this, 2));
        recyclerMenu.setAdapter(adapter);
    }
// --- Inner Classes for Menu Grid ---

    private static class MenuOption {
        String title;
        int iconRes;
        Runnable onClick;

        MenuOption(String title, int iconRes, Runnable onClick) {
            this.title = title;
            this.iconRes = iconRes;
            this.onClick = onClick;
        }
    }

    private static class MenuAdapter extends RecyclerView.Adapter<MenuAdapter.ViewHolder> {
        private final List<MenuOption> list;

        MenuAdapter(List<MenuOption> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dashboard_card, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            MenuOption option = list.get(position);
            holder.tvTitle.setText(option.title);
            holder.ivIcon.setImageResource(option.iconRes);
            holder.itemView.setOnClickListener(v -> option.onClick.run());
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle;
            ImageView ivIcon;

            ViewHolder(View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tv_title);
                ivIcon = itemView.findViewById(R.id.iv_icon);
            }
        }
    }
}
