
(function() {
    console.log("Replayer injected.");

    if (!window.replayEvents) {
        console.error("Replay data missing");
        return;
    }

    var events = window.replayEvents;
    // Android passes lastExecutedIndex. We start from lastExecutedIndex + 1
    // If undefined, start at 0.
    var startIndex = (typeof window.lastExecutedIndex === 'number') ? window.lastExecutedIndex + 1 : 0;

    var coordinateMode = window.coordinateMode === true;

    // Derived from the last event in the recording
    var successUrl = "";
    if (events.length > 0) {
        successUrl = events[events.length - 1].url;
    }

    // --- STRATEGY FINDER ---
    function findTarget(event) {
        // 0. ID (Strongest)
        if (event.id) {
            var el = document.getElementById(event.id);
            if (el) return el;
        }

        // 1. Name (Inputs)
        if (event.name) {
            var els = document.getElementsByName(event.name);
            if (els.length > 0) return els[0];
            var el = document.querySelector('[name="' + event.name + '"]');
            if (el) return el;
        }

        // 2. Text Content (Buttons, Links, Labels)
        // We use XPath to find elements containing the text
        if (event.innerText && event.innerText.length > 1) {
             var cleanText = event.innerText.replace(/'/g, "\'");
             // Try exact match first
             var xpathExact = "//*[text()='" + cleanText + "']";
             var resExact = document.evaluate(xpathExact, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null);
             if (resExact.singleNodeValue) return resExact.singleNodeValue;

             // Try contains
             var xpathContains = "//*[contains(text(), '" + cleanText + "')]";
             var resContains = document.evaluate(xpathContains, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null);
             if (resContains.singleNodeValue) return resContains.singleNodeValue;
        }

        // 3. Placeholder
        if (event.placeholder) {
             var el = document.querySelector('[placeholder="' + event.placeholder + '"]');
             if (el) return el;
        }

        // 4. Href (Links)
        if (event.href) {
             // Strict match
             var el = document.querySelector('a[href="' + event.href + '"]');
             if (el) return el;
             // Loose match
             el = document.querySelector('a[href*="' + event.href + '"]');
             if (el) return el;
        }

        // 5. Fallback: CSS Selector (Original)
        if (event.selector) {
            try {
                var el = document.querySelector(event.selector);
                if (el) return el;
            } catch(e) {}
        }

        // 6. Class Name (Weakest, but useful if unique)
        if (event.className) {
            try {
                // Only if class name is not too generic
                var classes = event.className.split(" ").filter(c => c.length > 5);
                if (classes.length > 0) {
                     var el = document.querySelector("." + classes.join("."));
                     if (el) return el;
                }
            } catch(e) {}
        }

        return null;
    }

    // --- POLL & WAIT ---
    function waitForElementRobust(event, timeoutMs) {
        return new Promise(function(resolve, reject) {
            var startTime = Date.now();

            function check() {
                var el = findTarget(event);
                if (el) {
                    // Check visibility
                    var style = window.getComputedStyle(el);
                    var isVisible = style.display !== 'none' && style.visibility !== 'hidden' && (el.offsetWidth > 0 || el.offsetHeight > 0);

                    // Extra Stability Check: element must not be disabled
                    var isDisabled = el.disabled === true || el.getAttribute("aria-disabled") === "true";

                    if (isVisible && !isDisabled && document.body.contains(el)) {
                        resolve(el);
                        return;
                    }
                }

                if (Date.now() - startTime > timeoutMs) {
                    resolve(null); // Timed out
                    return;
                }

                setTimeout(check, 200); // Check every 200ms
            }
            check();
        });
    }

    function waitForPageLoad() {
        return new Promise(function(resolve) {
            // Check if document is complete
            if (document.readyState === 'complete') {
                resolve();
                return;
            }
            // Otherwise wait for load event or poll
            var interval = setInterval(function() {
                if (document.readyState === 'complete') {
                    clearInterval(interval);
                    resolve();
                }
            }, 500);

            // Safety timeout for page load (10s)
            setTimeout(function() {
                clearInterval(interval);
                resolve();
            }, 10000);
        });
    }

    // --- EXECUTION LOOP ---
    function processNextEvent(index) {
        if (index >= events.length) {
            console.log("All events executed.");
            return;
        }

        var event = events[index];
        console.log("Processing Event #" + index + ": " + event.type + " " + (event.innerText || event.selector));

        // Smart Skip Logic (Challenge)
        if (shouldSkipEvent(event)) {
             console.log("Skipping event #" + index + " (Challenge Skip)");
             if (window.Android && window.Android.eventExecuted) {
                window.Android.eventExecuted(index);
            }
            // Move to next immediately
            setTimeout(function() { processNextEvent(index + 1); }, 50);
            return;
        }

        // Wait for Page Load before anything
        waitForPageLoad().then(function() {

            // Coordinate Mode Logic (Bypass Element Search for Clicks)
            if (coordinateMode && event.type === 'click' && event.x !== undefined && event.y !== undefined) {
                console.log("Coordinate Mode: Clicking at " + event.x + ", " + event.y);

                // Scroll to target (centered)
                var targetX = event.x - (window.innerWidth / 2);
                var targetY = event.y - (window.innerHeight / 2);
                window.scrollTo(targetX, targetY);

                // Wait for scroll
                setTimeout(function() {
                     // Calculate Client Coordinates (Viewport relative)
                     var clientX = event.x - window.pageXOffset;
                     var clientY = event.y - window.pageYOffset;

                     // Get element at point or fallback to body
                     var el = document.elementFromPoint(clientX, clientY) || document.body;

                     try {
                        triggerAction(el, event, index, { x: clientX, y: clientY });
                     } catch(e) {
                        console.error("Error executing coordinate click", e);
                     }

                     if (window.Android && window.Android.eventExecuted) window.Android.eventExecuted(index);
                     setTimeout(function() { processNextEvent(index + 1); }, 500);
                }, 300);

                return;
            }

            // Wait for Element (Up to 25 seconds!)
            // If it's a scroll event, we don't need to wait for an element (target is usually document)
            if (event.type === 'scroll') {
                 triggerAction(document.body, event, index);
                 if (window.Android && window.Android.eventExecuted) window.Android.eventExecuted(index);
                 setTimeout(function() { processNextEvent(index + 1); }, 100);
                 return;
            }

            waitForElementRobust(event, 25000).then(function(el) {
                if (!el) {
                    console.error("Could not find element for event #" + index);
                    // We stop here. The user must intervene or the batch logic will eventually time out (if configured).
                    return;
                }

                // Execute
                try {
                    triggerAction(el, event, index);
                } catch (e) {
                    console.error("Error executing event #" + index, e);
                }

                // Notify Android
                if (window.Android && window.Android.eventExecuted) {
                    window.Android.eventExecuted(index);
                }

                // Schedule Next
                // Small delay to allow JS handlers to fire and potential navigation to start
                setTimeout(function() {
                    processNextEvent(index + 1);
                }, 100);
            });
        });
    }

    function shouldSkipEvent(event) {
        if (!event.url) return false;
        var currentUrl = window.location.href.toLowerCase();
        var eventUrl = event.url.toLowerCase();
        var isChallengeEvent = eventUrl.includes("challenge") || eventUrl.includes("captcha") || eventUrl.includes("turnstile");
        if (isChallengeEvent && currentUrl === successUrl.toLowerCase()) {
            return true;
        }
        return false;
    }

    function triggerAction(el, event, index, forcedCoords) {
        if (event.type === 'click') {
            var clientX, clientY;

            if (forcedCoords) {
                clientX = forcedCoords.x;
                clientY = forcedCoords.y;
            } else {
                // RANDOMIZATION LOGIC
                var rect = el.getBoundingClientRect();
                var cx = rect.left + (rect.width / 2);
                var cy = rect.top + (rect.height / 2);
                var rx = (Math.random() * 6) - 3;
                var ry = (Math.random() * 6) - 3;
                clientX = cx + rx;
                clientY = cy + ry;
            }

            var clickEvent = new MouseEvent('click', {
                view: window, bubbles: true, cancelable: true, clientX: clientX, clientY: clientY
            });
            var mouseDown = new MouseEvent('mousedown', {
                view: window, bubbles: true, cancelable: true, clientX: clientX, clientY: clientY
            });
            var mouseUp = new MouseEvent('mouseup', {
                view: window, bubbles: true, cancelable: true, clientX: clientX, clientY: clientY
            });

            el.dispatchEvent(mouseDown);
            el.dispatchEvent(mouseUp);
            el.dispatchEvent(clickEvent);

            // Native fallback (only if not coordinate mode, or maybe always?)
            // If coordinate mode, el might be body or something wrong, so be careful with .click()
            // But if we found an elementFromPoint, .click() is good.
            if (!coordinateMode || (forcedCoords && el !== document.body)) {
                 setTimeout(function(){ try { el.click(); } catch(e){} }, 10);
            }

        } else if (event.type === 'input') {
            // SUBSTITUTION LOGIC
            var valToSet = event.value;

            // SMART INTERCEPTOR:
            // If the browser has global overrides set by Java, use them!
            if (window.DTECH_AUTO_EMAIL && window.DTECH_AUTO_PASS) {
                // Heuristic: Is this a password field?
                var inputType = (el.type || "").toLowerCase();

                if (inputType === 'password') {
                    valToSet = window.DTECH_AUTO_PASS;
                    console.log("DTECH: Injecting Batch Password");
                }
                // Heuristic: Is the recorded value an email? OR is the field type email?
                else if (valToSet.includes('@') || inputType === 'email') {
                    valToSet = window.DTECH_AUTO_EMAIL;
                    console.log("DTECH: Injecting Batch Email");
                }
            }

            // React/Angular Value Setter workaround
            var nativeInputValueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
            if (nativeInputValueSetter) {
                nativeInputValueSetter.call(el, valToSet);
            } else {
                el.value = valToSet;
            }

            el.dispatchEvent(new Event('input', { bubbles: true }));
            el.dispatchEvent(new Event('change', { bubbles: true }));
            // Dispatch key events to simulate typing? (Simplified for now)
            el.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true }));
            el.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true }));

        } else if (event.type === 'scroll') {
            window.scrollTo(event.value.x, event.value.y);
        }
    }

    // Start
    processNextEvent(startIndex);

})();
