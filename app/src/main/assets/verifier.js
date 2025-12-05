(function() {
    // Check for success or failure indicators
    // Returns JSON string: { status: "success" | "failure" | "pending" | "rate_limit" | "challenge" }

    var currentUrl = window.location.href;
    var rawBodyText = document.body.innerText || "";
    var bodyText = rawBodyText.toLowerCase();

    // Injected variables from Java
    var targetSuccessUrl = window.targetSuccessUrl || "";
    // failureKeywords is expected to be a JSON array of strings, injected as window.failureKeywords
    var failureKeywords = window.failureKeywords || [];

    // CHALLENGE DETECT
    if (bodyText.includes("challenge") ||
        bodyText.includes("cloudflare") ||
        bodyText.includes("verify you are human") ||
        currentUrl.includes("challenge")) {
        return JSON.stringify({ status: "challenge", detail: "Challenge detected" });
    }

    // FAILURE KEYWORDS CHECK (High Priority)
    // If specific keywords were recorded, use them
    if (failureKeywords && failureKeywords.length > 0) {
        for (var i = 0; i < failureKeywords.length; i++) {
            var kw = failureKeywords[i].toLowerCase();
            if (bodyText.includes(kw)) {
                 return JSON.stringify({ status: "failure", detail: "Failure keyword found: " + kw });
            }
        }
    }

    // STRICT SUCCESS CHECK
    // If a target URL is provided, we ONLY accept that as success
    if (targetSuccessUrl && targetSuccessUrl.length > 5) {
        // Strip trailing slashes for comparison
        var cleanCurrent = currentUrl.replace(/\/$/, "").toLowerCase();
        var cleanTarget = targetSuccessUrl.replace(/\/$/, "").toLowerCase();

        // Check for exact match or if current URL starts with target (e.g. /dashboard -> /dashboard/home)
        if (cleanCurrent === cleanTarget || cleanCurrent.startsWith(cleanTarget)) {
            return JSON.stringify({ status: "success", detail: "Target URL reached" });
        }
    }

    // Rate Limit Checks (Generic)
    if (bodyText.includes("limit") && bodyText.includes("reached")) {
        return JSON.stringify({ status: "rate_limit", detail: "Rate limit detected (limit reached)" });
    }
    if (bodyText.includes("too many requests") ||
        bodyText.includes("try again later")) {
        return JSON.stringify({ status: "rate_limit", detail: "Rate limit detected (generic)" });
    }

    // GENERIC FALLBACKS (If no specific keywords matched)
    if (bodyText.includes("incorrect")) {
        return JSON.stringify({ status: "failure", detail: "Incorrect credential detected (generic)" });
    }
    if ((bodyText.includes("email") || bodyText.includes("password")) &&
        (bodyText.includes("invalid") || bodyText.includes("error"))) {
        return JSON.stringify({ status: "failure", detail: "Login error detected (generic)" });
    }

    return JSON.stringify({ status: "pending", detail: "Waiting for result..." });
})();
