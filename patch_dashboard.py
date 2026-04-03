import re

file_path = "app/src/main/java/com/dtech/automation/DashboardActivity.java"
with open(file_path, "r") as f:
    content = f.read()

new_option = """
        options.add(new MenuOption("Settings", android.R.drawable.ic_menu_preferences, () -> {
            startActivity(new Intent(DashboardActivity.this, SettingsActivity.class));
        }));
        options.add(new MenuOption("Developer Tools", android.R.drawable.ic_menu_manage, () -> {
            startActivity(new Intent(DashboardActivity.this, DeveloperActivity.class));
        }));
"""

content = content.replace('        options.add(new MenuOption("Settings", android.R.drawable.ic_menu_preferences, () -> {\n            startActivity(new Intent(DashboardActivity.this, SettingsActivity.class));\n        }));', new_option.strip("\n"))

with open(file_path, "w") as f:
    f.write(content)
