(function() {
    // Check for success or failure indicators
    // Returns JSON string: { status: "success" | "failure" | "pending" | "rate_limit" }

    var currentUrl = window.location.href;
    var rawBodyText = document.body.innerText || "";
    var bodyText = rawBodyText.toLowerCase();

    // Check URL first (Strongest indicator of success)
    if (!currentUrl.includes("/login")) {
         return JSON.stringify({ status: "success", detail: "Redirected away from login" });
    }

    // Rate Limit Checks
    // Keywords: "limit", "reached", "try again", "later"
    if (bodyText.includes("limit") && bodyText.includes("reached")) {
        return JSON.stringify({ status: "rate_limit", detail: "Rate limit detected (limit reached)" });
    }
    if (bodyText.includes("too many requests") ||
        bodyText.includes("try again later")) {
        return JSON.stringify({ status: "rate_limit", detail: "Rate limit detected (generic)" });
    }

    // Failure Checks
    // Keywords: "incorrect", "email", "password"
    if (bodyText.includes("incorrect")) {
        // High confidence failure
        return JSON.stringify({ status: "failure", detail: "Incorrect credential detected" });
    }
    if ((bodyText.includes("email") || bodyText.includes("password")) &&
        (bodyText.includes("invalid") || bodyText.includes("error"))) {
        return JSON.stringify({ status: "failure", detail: "Login error detected" });
    }

    // Check DOM for specific success messages
    if (bodyText.includes("successfully logged in")) {
         return JSON.stringify({ status: "success", detail: "Success text found" });
    }

    // Check for "Red Popup" or generic error indicators at the top of the page
    // The user mentioned "red notification that pop up"
    var allElements = document.getElementsByTagName("*");
    for (var i = 0; i < allElements.length; i++) {
        var el = allElements[i];
        // Optimization: Only check elements near the top
        var rect = el.getBoundingClientRect();
        if (rect.top < 300 && rect.height > 0 && rect.width > 0) { // Increased range slightly
            var style = window.getComputedStyle(el);
            var bg = style.backgroundColor;

            var rgbMatch = bg.match(/^rgba?\((\d+),\s*(\d+),\s*(\d+)/);
            if (rgbMatch) {
                var r = parseInt(rgbMatch[1]);
                var g = parseInt(rgbMatch[2]);
                var b = parseInt(rgbMatch[3]);

                // Heuristic: Red > 150, Green & Blue < 100 -> likely a red error box
                if (r > 150 && g < 100 && b < 100) {
                     var text = el.innerText ? el.innerText.trim().toLowerCase() : "";
                     if (text.length > 0) {
                         // Check if this red box contains specific keywords
                         if (text.includes("limit") || text.includes("reached") || text.includes("requests")) {
                             return JSON.stringify({ status: "rate_limit", detail: "Rate limit popup: " + text.substring(0, 20) });
                         }
                         if (text.includes("incorrect") || text.includes("invalid") || text.includes("error")) {
                             return JSON.stringify({ status: "failure", detail: "Error popup: " + text.substring(0, 20) });
                         }

                         // If it's red and has text, it's very likely an error, but verify it's not just a red button
                         // Buttons usually have short text. Error messages usually > 1 word.
                         if (text.split(" ").length > 2) {
                             return JSON.stringify({ status: "failure", detail: "Red warning popup: " + text.substring(0, 20) });
                         }
                     }
                }
            }
        }
    }

    return JSON.stringify({ status: "pending", detail: "No obvious result yet" });
})();
