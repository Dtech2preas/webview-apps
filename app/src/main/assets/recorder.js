
(function() {
    if (window.isRecording) return;
    window.isRecording = true;

    // We expect 'recordingStartTime' to be injected
    if (!window.recordingStartTime) {
        window.recordingStartTime = Date.now();
    }

    console.log("Recorder started at " + window.recordingStartTime);

    function getSelector(el) {
        if (!el || el.nodeType !== 1) return null;

        // --- STRATEGY 1: Stable ID ---
        // Avoid using IDs if they contain digits (often dynamic like 'input-123')
        if (el.id && !/\d/.test(el.id)) return '#' + el.id;

        // --- STRATEGY 2: Data Attributes (Gold Standard for Automation) ---
        // These are often used by QA and are very stable.
        var dataAttrs = ["data-testid", "data-test-id", "data-qa", "data-cy", "data-automation-id", "data-component"];
        for (var i = 0; i < dataAttrs.length; i++) {
            if (el.hasAttribute(dataAttrs[i])) {
                var sel = '[' + dataAttrs[i] + '="' + el.getAttribute(dataAttrs[i]).replace(/"/g, '\\"') + '"]';
                if (document.querySelectorAll(sel).length === 1) return sel;
            }
        }

        // --- STRATEGY 3: Unique Name Attribute ---
        // Especially useful for input fields. Must check uniqueness to avoid issues with radio buttons.
        if (el.name && document.getElementsByName(el.name).length === 1) {
             return '[name="' + el.name.replace(/"/g, '\\"') + '"]';
        }

        // --- STRATEGY 4: Accessibility Attributes ---
        if (el.hasAttribute('aria-label')) {
             var sel = '[aria-label="' + el.getAttribute('aria-label').replace(/"/g, '\\"') + '"]';
             if (document.querySelectorAll(sel).length === 1) return sel;
        }

        // --- STRATEGY 5: Placeholder ---
        if (el.placeholder) {
             var sel = '[placeholder="' + el.placeholder.replace(/"/g, '\\"') + '"]';
             if (document.querySelectorAll(sel).length === 1) return sel;
        }

        // --- STRATEGY 6: Path Fallback (Original Logic) ---
        var path = [];
        var current = el;
        while (current && current.nodeType === 1) {
            var selector = current.nodeName.toLowerCase();
            // Check ID at this level too
            if (current.id && !/\d/.test(current.id)) {
                selector = '#' + current.id;
                path.unshift(selector);
                break;
            } else {
                var sib = current, nth = 1;
                while (sib = sib.previousElementSibling) {
                    if (sib.nodeName.toLowerCase() == selector)
                       nth++;
                }
                if (nth != 1)
                    selector += ":nth-of-type("+nth+")";
            }
            path.unshift(selector);
            current = current.parentNode;
        }
        return path.join(" > ");
    }

    function recordEvent(type, target, value, extra) {
        var event = {
            type: type,
            selector: getSelector(target),
            time: Date.now() - window.recordingStartTime,
            url: window.location.href,
            value: value,

            // Robust Attributes
            tagName: target.tagName.toLowerCase(),
            id: target.id || "",
            name: target.name || "",
            className: target.className || "",
            innerText: (target.innerText || "").trim().substring(0, 50), // Cap length
            textContent: (target.textContent || "").trim().substring(0, 50),
            placeholder: target.placeholder || "",
            href: (target.href || ""),
            inputType: target.type || "" // for input elements
        };

        // Merge extra properties (like coordinates)
        if (extra) {
            for (var key in extra) {
                event[key] = extra[key];
            }
        }

        // Send directly to Java to persist across page loads
        if (window.Android && window.Android.recordEvent) {
             window.Android.recordEvent(JSON.stringify(event));
        }
        console.log("Recorded: ", JSON.stringify(event));
    }

    // CLICK
    document.addEventListener('click', function(e) {
        // Check if selection mode is active
        if (window.selectionModeActive) {
            e.preventDefault();
            e.stopPropagation();

            var selector = getSelector(e.target);
            var tagName = e.target.tagName.toLowerCase();
            var innerText = (e.target.innerText || "").trim().substring(0, 30);

            // Confirm with user logic in Android is triggered by this
            if (window.Android && window.Android.onSuccessElementSelected) {
                window.Android.onSuccessElementSelected(selector);
            }

            // Disable mode after one click
            disableSelectionMode();
            return;
        }

        // Don't record clicks on recorder UI if we ever add one
        recordEvent('click', e.target, null, {
            x: e.pageX,
            y: e.pageY
        });
    }, true);

    // --- Success Analysis & Selection Mode ---

    window.initialBodyText = document.body.innerText || "";

    window.analyzeSuccessState = function() {
        var currentText = (document.body.innerText || "").toLowerCase();

        // Define common success indicators
        var candidates = ["logout", "sign out", "my account", "deposit", "balance", "profile", "settings", "welcome"];
        var found = [];

        candidates.forEach(function(word) {
            // Simple check: exists now
            // Better check: exists now AND (maybe didn't exist before? or just exists is enough for these specific words)
            // For robust 'logout', it usually doesn't exist on login page.
            if (currentText.includes(word)) {
                found.push(word);
            }
        });

        return JSON.stringify(found);
    };

    // UI Helpers for Selection Mode
    var styleElement = null;

    window.enableSelectionMode = function() {
        window.selectionModeActive = true;

        // Add highlight styles
        styleElement = document.createElement('style');
        styleElement.innerHTML = `
            * { cursor: crosshair !important; }
            .dtech-highlight { outline: 2px solid #ff0000 !important; background-color: rgba(255, 0, 0, 0.1) !important; }
        `;
        document.head.appendChild(styleElement);

        document.addEventListener('mouseover', onMouseOver, true);
        document.addEventListener('mouseout', onMouseOut, true);

        console.log("Selection Mode Enabled");
    };

    function disableSelectionMode() {
        window.selectionModeActive = false;
        if (styleElement) {
            document.head.removeChild(styleElement);
            styleElement = null;
        }
        document.removeEventListener('mouseover', onMouseOver, true);
        document.removeEventListener('mouseout', onMouseOut, true);
        console.log("Selection Mode Disabled");
    }

    function onMouseOver(e) {
        if (!window.selectionModeActive) return;
        e.target.classList.add('dtech-highlight');
    }

    function onMouseOut(e) {
        if (!window.selectionModeActive) return;
        e.target.classList.remove('dtech-highlight');
    }

    // INPUT
    document.addEventListener('input', function(e) {
        recordEvent('input', e.target, e.target.value);
    }, true);

    // SCROLL (Throttled)
    var lastScroll = 0;
    window.addEventListener('scroll', function(e) {
        var now = Date.now();
        if (now - lastScroll > 500) { // 500ms throttle for scroll
            recordEvent('scroll', document.scrollingElement || document.body, {
                x: window.scrollX,
                y: window.scrollY
            });
            lastScroll = now;
        }
    }, true);

    console.log("Recorder injected successfully.");
})();
