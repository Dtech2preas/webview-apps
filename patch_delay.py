with open("./app/src/main/java/com/dtech/automation/MainActivity.java", "r") as f:
    content = f.read()

target = """                if (stepIndex == 0 || stepIndex == 1) {
                    batchHandler.postDelayed(() -> {
                        String credential = credentialList.get(currentCredentialIndex);
                        String[] parts = credential.split(":");
                        String textToInject = (stepIndex == 0) ? parts[0] : (parts.length > 1 ? parts[1] : "");

                        BaseInputConnection connection = new BaseInputConnection(mWebView, true);
                        connection.commitText(textToInject, 1);

                        // Small delay before next step
                        batchHandler.postDelayed(() -> executeVisualSteps(stepIndex + 1), 800);
                    }, 500); // Wait for focus
                } else {
                    // It was a click, proceed to next
                    batchHandler.postDelayed(() -> executeVisualSteps(stepIndex + 1), 800);
                }"""

replacement = """                if (stepIndex == 0 || stepIndex == 1) {
                    batchHandler.postDelayed(() -> {
                        String credential = credentialList.get(currentCredentialIndex);
                        String[] parts = credential.split(":");
                        String textToInject = (stepIndex == 0) ? parts[0] : (parts.length > 1 ? parts[1] : "");

                        BaseInputConnection connection = new BaseInputConnection(mWebView, true);
                        connection.commitText(textToInject, 1);

                        // Increased delay before next step to allow UI updates
                        batchHandler.postDelayed(() -> executeVisualSteps(stepIndex + 1), 2000);
                    }, 1000); // Wait longer for focus
                } else {
                    // It was a click, proceed to next with an increased delay
                    batchHandler.postDelayed(() -> executeVisualSteps(stepIndex + 1), 2000);
                }"""

if target in content:
    content = content.replace(target, replacement)
    with open("./app/src/main/java/com/dtech/automation/MainActivity.java", "w") as f:
        f.write(content)
    print("Patch applied successfully.")
else:
    print("Target not found.")
