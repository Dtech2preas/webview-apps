(function() {
    console.log("Anti-Bot Script Injected (Pre-load)");

    // 1. Mask navigator.webdriver
    Object.defineProperty(navigator, 'webdriver', {
        get: () => false,
    });

    // 2. Fake Chrome Plugins to look like a real desktop browser
    if (navigator.plugins.length === 0) {
        Object.defineProperty(navigator, 'plugins', {
            get: () => [
                { name: "Chrome PDF Plugin" },
                { name: "Chrome PDF Viewer" },
                { name: "Native Client" }
            ],
        });
    }

    // 3. Fake languages
    Object.defineProperty(navigator, 'languages', {
        get: () => ['en-US', 'en'],
    });

    // 4. Overwrite window.chrome (often checked by Cloudflare)
    window.chrome = {
        runtime: {},
        app: {},
        csi: function() {},
        loadTimes: function() {}
    };

    // 5. Spoof hardware concurrency
    Object.defineProperty(navigator, 'hardwareConcurrency', {
        get: () => 4
    });

    // 6. Mock permissions API (some bots check this for consistency)
    const originalQuery = window.navigator.permissions.query;
    window.navigator.permissions.query = (parameters) => (
        parameters.name === 'notifications' ?
            Promise.resolve({ state: Notification.permission }) :
            originalQuery(parameters)
    );

    // 7. Prevent iframe detection
    try {
        if (window.top !== window.self) {
            console.log("Running inside iframe - applying additional stealth");
            Object.defineProperty(window, 'frameElement', { get: () => null });
        }
    } catch(e) {}

    console.log("Anti-Bot Stealth Applied Successfully");
})();
