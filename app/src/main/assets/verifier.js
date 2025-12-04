(function() {
    // Check for success or failure indicators
    // Returns JSON string: { status: "success" | "failure" | "pending" | "rate_limit" | "challenge" }

    var currentUrl = window.location.href;
    var rawBodyText = document.body.innerText || "";
    var bodyText = rawBodyText.toLowerCase();

    // Injected variable from Java
    var targetSuccessUrl = window.targetSuccessUrl || "";

    // CHALLENGE DETECT
    if (bodyText.includes("challenge") ||
        bodyText.includes("cloudflare") ||
        bodyText.includes("verify you are human") ||
        currentUrl.includes("challenge")) {
        return JSON.stringify({ status: "challenge", detail: "Challenge detected" });
    }

    // STRICT SUCCESS CHECK
    // If a target URL is provided, we ONLY accept that as success
    if (targetSuccessUrl && targetSuccessUrl.length > 5) {
        // Strip trailing slashes for comparison
        var cleanCurrent = currentUrl.replace(/\/$/, "").toLowerCase();
        var cleanTarget = targetSuccessUrl.replace(/\/$/, "").toLowerCase();

        if (cleanCurrent === cleanTarget) {
            return JSON.stringify({ status: "success", detail: "Target URL reached" });
        }
    } else {
        // Fallback to old heuristic if no target provided (backward compatibility)
        if (!currentUrl.includes("/login") && !currentUrl.includes("challenge")) {
             return JSON.stringify({ status: "success", detail: "Redirected away from login" });
        }
    }

    // Rate Limit Checks
    if (bodyText.includes("limit") && bodyText.includes("reached")) {
        return JSON.stringify({ status: "rate_limit", detail: "Rate limit detected (limit reached)" });
    }
    if (bodyText.includes("too many requests") ||
        bodyText.includes("try again later")) {
        return JSON.stringify({ status: "rate_limit", detail: "Rate limit detected (generic)" });
    }

    // Failure Checks
    if (bodyText.includes("incorrect")) {
        return JSON.stringify({ status: "failure", detail: "Incorrect credential detected" });
    }
    if ((bodyText.includes("email") || bodyText.includes("password")) &&
        (bodyText.includes("invalid") || bodyText.includes("error"))) {
        return JSON.stringify({ status: "failure", detail: "Login error detected" });
    }

    // Check for "Red Popup"
    var allElements = document.getElementsByTagName("*");
    for (var i = 0; i < allElements.length; i++) {
        var el = allElements[i];
        var rect = el.getBoundingClientRect();
        if (rect.top < 300 && rect.height > 0 && rect.width > 0) {
            var style = window.getComputedStyle(el);
            var bg = style.backgroundColor;
            var rgbMatch = bg.match(/^rgba?\((\d+),\s*(\d+),\s*(\d+)/);
            if (rgbMatch) {
                var r = parseInt(rgbMatch[1]);
                var g = parseInt(rgbMatch[2]);
                var b = parseInt(rgbMatch[3]);
                if (r > 150 && g < 100 && b < 100) {
                     var text = el.innerText ? el.innerText.trim().toLowerCase() : "";
                     if (text.length > 0) {
                         if (text.includes("limit") || text.includes("reached") || text.includes("requests")) {
                             return JSON.stringify({ status: "rate_limit", detail: "Rate limit popup: " + text.substring(0, 20) });
                         }
                         if (text.includes("incorrect") || text.includes("invalid") || text.includes("error")) {
                             return JSON.stringify({ status: "failure", detail: "Error popup: " + text.substring(0, 20) });
                         }
                         if (text.split(" ").length > 2) {
                             return JSON.stringify({ status: "failure", detail: "Red warning popup: " + text.substring(0, 20) });
                         }
                     }
                }
            }
        }
    }

    return JSON.stringify({ status: "pending", detail: "Waiting for result..." });
})();
