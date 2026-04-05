import re

with open('app/src/main/java/com/dtech/automation/MainActivity.java', 'r') as f:
    content = f.read()

# 1. Add stealth WebView settings in setupWebView()
settings_block_pattern = r'(WebSettings webSettings = mWebView.getSettings\(\);)(.*?)(webSettings.setJavaScriptEnabled\(true\);)'
stealth_settings = """\\1\\2
        // AI Stealth Enhancements
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        // Randomize User Agent slightly to bypass generic bot filters
        String defaultAgent = WebSettings.getDefaultUserAgent(this);
        webSettings.setUserAgentString(defaultAgent + " (Mobile; D-Tech; rv:109.0) Gecko/109.0");

        \\3"""

content = re.sub(settings_block_pattern, stealth_settings, content, flags=re.DOTALL)

# 2. Add antibot injection onPageStarted
on_page_started_pattern = r'(public void onPageStarted\(WebView view, String url, Bitmap favicon\) \{)(.*?)(super\.onPageStarted\(view, url, favicon\);)'
antibot_injection = """\\1\\2\\3
                try {
                    java.io.InputStream is = getAssets().open("antibot.js");
                    int size = is.available();
                    byte[] buffer = new byte[size];
                    is.read(buffer);
                    is.close();
                    String script = new String(buffer, "UTF-8");
                    view.evaluateJavascript(script, null);
                } catch (Exception e) {
                    e.printStackTrace();
                }
"""

if "onPageStarted" in content:
    content = re.sub(on_page_started_pattern, antibot_injection, content, flags=re.DOTALL)
else:
    # If onPageStarted doesn't exist in the WebViewClient, we need to add it before onPageFinished
    on_page_finished_index = content.find('public void onPageFinished')
    if on_page_finished_index != -1:
        injection = """
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                try {
                    java.io.InputStream is = getAssets().open("antibot.js");
                    int size = is.available();
                    byte[] buffer = new byte[size];
                    is.read(buffer);
                    is.close();
                    String script = new String(buffer, "UTF-8");
                    view.evaluateJavascript(script, null);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        """
        content = content[:on_page_finished_index] + injection + content[on_page_finished_index:]

with open('app/src/main/java/com/dtech/automation/MainActivity.java', 'w') as f:
    f.write(content)

print("Patch applied")
