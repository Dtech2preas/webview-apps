with open("./app/src/main/java/com/dtech/automation/MainActivity.java", "r") as f:
    content = f.read()

target = """                    String actionName = "";
                    if (visualRecordingStep == 0) {
                        actionName = "Email Field";
                        visualRecordingStep++;
                        runOnUiThread(() -> visualStatusText.setText("Ready: Tap Password Field"));
                        Toast.makeText(this, actionName + " recorded. Tap Password field.", Toast.LENGTH_SHORT).show();
                    } else if (visualRecordingStep == 1) {
                        actionName = "Password Field";
                        visualRecordingStep++;
                        runOnUiThread(() -> visualStatusText.setText("Ready: Tap Login Button"));
                        Toast.makeText(this, actionName + " recorded. Tap Login button.", Toast.LENGTH_SHORT).show();
                    } else if (visualRecordingStep == 2) {
                        actionName = "Login Button";
                        visualRecordingStep++;
                        runOnUiThread(() -> visualStatusText.setText("Done: Click STOP RECORDING"));
                        Toast.makeText(this, actionName + " recorded. Click STOP to finish.", Toast.LENGTH_SHORT).show();
                        // Automatically stop after login button click
                        stopRecording();
                    }"""

replacement = """                    String actionName = "Action " + (visualRecordingStep + 1);
                    visualRecordingStep++;
                    runOnUiThread(() -> visualStatusText.setText("Ready: Tap next field/button or click STOP"));
                    Toast.makeText(MainActivity.this, actionName + " recorded. Tap next field/button or STOP.", Toast.LENGTH_SHORT).show();"""

if target in content:
    content = content.replace(target, replacement)
    with open("./app/src/main/java/com/dtech/automation/MainActivity.java", "w") as f:
        f.write(content)
    print("Patch applied successfully.")
else:
    print("Target not found.")
