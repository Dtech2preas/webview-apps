import re

with open('app/src/main/java/com/dtech/automation/MainActivity.java', 'r') as f:
    content = f.read()

# Add a robust clearCookiesAndCache method
clear_method = """
    private void clearCookiesAndCache() {
        mWebView.clearCache(true);
        mWebView.clearHistory();
        mWebView.clearFormData();

        android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
        cookieManager.removeAllCookies(null);
        cookieManager.flush();

        android.webkit.WebStorage.getInstance().deleteAllData();
    }
"""

# Insert it before setupWebView
setup_index = content.find('private void setupWebView()')
if setup_index != -1:
    content = content[:setup_index] + clear_method + content[setup_index:]

# Call clearCookiesAndCache in restartBatch (so every batch run gets a clean session)
restart_batch_pattern = r'(private void continueBatchProcessing\(\) \{.*?)(loadUrlWithWait.*?;)'
content = re.sub(restart_batch_pattern, r'\1clearCookiesAndCache();\n        \2', content, flags=re.DOTALL)

with open('app/src/main/java/com/dtech/automation/MainActivity.java', 'w') as f:
    f.write(content)

print("Patch applied for cache clearing")
