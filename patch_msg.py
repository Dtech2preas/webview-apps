with open("./app/src/main/java/com/dtech/automation/MainActivity.java", "r") as f:
    content = f.read()

target = """.setMessage("1. Wait for the yellow indicator to turn green.\\n2. Tap the Email field.\\n3. Tap the Password field.\\n4. Tap the Login button.")"""

replacement = """.setMessage("1. Wait for the yellow indicator to turn green.\\n2. Tap the fields and buttons in order.\\n3. Click STOP RECORDING when finished.")"""

if target in content:
    content = content.replace(target, replacement)
    with open("./app/src/main/java/com/dtech/automation/MainActivity.java", "w") as f:
        f.write(content)
    print("Patch applied successfully.")
else:
    print("Target not found.")
