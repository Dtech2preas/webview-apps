package com.dtech.automation;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends Activity {

    private UrlManager urlManager;
    private EditText etNewUrl;
    private ListView listUrls;
    private ArrayAdapter<String> adapter;
    private List<String> displayList;
    private List<UrlManager.UrlItem> itemList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        urlManager = new UrlManager(this);
        etNewUrl = findViewById(R.id.et_new_url);
        listUrls = findViewById(R.id.list_urls);
        Button btnAdd = findViewById(R.id.btn_add_url);
        Button btnBack = findViewById(R.id.btn_back);

        displayList = new ArrayList<>();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, displayList);
        listUrls.setAdapter(adapter);

        refreshList();

        btnAdd.setOnClickListener(v -> {
            String url = etNewUrl.getText().toString().trim();
            if (!url.isEmpty()) {
                urlManager.addUrl(url);
                etNewUrl.setText("");
                refreshList();
            }
        });

        listUrls.setOnItemLongClickListener((parent, view, position, id) -> {
            String urlToRemove = itemList.get(position).url;
            urlManager.deleteUrl(urlToRemove);
            refreshList();
            Toast.makeText(SettingsActivity.this, "Deleted", Toast.LENGTH_SHORT).show();
            return true;
        });

        btnBack.setOnClickListener(v -> finish());
    }

    private void refreshList() {
        itemList = urlManager.getUrls();
        displayList.clear();
        for (UrlManager.UrlItem item : itemList) {
            String status = item.isDue() ? "[DUE] " : "[OK] ";
            displayList.add(status + item.url);
        }
        adapter.notifyDataSetChanged();
    }
}
