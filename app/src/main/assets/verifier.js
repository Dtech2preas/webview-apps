
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

    // Check DOM for error messages
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

    return JSON.stringify({ status: "pending", detail: "No obvious result yet" });
})();
