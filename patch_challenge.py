import re

with open('app/src/main/java/com/dtech/automation/MainActivity.java', 'r') as f:
    content = f.read()

# Add a check in processVerifierResult for challenges
verifier_pattern = r'(private void processVerifierResult\(String result\) \{.*?try \{.*?JSONObject json = new JSONObject\(result\);.*?String status = json\.getString\("status"\);)'
challenge_injection = """\\1
            if ("challenge".equals(status)) {
                pauseBatchForChallenge(json.optString("detail", "Challenge detected"));
                return;
            }"""

if "challenge" not in content and "processVerifierResult" in content:
    content = re.sub(verifier_pattern, challenge_injection, content, flags=re.DOTALL)

# Add the pauseBatchForChallenge method
pause_method = """
    private void pauseBatchForChallenge(String reason) {
        if (!isBatchRunning || isBatchPaused) return;

        isBatchPaused = true;
        runOnUiThread(() -> {
            Toast.makeText(this, "Batch Paused: " + reason, Toast.LENGTH_LONG).show();
            updateUIState();

            new AlertDialog.Builder(this)
                .setTitle("Action Required")
                .setMessage("A challenge or CAPTCHA was detected (" + reason + "). Please solve it manually, then press Resume.")
                .setPositiveButton("I solved it, Resume", (dialog, which) -> {
                    isBatchPaused = false;
                    updateUIState();
                    verifySuccess(); // Re-trigger verification
                })
                .setNegativeButton("Skip Account", (dialog, which) -> {
                    isBatchPaused = false;
                    recordResult("SKIPPED (Challenge)", "Manually Skipped");
                    continueBatchProcessing();
                })
                .setCancelable(false)
                .show();
        });
    }
"""

if "pauseBatchForChallenge" not in content:
    # Insert before continueBatchProcessing
    continue_index = content.find('private void continueBatchProcessing')
    if continue_index != -1:
        content = content[:continue_index] + pause_method + content[continue_index:]

with open('app/src/main/java/com/dtech/automation/MainActivity.java', 'w') as f:
    f.write(content)

print("Patch applied for auto-challenge pausing")
