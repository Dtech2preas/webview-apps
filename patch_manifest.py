import re

file_path = "app/src/main/AndroidManifest.xml"
with open(file_path, "r") as f:
    content = f.read()

new_activity = """
        <activity android:name=".SettingsActivity" />
        <activity android:name=".DeveloperActivity" />
"""

content = content.replace('<activity android:name=".SettingsActivity" />', new_activity.strip("\n"))

with open(file_path, "w") as f:
    f.write(content)
