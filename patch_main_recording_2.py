import re

file_path = "app/src/main/java/com/dtech/automation/MainActivity.java"
with open(file_path, "r") as f:
    content = f.read()

# Let's insert the check right after setupScannerUI();

search_str = "setupScannerUI();"
replace_str = """setupScannerUI();

        // Check for dev mode recording
        if (getIntent().getBooleanExtra("START_RECORDING_MODE", false)) {
            // Un-hide record buttons for dev
            if (btnRecordStep1 != null) btnRecordStep1.setVisibility(View.VISIBLE);
            if (btnRecordStep2 != null) btnRecordStep2.setVisibility(View.VISIBLE);
            Toast.makeText(this, "Developer Recording Mode Enabled", Toast.LENGTH_SHORT).show();
        }"""

content = content.replace(search_str, replace_str)

with open(file_path, "w") as f:
    f.write(content)
