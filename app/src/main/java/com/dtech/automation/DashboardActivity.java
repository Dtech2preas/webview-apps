package com.dtech.automation;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class DashboardActivity extends Activity {

    private LinearLayout cardQuickResume;
    private TextView tvLastService;
    private Button btnQuickStart, btnOpenAutomation, btnResultsHistory, btnAdSystem, btnInfoHelp;
    private ServiceRepository serviceRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        serviceRepo = new ServiceRepository(this);

        cardQuickResume = findViewById(R.id.card_quick_resume);
        tvLastService = findViewById(R.id.tv_last_service);
        btnQuickStart = findViewById(R.id.btn_quick_start);
        btnOpenAutomation = findViewById(R.id.btn_open_automation);
        btnResultsHistory = findViewById(R.id.btn_results_history);
        btnAdSystem = findViewById(R.id.btn_ad_system);
        btnInfoHelp = findViewById(R.id.btn_info_help);

        setupListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadQuickResume();
    }

    private void setupListeners() {
        btnOpenAutomation.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, MainActivity.class);
            startActivity(intent);
        });

        btnResultsHistory.setOnClickListener(v -> {
            // Will link to ResultsHistoryActivity later
             Intent intent = new Intent(DashboardActivity.this, ResultsHistoryActivity.class);
             startActivity(intent);
        });

        btnAdSystem.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, AdSystemActivity.class);
            startActivity(intent);
        });

        btnInfoHelp.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, InfoActivity.class);
            startActivity(intent);
        });

        btnQuickStart.setOnClickListener(v -> {
            String lastId = serviceRepo.getLastUsedServiceId();
            if (lastId != null) {
                Intent intent = new Intent(DashboardActivity.this, MainActivity.class);
                intent.putExtra("SERVICE_ID", lastId);
                intent.putExtra("AUTO_START", true);
                startActivity(intent);
            }
        });
    }

    private void loadQuickResume() {
        String lastId = serviceRepo.getLastUsedServiceId();
        if (lastId != null) {
            ServiceRepository.ServiceData service = serviceRepo.getServiceById(lastId);
            if (service != null) {
                tvLastService.setText(service.getName());
                cardQuickResume.setVisibility(View.VISIBLE);
            } else {
                cardQuickResume.setVisibility(View.GONE);
            }
        } else {
            cardQuickResume.setVisibility(View.GONE);
        }
    }
}
