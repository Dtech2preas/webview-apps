
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

    // Derived from the last event in the recording usually
    var successUrl = "";
    if (events.length > 0) {
        successUrl = events[events.length - 1].url;
    }

    events.sort(function(a,b){ return a.time - b.time; });

    function simulateEvent(event, index) {
        // Challenge / Smart Skip Logic
        if (shouldSkipEvent(event)) {
            console.log("Skipping event #" + index + " (Challenge Skip)");
             if (window.Android && window.Android.eventExecuted) {
                window.Android.eventExecuted(index);
            }
            return;
        }

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

    function shouldSkipEvent(event) {
        if (!event.url) return false;

        var currentUrl = window.location.href.toLowerCase();
        var eventUrl = event.url.toLowerCase();

        // Define what looks like a challenge URL
        var isChallengeEvent = eventUrl.includes("challenge") || eventUrl.includes("captcha") || eventUrl.includes("turnstile");

        // If the event is for a challenge, but we are ALREADY on the success URL, skip it
        if (isChallengeEvent && currentUrl === successUrl.toLowerCase()) {
            return true;
        }

        return false;
    }

    function triggerAction(el, event, index) {
        console.log("Executing event #" + index + " (" + event.type + ") at " + (Date.now() - startTime));

        if (event.type === 'click') {
            // RANDOMIZATION LOGIC
            var rect = el.getBoundingClientRect();
            // Calculate center
            var cx = rect.left + (rect.width / 2);
            var cy = rect.top + (rect.height / 2);

            // Add random offset (+/- 3 pixels)
            var rx = (Math.random() * 6) - 3;
            var ry = (Math.random() * 6) - 3;

            var clientX = cx + rx;
            var clientY = cy + ry;

            // Dispatch synthetic MouseEvents with coordinates
            var clickEvent = new MouseEvent('click', {
                view: window,
                bubbles: true,
                cancelable: true,
                clientX: clientX,
                clientY: clientY
            });

            // Also dispatch mousedown/mouseup for completeness
             var mouseDown = new MouseEvent('mousedown', {
                view: window,
                bubbles: true,
                cancelable: true,
                clientX: clientX,
                clientY: clientY
            });
             var mouseUp = new MouseEvent('mouseup', {
                view: window,
                bubbles: true,
                cancelable: true,
                clientX: clientX,
                clientY: clientY
            });

            el.dispatchEvent(mouseDown);
            el.dispatchEvent(mouseUp);
            el.dispatchEvent(clickEvent);

            // Fallback: Ensure the native click happens if synthetic doesn't trigger action
            // Many frameworks listen to synthetic, but some native forms need .click()
            // We use a small timeout to let the synthetic events bubble first
            setTimeout(function() {
                // Only call native click if the element handles it (like a link or button)
                // and to be safe we don't double submit if the event handled it.
                // But for automation, calling .click() is usually safest.
                // However, doing BOTH might double-click.
                // Let's stick to the synthetic event primarily, but if it's a form submit button, .click() is better.
                // For now, let's assume .click() ensures the action happens.
                // But if we want to be "human", the event dispatch is key.
                // The native .click() does NOT take coordinates.
                // If we want to strictly follow "random pixels", we rely on the dispatched event.
                // BUT, if the site doesn't care about coordinates, .click() is robust.
                // Let's do .click() as backup only? No, let's do .click() always to ensure functionality.
                // The randomization is "logged" via the events, and if the site tracks mouse, it tracks the events.
                el.click();
            }, 10);

        } else if (event.type === 'input') {
            // SUBSTITUTION LOGIC
            var valToSet = event.value;

            // Check if it's a password field
            if (el.type === 'password' && overridePassword) {
                console.log("Substituting Password");
                valToSet = overridePassword;
            }
            // Check if it's likely an email field
            else if (overrideEmail) {
                 var type = (el.type || "").toLowerCase();
                 var name = (el.name || "").toLowerCase();
                 var id = (el.id || "").toLowerCase();

                 var isEmailType = type === 'email';
                 var isTextType = type === 'text';
                 var looksLikeEmail = name.includes("email") || name.includes("user") || name.includes("login") ||
                                      id.includes("email") || id.includes("user");

                 if (isEmailType || (isTextType && looksLikeEmail) || (isTextType && !looksLikeEmail && index === 0)) {
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
