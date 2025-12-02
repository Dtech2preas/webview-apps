
(function() {
    // Check for success or failure indicators
    // Returns JSON string: { status: "success" | "failure" | "pending" | "rate_limit" }

    var currentUrl = window.location.href;
    var bodyText = document.body.innerText || "";

    // Check URL first (Strongest indicator of success)
    if (!currentUrl.includes("/login")) {
         return JSON.stringify({ status: "success", detail: "Redirected away from login" });
    }

    // Rate Limit Checks
    if (bodyText.includes("limit reach") ||
        bodyText.includes("Too Many Requests") ||
        bodyText.includes("rate limit")) {
        return JSON.stringify({ status: "rate_limit", detail: "Rate limit detected" });
    }

    // Check DOM for specific error messages
    if (bodyText.includes("Email or password is incorrect") ||
        bodyText.includes("Something went wrong") ||
        bodyText.includes("Invalid login")) {
        return JSON.stringify({ status: "failure", detail: "Error message found" });
    }

    // Check DOM for success messages
    if (bodyText.includes("successfully logged in")) {
         return JSON.stringify({ status: "success", detail: "Success text found" });
    }

    // Check for "Red Popup" or generic error indicators at the top of the page
    var allElements = document.getElementsByTagName("*");
    for (var i = 0; i < allElements.length; i++) {
        var el = allElements[i];
        // Optimization: Only check elements near the top
        var rect = el.getBoundingClientRect();
        if (rect.top < 200 && rect.height > 0 && rect.width > 0) {
            var style = window.getComputedStyle(el);
            var bg = style.backgroundColor;

            var rgbMatch = bg.match(/^rgba?\((\d+),\s*(\d+),\s*(\d+)/);
            if (rgbMatch) {
                var r = parseInt(rgbMatch[1]);
                var g = parseInt(rgbMatch[2]);
                var b = parseInt(rgbMatch[3]);

                // Heuristic: Red > 150, Green & Blue < 100 -> likely a red error box
                if (r > 150 && g < 100 && b < 100) {
                     var text = el.innerText ? el.innerText.trim() : "";
                     if (text.length > 0) {
                         // Check if this red box contains specific rate limit text
                         if (text.includes("limit") || text.includes("Requests")) {
                             return JSON.stringify({ status: "rate_limit", detail: "Rate limit popup: " + text.substring(0, 20) });
                         }
                         // Otherwise assume general failure
                         return JSON.stringify({ status: "failure", detail: "Red error popup detected: " + text.substring(0, 20) });
                     }
                }
            }
        }
    }

    return JSON.stringify({ status: "pending", detail: "No obvious result yet" });
})();
