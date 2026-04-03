import re

file_path = "app/src/main/java/com/dtech/automation/MainActivity.java"
with open(file_path, "r") as f:
    content = f.read()

# We need to add logic in onCreate of MainActivity to check for START_RECORDING_MODE
# and if so, show the record buttons and start the flow.

# Let's find onCreate
on_create_pattern = r'(protected void onCreate\(Bundle savedInstanceState\) \{[\s\S]*?)(// --- Intent Handling ---)'

def replacement(m):
    return m.group(1) + """
        // Check for dev mode recording
        if (getIntent().getBooleanExtra("START_RECORDING_MODE", false)) {
            // Un-hide record buttons for dev
            btnRecordStep1.setVisibility(View.VISIBLE);
            btnRecordStep2.setVisibility(View.VISIBLE);
            Toast.makeText(this, "Developer Recording Mode Enabled", Toast.LENGTH_SHORT).show();
        }

        """ + m.group(2)

content = re.sub(on_create_pattern, replacement, content, count=1)

with open(file_path, "w") as f:
    f.write(content)
