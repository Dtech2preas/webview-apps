(function() {
    // Check for success or failure indicators
    // Returns JSON string: { status: "success" | "failure" | "pending" | "rate_limit" | "challenge" }

    var currentUrl = window.location.href;
    var rawBodyText = document.body.innerText || "";
    var bodyText = rawBodyText.toLowerCase();

    // Injected variables from Java
    var loginUrl = window.loginUrl || "";
    var targetSuccessUrl = window.targetSuccessUrl || "";
    var successSelector = window.successSelector || "";
    var successKeywords = window.successKeywords || [];
    var failureKeywords = window.failureKeywords || [];
    var extractionPoints = window.extractionPoints || [];

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

    // CHECK FOR LOADING STATE
    // If the page is still loading, we shouldn't attempt to verify success yet.
    if (document.readyState !== 'complete') {
         return JSON.stringify({ status: "pending", detail: "Page loading..." });
    }

    // SUCCESS CHECK (Priority: ExtractionPoints > Selector > Keywords > URL)

    // 0. Extraction Points (If any exist, they are strong indicators)
    var extractedData = {};
    var foundExtractionPoints = false;

    if (extractionPoints && extractionPoints.length > 0) {
        for (var i = 0; i < extractionPoints.length; i++) {
            var point = extractionPoints[i];
            var el = document.querySelector(point.selector);
            if (el) {
                var text = (el.innerText || "").trim();

                // Validate Pattern if exists
                if (point.pattern && point.pattern.length > 0) {
                    try {
                        var regex = new RegExp(point.pattern);
                        if (!regex.test(text)) {
                            // Found element but pattern mismatch?
                            // User asked: "if it's numbers just record the balance"
                            // So we should probably still accept it if it exists, but maybe log a warning?
                            // Or better: the regex is meant to VALIDATE success.
                            // If user says "Balance: \d+", and we see "Error", it should NOT match.
                            // But if we see "Balance: 123", it matches.

                            // If regex fails, we treat it as NOT FOUND (so we don't count this point as success yet)
                            continue;
                        }
                    } catch(e) {
                        // Bad regex? Ignore pattern validation.
                    }
                }

                if (text.length > 0) {
                    extractedData[point.label] = text;
                    foundExtractionPoints = true;
                }
            }
        }
    }

    if (foundExtractionPoints) {
         // If we found the data the user wanted, that's a success
         return JSON.stringify({
             status: "success",
             detail: "Extracted data points found",
             extractedData: extractedData
         });
    }

    // 1. Specific Element Selector
    if (successSelector && successSelector.length > 0) {
        var el = document.querySelector(successSelector);
        if (el) {
             // Check visibility? Maybe optional, but existence is usually enough for SPA state
             var style = window.getComputedStyle(el);
             if (style.display !== 'none' && style.visibility !== 'hidden') {
                 return JSON.stringify({ status: "success", detail: "Success element found: " + successSelector });
             }
        }
    }

    // 2. Success Keywords
    if (successKeywords && successKeywords.length > 0) {
        var allFound = true; // or anyFound? usually we want at least one strong indicator
        for (var i = 0; i < successKeywords.length; i++) {
            if (!bodyText.includes(successKeywords[i].toLowerCase())) {
                allFound = false;
                break;
            }
        }
        // If we found the keywords, that's a success
        if (allFound) {
             return JSON.stringify({ status: "success", detail: "Success keywords found" });
        }
    }

    // 3. Fallback to URL Check (Only if no specific selector/keywords are enforced)
    // If the user set up selector/keywords, we ignore URL matches because they might be false positives (like on betway)
    var hasSpecificSuccessLogic = (successSelector && successSelector.length > 0) || (successKeywords && successKeywords.length > 0);

    // Standard Target URL Check
    if (!hasSpecificSuccessLogic && targetSuccessUrl && targetSuccessUrl.length > 5) {
        // Strip trailing slashes for comparison
        var cleanCurrent = currentUrl.replace(/\/$/, "").toLowerCase();
        var cleanTarget = targetSuccessUrl.replace(/\/$/, "").toLowerCase();

        // Check for exact match or if current URL starts with target (e.g. /dashboard -> /dashboard/home)
        if (cleanCurrent === cleanTarget || cleanCurrent.startsWith(cleanTarget)) {
            return JSON.stringify({ status: "success", detail: "Target URL reached" });
        }
    }

    // 4. Fallback for Dummy Mode (No target success URL)
    // If we don't have a target success URL, check if URL simply CHANGED from the Login URL.
    if (!hasSpecificSuccessLogic && (!targetSuccessUrl || targetSuccessUrl.length < 5) && loginUrl && loginUrl.length > 5) {
         var cleanCurrent = currentUrl.split('?')[0].replace(/\/$/, "").toLowerCase();
         var cleanLogin = loginUrl.split('?')[0].replace(/\/$/, "").toLowerCase();

         // If we are NOT on the login page anymore, assume success
         if (cleanCurrent !== cleanLogin) {
             return JSON.stringify({ status: "success", detail: "URL Changed (No Verification Set)" });
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
