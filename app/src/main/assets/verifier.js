
(function() {
    // Check for success or failure indicators
    // Returns JSON string: { status: "success" | "failure" | "pending" }

    // Logic for Crunchyroll Login
    // Success: URL is NOT /login OR "You have been successfully logged in" is visible
    // Failure: "Email or password is incorrect" is visible OR "Something went wrong"

    var currentUrl = window.location.href;

    // Check URL first (Strongest indicator of success)
    if (!currentUrl.includes("/login")) {
         return JSON.stringify({ status: "success", detail: "Redirected away from login" });
    }

    // Check DOM for specific error messages
    var bodyText = document.body.innerText || "";
    if (bodyText.includes("Email or password is incorrect") ||
        bodyText.includes("Something went wrong") ||
        bodyText.includes("Invalid login")) {
        return JSON.stringify({ status: "failure", detail: "Error message found" });
    }

    // Check DOM for success messages (just in case URL doesn't change immediately)
    if (bodyText.includes("successfully logged in")) {
         return JSON.stringify({ status: "success", detail: "Success text found" });
    }

    // Check for "Red Popup" or generic error indicators at the top of the page
    // We look for elements with red background colors that contain text
    var allElements = document.getElementsByTagName("*");
    for (var i = 0; i < allElements.length; i++) {
        var el = allElements[i];
        // Optimization: Only check elements near the top
        var rect = el.getBoundingClientRect();
        if (rect.top < 200 && rect.height > 0 && rect.width > 0) {
            var style = window.getComputedStyle(el);
            var bg = style.backgroundColor; // returns "rgb(r, g, b)" or "rgba..."

            // Check for reddish colors (High R, Low G/B)
            // Regex to parse rgb(r, g, b)
            var rgbMatch = bg.match(/^rgba?\((\d+),\s*(\d+),\s*(\d+)/);
            if (rgbMatch) {
                var r = parseInt(rgbMatch[1]);
                var g = parseInt(rgbMatch[2]);
                var b = parseInt(rgbMatch[3]);

                // Heuristic: Red > 150, Green & Blue < 100 -> likely a red error box
                if (r > 150 && g < 100 && b < 100) {
                     // Check if it has text content (avoid empty containers)
                     if (el.innerText && el.innerText.trim().length > 0) {
                         // Found a red box with text near the top
                         return JSON.stringify({ status: "failure", detail: "Red error popup detected: " + el.innerText.substring(0, 20) });
                     }
                }
            }
        }
    }

    return JSON.stringify({ status: "pending", detail: "No obvious result yet" });
})();
