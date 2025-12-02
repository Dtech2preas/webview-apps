
(function() {
    // Check for success or failure indicators
    // Returns JSON string: { status: "success" | "failure" | "pending" }

    var currentUrl = window.location.href;

    // Check URL first (Strongest indicator of success)
    if (!currentUrl.includes("/login")) {
         return JSON.stringify({ status: "success", detail: "Redirected away from login" });
    }

    // Check for Red Error Popup (Crunchyroll specific)
    // Looking for generic error classes or text content
    var bodyText = document.body.innerText || "";

    // Fast fail check
    if (bodyText.includes("Email or password is incorrect") ||
        bodyText.includes("Something went wrong") ||
        bodyText.includes("Invalid login") ||
        bodyText.includes("Too many requests") ||
        document.querySelector(".error-message") || // Generic guess
        document.querySelector("[class*='error']") // Aggressive class check
        ) {

        // Double check it's not just the word "error" in normal text
        if (bodyText.includes("Email or password is incorrect")) {
            return JSON.stringify({ status: "failure", detail: "Incorrect credentials text found" });
        }
    }

    // Check DOM for success messages (just in case URL doesn't change immediately)
    if (bodyText.includes("successfully logged in")) {
         return JSON.stringify({ status: "success", detail: "Success text found" });
    }

    return JSON.stringify({ status: "pending", detail: "No obvious result yet" });
})();
