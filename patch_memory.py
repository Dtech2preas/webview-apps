import re

with open('app/src/main/java/com/dtech/automation/MainActivity.java', 'r') as f:
    content = f.read()

# Add periodic System.gc() to continueBatchProcessing
continue_pattern = r'(private void continueBatchProcessing\(\) \{)(.*?)(\})'

def add_gc(match):
    body = match.group(2)
    gc_logic = """
        // Periodic Garbage Collection every 50 accounts to prevent OOM
        if (currentBatchIndex > 0 && currentBatchIndex % 50 == 0) {
            System.gc();
            mWebView.clearCache(true); // Aggressive cache flush
        }
    """
    return match.group(1) + gc_logic + body + match.group(3)

content = re.sub(continue_pattern, add_gc, content, count=1, flags=re.DOTALL)

with open('app/src/main/java/com/dtech/automation/MainActivity.java', 'w') as f:
    f.write(content)

print("Patch applied for memory management")
