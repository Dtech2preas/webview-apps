
(function() {
    console.log("Replayer injected.");

    if (!window.replayEvents || !window.replayStartTime) {
        console.error("Replay data missing");
        return;
    }

    var events = window.replayEvents;
    var startTime = window.replayStartTime;
    var lastIndex = (typeof window.lastExecutedIndex === 'number') ? window.lastExecutedIndex : -1;
    var overrideEmail = window.overrideEmail || null;
    var overridePassword = window.overridePassword || null;

    events.sort(function(a,b){ return a.time - b.time; });

    function simulateEvent(event, index) {
        var el = document.querySelector(event.selector);

        // Smart Wait Logic
        if (!el) {
            console.log("Element not found: " + event.selector + ". Waiting...");
            waitForElement(event.selector, 2000).then(function(foundEl) {
                 if (foundEl) {
                     triggerAction(foundEl, event, index);
                 } else {
                     console.error("Timed out waiting for " + event.selector);
                 }
            });
            return;
        }
        triggerAction(el, event, index);
    }

    function triggerAction(el, event, index) {
        console.log("Executing event #" + index + " (" + event.type + ") at " + (Date.now() - startTime));

        if (event.type === 'click') {
            el.click();
        } else if (event.type === 'input') {
            // SUBSTITUTION LOGIC
            var valToSet = event.value;

            // Check if it's a password field
            if (el.type === 'password' && overridePassword) {
                console.log("Substituting Password");
                valToSet = overridePassword;
            }
            // Check if it's likely an email field
            // Heuristic: Explicit email type OR text type that isn't search/hidden
            // AND user provided an override
            else if (overrideEmail) {
                 var type = (el.type || "").toLowerCase();
                 var name = (el.name || "").toLowerCase();
                 var id = (el.id || "").toLowerCase();

                 var isEmailType = type === 'email';
                 var isTextType = type === 'text';
                 var looksLikeEmail = name.includes("email") || name.includes("user") || name.includes("login") ||
                                      id.includes("email") || id.includes("user");

                 if (isEmailType || (isTextType && looksLikeEmail) || (isTextType && !looksLikeEmail && index === 0)) {
                      // Fallback: if it's the first text input we see, assume it's email if unsure
                      console.log("Substituting Email");
                      valToSet = overrideEmail;
                 }
            }

            // Set value
            var nativeInputValueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
            if (nativeInputValueSetter) {
                nativeInputValueSetter.call(el, valToSet);
            } else {
                el.value = valToSet;
            }

            // Dispatch events to ensure frameworks pick it up
            el.dispatchEvent(new Event('input', { bubbles: true }));
            el.dispatchEvent(new Event('change', { bubbles: true }));

            el.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true }));
            el.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true }));
            el.dispatchEvent(new KeyboardEvent('keypress', { bubbles: true }));

        } else if (event.type === 'scroll') {
            window.scrollTo(event.value.x, event.value.y);
        }

        if (window.Android && window.Android.eventExecuted) {
            window.Android.eventExecuted(index);
        }
    }

    function waitForElement(selector, timeout) {
        return new Promise(function(resolve, reject) {
            var el = document.querySelector(selector);
            if (el) { resolve(el); return; }

            var observer = new MutationObserver(function(mutations, obs) {
                el = document.querySelector(selector);
                if (el) {
                    obs.disconnect();
                    resolve(el);
                }
            });
            observer.observe(document, { childList: true, subtree: true });

            setTimeout(function() {
                observer.disconnect();
                resolve(null);
            }, timeout);
        });
    }

    var now = Date.now();

    events.forEach(function(event, index) {
        if (index <= lastIndex) return;

        var targetTime = startTime + event.time;
        var delay = targetTime - now;

        if (delay < 0) delay = 0;

        setTimeout(function() {
            simulateEvent(event, index);
        }, delay);
    });

})();
