import re

# 1. Hide RECORD button in layout_control_deck.xml
file_path = "app/src/main/res/layout/layout_control_deck.xml"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace('android:id="@+id/btn_deck_record"', 'android:id="@+id/btn_deck_record"\n                android:visibility="gone"')

with open(file_path, "w") as f:
    f.write(content)

# 2. Hide IMPORT button in dialog_service_list.xml
file_path = "app/src/main/res/layout/dialog_service_list.xml"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace('android:id="@+id/btn_import_service"', 'android:id="@+id/btn_import_service"\n            android:visibility="gone"')

with open(file_path, "w") as f:
    f.write(content)

# 3. We also should hide the RECORD buttons in layout_floating_overlay.xml
file_path = "app/src/main/res/layout/layout_floating_overlay.xml"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace('android:id="@+id/btn_record_step1"', 'android:id="@+id/btn_record_step1"\n                    android:visibility="gone"')
content = content.replace('android:id="@+id/btn_record_step2"', 'android:id="@+id/btn_record_step2"\n                    android:visibility="gone"')

with open(file_path, "w") as f:
    f.write(content)

print("Patched layouts.")
