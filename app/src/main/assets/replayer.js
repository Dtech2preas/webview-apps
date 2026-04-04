
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

    var overrideEmail = window.overrideEmail || null;
    var overridePassword = window.overridePassword || null;
    var coordinateMode = window.coordinateMode === true;

    // Derived from the last event in the recording
    var successUrl = "";
    if (events.length > 0) {
        successUrl = events[events.length - 1].url;
    }

    // --- SHADOW DOM PIERCER ---
    // Helper to find all shadow roots in the page
    function findAllShadowRoots(root, roots) {
        roots = roots || [];
        if (root.shadowRoot) {
            roots.push(root.shadowRoot);
            findAllShadowRoots(root.shadowRoot, roots);
        }
        var children = root.children || root.childNodes;
        if (children) {
            for (var i = 0; i < children.length; i++) {
                if (children[i].nodeType === 1) { // Element node
                    findAllShadowRoots(children[i], roots);
                }
            }
        }
        return roots;
    }

    function querySelectorAllDeep(selector) {
        var allRoots = [document];
        findAllShadowRoots(document.documentElement, allRoots);
        var results = [];
        for (var i = 0; i < allRoots.length; i++) {
            var els = allRoots[i].querySelectorAll(selector);
            for (var j = 0; j < els.length; j++) {
                results.push(els[j]);
            }
        }
        return results;
    }

    // Helper to find text deeply across shadow boundaries
    function findElementByTextDeep(text, isExact) {
        if (!text) return null;
        var cleanText = text.replace(/'/g, "\\'");
        var allRoots = [document];
        findAllShadowRoots(document.documentElement, allRoots);

        for (var r = 0; r < allRoots.length; r++) {
            var root = allRoots[r];
            // Since XPath doesn't work well across shadow roots easily from the top,
            // we use TreeWalker
            var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null, false);
            var node;
            while (node = walker.nextNode()) {
                var nodeText = node.nodeValue.trim();
                if (isExact ? nodeText === text : nodeText.includes(text)) {
                    // Return the parent element of the text node
                    var parent = node.parentElement;
                    if (parent && parent.tagName !== 'SCRIPT' && parent.tagName !== 'STYLE') {
                        return parent;
                    }
                }
            }
        }
        return null;
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
            var deepEls = querySelectorAllDeep('[name="' + event.name + '"]');
            if (deepEls.length > 0) return deepEls[0];
        }

        // 2. Text Content (Deep Search - Buttons, Links, Labels)
        if (event.innerText && event.innerText.length > 1) {
             // Try exact match first (deep)
             var exactDeep = findElementByTextDeep(event.innerText, true);
             if (exactDeep) return exactDeep;

             // Try contains (deep)
             var containsDeep = findElementByTextDeep(event.innerText, false);
             if (containsDeep) return containsDeep;
        }

        // 3. Placeholder
        if (event.placeholder) {
             var deepEls = querySelectorAllDeep('[placeholder="' + event.placeholder + '"]');
             if (deepEls.length > 0) return deepEls[0];
        }

        // 4. Href (Links)
        if (event.href) {
             var strictDeep = querySelectorAllDeep('a[href="' + event.href + '"]');
             if (strictDeep.length > 0) return strictDeep[0];
             var looseDeep = querySelectorAllDeep('a[href*="' + event.href + '"]');
             if (looseDeep.length > 0) return looseDeep[0];
        }

        // 5. Robust XPath (New from Recorder)
        if (event.xpath) {
            try {
                var resXPath = document.evaluate(event.xpath, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null);
                if (resXPath.singleNodeValue) return resXPath.singleNodeValue;
            } catch(e) {}
        }

        // 6. Fallback: CSS Selector (Deep)
        if (event.selector) {
            try {
                var deepEls = querySelectorAllDeep(event.selector);
                if (deepEls.length > 0) return deepEls[0];
            } catch(e) {}
        }

        // 7. Class Name (Weakest, Deep Search)
        if (event.className) {
            try {
                var classes = event.className.split(" ").filter(c => c.length > 5);
                if (classes.length > 0) {
                     var deepEls = querySelectorAllDeep("." + classes.join("."));
                     if (deepEls.length > 0) return deepEls[0];
                }
            } catch(e) {}
        }

        return null;
    }

    // --- POLL & WAIT ---
    function waitForElementRobust(event, timeoutMs) {
        return new Promise(function(resolve, reject) {
            var startTime = Date.now();

            function checkConditions(el) {
                var style = window.getComputedStyle(el);

                // Be more lenient with visibility - modals sometimes use opacity: 0 but pointer-events: auto, or vice versa.
                // Or transform scaling. If it has width/height, consider it potentially visible unless explicitly none/hidden.
                var isVisible = style.display !== 'none' &&
                                style.visibility !== 'hidden' &&
                                (el.offsetWidth > 0 || el.offsetHeight > 0 || el.getClientRects().length > 0);

                var isDisabled = el.disabled === true || el.getAttribute("aria-disabled") === "true";

                // document.body.contains(el) fails for elements in Shadow DOM.
                // Use composedPath() to check if it's connected to the main document.
                var isConnected = el.isConnected;

                return isVisible && !isDisabled && isConnected;
            }

            // Polling is more robust than MutationObserver here because Shadow DOM changes might not bubble up,
            // and animations/CSS transitions don't reliably trigger MutationObserver.
            var pollInterval = setInterval(function() {
                var el = findTarget(event);
                if (el && checkConditions(el)) {
                    clearInterval(pollInterval);
                    clearTimeout(timeoutId);

                    // Modals often animate in. Let's add a tiny buffer after we detect it's "visible"
                    // to let animations settle, which prevents intercept errors.
                    setTimeout(function() {
                        resolve(el);
                    }, 500);
                }
            }, 500);

            var timeoutId = setTimeout(function() {
                clearInterval(pollInterval);
                // Final check before failing
                var el = findTarget(event);
                if (el && checkConditions(el)) {
                    resolve(el);
                } else {
                    resolve(null);
                }
            }, timeoutMs);
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

            // Force Coordinate Click Logic (Blindly click coordinates)
            if (event.type === 'force_coordinate_click') {
                console.log("Force Coordinate Click Mode: Clicking at " + event.x + ", " + event.y);

                // Scroll to target (centered)
                var targetX = event.x - (window.innerWidth / 2);
                var targetY = event.y - (window.innerHeight / 2);
                window.scrollTo(targetX, targetY);

                // Wait for scroll
                setTimeout(function() {
                     // Calculate Client Coordinates (Viewport relative)
                     var clientX = event.x - window.pageXOffset;
                     var clientY = event.y - window.pageYOffset;

                     // Dispatch click blindly
                     var clickEvent = new MouseEvent('click', {
                         view: window, bubbles: true, cancelable: true, clientX: clientX, clientY: clientY
                     });
                     var pointerDown = new PointerEvent('pointerdown', {
                         view: window, bubbles: true, cancelable: true, clientX: clientX, clientY: clientY
                     });
                     var mouseDown = new MouseEvent('mousedown', {
                         view: window, bubbles: true, cancelable: true, clientX: clientX, clientY: clientY
                     });
                     var pointerUp = new PointerEvent('pointerup', {
                         view: window, bubbles: true, cancelable: true, clientX: clientX, clientY: clientY
                     });
                     var mouseUp = new MouseEvent('mouseup', {
                         view: window, bubbles: true, cancelable: true, clientX: clientX, clientY: clientY
                     });

                     var el = document.elementFromPoint(clientX, clientY) || document.body;

                     try { el.focus(); } catch(e) {}
                     el.dispatchEvent(pointerDown);
                     el.dispatchEvent(mouseDown);
                     el.dispatchEvent(pointerUp);
                     el.dispatchEvent(mouseUp);
                     el.dispatchEvent(clickEvent);

                     if (window.Android && window.Android.eventExecuted) window.Android.eventExecuted(index);
                     setTimeout(function() { processNextEvent(index + 1); }, 500);
                }, 300);

                return;
            }

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
                    console.warn("Could not find element for event #" + index + " via selectors.");

                    // --- EXTREME FALLBACK: Coordinate Force Click ---
                    if (event.type === 'click' && event.clientX !== undefined && event.clientY !== undefined) {
                        console.log("Attempting fallback coordinate click at " + event.clientX + ", " + event.clientY);
                        var fallbackEl = document.elementFromPoint(event.clientX, event.clientY) || document.body;
                        try {
                            triggerAction(fallbackEl, event, index, { x: event.clientX, y: event.clientY });
                        } catch(e) {
                            console.error("Fallback coordinate click failed", e);
                        }
                    } else {
                        console.error("No element and no coordinate fallback possible. Stopping here.");
                        return; // Stop execution
                    }
                } else {
                    // Execute normal action
                    try {
                        triggerAction(el, event, index);
                    } catch (e) {
                        console.error("Error executing event #" + index, e);
                    }
                }

                // Notify Android
                if (window.Android && window.Android.eventExecuted) {
                    window.Android.eventExecuted(index);
                }

                // Schedule Next
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
        // Bring element into view first. Modals might be off-screen or part of a scrolling container.
        try {
            if (el && el.scrollIntoView && el !== document.body) {
                el.scrollIntoView({ behavior: 'auto', block: 'center', inline: 'center' });
            }
        } catch(e) {}

        if (event.type === 'click') {
            // REMOVE DISABLED ATTRIBUTE
            if (el && el.hasAttribute && el.hasAttribute('disabled')) {
                console.log("Removing 'disabled' attribute from target element");
                el.removeAttribute('disabled');
            }

            var clientX, clientY;

            if (forcedCoords) {
                clientX = forcedCoords.x;
                clientY = forcedCoords.y;
            } else {
                var rect = el.getBoundingClientRect();
                var cx = rect.left + (rect.width / 2);
                var cy = rect.top + (rect.height / 2);

                // OVERLAY PIERCING LOGIC:
                // Check if an overlay is blocking our target element
                // We use document.elementFromPoint on the center of our target element.
                // If the element found is NOT our target (and not a descendant of it),
                // it means an overlay is blocking it. We should dispatch the click to the overlay!
                var topElement = document.elementFromPoint(cx, cy);
                if (topElement && topElement !== el && !el.contains(topElement)) {
                    console.log("Overlay detected! Target:", el, "Top element:", topElement);
                    // Use the top element instead to simulate what a real user click would do
                    el = topElement;
                }

                // Minor randomization
                var rx = (Math.random() * 4) - 2;
                var ry = (Math.random() * 4) - 2;
                clientX = cx + rx;
                clientY = cy + ry;
            }

            var clickEvent = new MouseEvent('click', {
                view: window, bubbles: true, cancelable: true, clientX: clientX, clientY: clientY
            });
            var pointerDown = new PointerEvent('pointerdown', {
                view: window, bubbles: true, cancelable: true, clientX: clientX, clientY: clientY
            });
            var mouseDown = new MouseEvent('mousedown', {
                view: window, bubbles: true, cancelable: true, clientX: clientX, clientY: clientY
            });
            var pointerUp = new PointerEvent('pointerup', {
                view: window, bubbles: true, cancelable: true, clientX: clientX, clientY: clientY
            });
            var mouseUp = new MouseEvent('mouseup', {
                view: window, bubbles: true, cancelable: true, clientX: clientX, clientY: clientY
            });

            try { el.focus(); } catch(e) {}
            el.dispatchEvent(pointerDown);
            el.dispatchEvent(mouseDown);
            el.dispatchEvent(pointerUp);
            el.dispatchEvent(mouseUp);
            el.dispatchEvent(clickEvent);

            // Native fallback (only if not coordinate mode, or if forcedCoords matched something useful)
            if (!coordinateMode || (forcedCoords && el !== document.body)) {
                 setTimeout(function(){
                     try { el.click(); } catch(e){}
                     // Also try submitting if it's a form button and we clicked
                     try { if (el.form) el.form.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true })); } catch(e){}
                 }, 10);
            }

        } else if (event.type === 'input') {
            // SUBSTITUTION LOGIC
            var valToSet = event.value;

            // Password
            if ((el.type === 'password' || event.inputType === 'password') && overridePassword) {
                console.log("Substituting Password");
                valToSet = overridePassword;
            }
            // Email/User
            else if (overrideEmail) {
                 var type = (el.type || "").toLowerCase();
                 var name = (el.name || "").toLowerCase();
                 var id = (el.id || "").toLowerCase();

                 var isEmailType = type === 'email';
                 var isPhoneType = type === 'tel';
                 var isTextType = type === 'text';
                 var isNumberType = type === 'number';

                 var combinedName = name + id;
                 // Expanded keywords to include phone/mobile related terms
                 // Removed generic "id" and "number" to avoid false positives (e.g. street_number)
                 var credentialKeywords = ["email", "user", "login", "phone", "mobile", "cell", "msisdn", "account"];

                 var looksLikeCredential = false;
                 for (var k = 0; k < credentialKeywords.length; k++) {
                     if (combinedName.includes(credentialKeywords[k])) {
                         looksLikeCredential = true;
                         break;
                     }
                 }

                 // Conditions to substitute:
                 // 1. Explicit Email or Tel type
                 // 2. Text or Number type AND looks like a credential field (keyword match)
                 // 3. Text or Number type AND is the very first event (fallback heuristic)
                 if (isEmailType || isPhoneType ||
                    ((isTextType || isNumberType) && looksLikeCredential) ||
                    ((isTextType || isNumberType) && !looksLikeCredential && index === 0)) {

                      console.log("Substituting Email/Phone");
                      valToSet = overrideEmail;
                 }
            }

            // Focus first
            try { el.focus(); } catch(e){}

            // React/Angular Value Setter workaround
            var nativeInputValueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
            if (nativeInputValueSetter) {
                nativeInputValueSetter.call(el, valToSet);
            } else {
                el.value = valToSet;
            }

            el.dispatchEvent(new Event('input', { bubbles: true }));
            el.dispatchEvent(new Event('change', { bubbles: true }));
            // Dispatch key events to simulate typing
            el.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, key: valToSet.slice(-1) }));
            el.dispatchEvent(new KeyboardEvent('keypress', { bubbles: true, key: valToSet.slice(-1) }));
            el.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true, key: valToSet.slice(-1) }));

            // Blur to trigger validation on some forms
            try { el.blur(); } catch(e){}

        } else if (event.type === 'scroll') {
            window.scrollTo(event.value.x, event.value.y);
        }
    }

    // Start
    processNextEvent(startIndex);

})();
