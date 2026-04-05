
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

    function getRobustXPath(el) {
        if (!el || el.nodeType !== 1) return '';
        if (el.id && !/\d/.test(el.id)) return '//*[@id="' + el.id + '"]';

        var paths = [];
        for (; el && el.nodeType === 1; el = el.parentNode) {
            var index = 0;
            var hasFollowingSiblings = false;
            for (var sibling = el.previousSibling; sibling; sibling = sibling.previousSibling) {
                if (sibling.nodeType === Node.DOCUMENT_TYPE_NODE) continue;
                if (sibling.nodeName === el.nodeName) ++index;
            }
            for (var sibling = el.nextSibling; sibling && !hasFollowingSiblings; sibling = sibling.nextSibling) {
                if (sibling.nodeName === el.nodeName) hasFollowingSiblings = true;
            }

            var tagName = el.nodeName.toLowerCase();
            var pathIndex = (index || hasFollowingSiblings ? "[" + (index + 1) + "]" : "");
            paths.splice(0, 0, tagName + pathIndex);
        }

        return paths.length ? "/" + paths.join("/") : null;
    }

    function recordEvent(type, target, value, extra) {
        var event = {
            type: type,
            selector: getSelector(target),
            xpath: getRobustXPath(target),
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

    // Force Click Mode Logic
    window.enableForceClickMode = function() {
        window.forceClickModeActive = true;
        // Optionally add an overlay to capture the click visually, but listening globally works.
    };

    // Force Area Selection Mode Logic
    var drawBoxOverlay = null;
    var drawBoxRect = null;
    var isDrawingBox = false;
    var startBoxX = 0, startBoxY = 0;

    window.enableForceAreaSelectionMode = function() {
        window.forceAreaSelectionModeActive = true;
        // Re-use highlight styles
        window.enableSelectionModeStylesOnly();

        // Add a full screen transparent overlay for drawing
        if (!drawBoxOverlay) {
            drawBoxOverlay = document.createElement('div');
            drawBoxOverlay.style.position = 'fixed';
            drawBoxOverlay.style.top = '0';
            drawBoxOverlay.style.left = '0';
            drawBoxOverlay.style.width = '100vw';
            drawBoxOverlay.style.height = '100vh';
            drawBoxOverlay.style.zIndex = '999999';
            drawBoxOverlay.style.cursor = 'crosshair';
            // Allow pointer events so we can catch drags, but also allow clicks to fall through?
            // Actually, we need pointer-events: auto to catch mousedown.
            // But we also want to allow element selection.
            // Let's not use an overlay, let's just listen to document mousedown/mousemove/mouseup.
            // Since we can't easily capture both without blocking, let's just listen on document globally.
        }
    };

    function disableForceAreaSelectionMode() {
        window.forceAreaSelectionModeActive = false;
        disableSelectionModeStylesOnly();
        if (drawBoxRect && drawBoxRect.parentNode) {
            drawBoxRect.parentNode.removeChild(drawBoxRect);
        }
        drawBoxRect = null;
    }

    // Handle Drag Drawing Box
    document.addEventListener('mousedown', function(e) {
        if (!window.forceAreaSelectionModeActive) return;
        isDrawingBox = true;
        startBoxX = e.clientX;
        startBoxY = e.clientY;

        if (!drawBoxRect) {
            drawBoxRect = document.createElement('div');
            drawBoxRect.style.position = 'fixed';
            drawBoxRect.style.border = '2px dashed #00E5FF';
            drawBoxRect.style.backgroundColor = 'rgba(0, 229, 255, 0.2)';
            drawBoxRect.style.zIndex = '999999';
            drawBoxRect.style.pointerEvents = 'none'; // let mouse events pass through
            document.body.appendChild(drawBoxRect);
        }
        drawBoxRect.style.left = startBoxX + 'px';
        drawBoxRect.style.top = startBoxY + 'px';
        drawBoxRect.style.width = '0px';
        drawBoxRect.style.height = '0px';
    }, true);

    document.addEventListener('mousemove', function(e) {
        if (!isDrawingBox) return;
        var currentX = e.clientX;
        var currentY = e.clientY;

        var left = Math.min(startBoxX, currentX);
        var top = Math.min(startBoxY, currentY);
        var width = Math.abs(currentX - startBoxX);
        var height = Math.abs(currentY - startBoxY);

        if (drawBoxRect) {
            drawBoxRect.style.left = left + 'px';
            drawBoxRect.style.top = top + 'px';
            drawBoxRect.style.width = width + 'px';
            drawBoxRect.style.height = height + 'px';
        }
    }, true);

    document.addEventListener('mouseup', function(e) {
        if (!isDrawingBox || !window.forceAreaSelectionModeActive) return;
        isDrawingBox = false;

        var endX = e.clientX;
        var endY = e.clientY;

        var width = Math.abs(endX - startBoxX);
        var height = Math.abs(endY - startBoxY);

        // If the user actually dragged a box (e.g. > 10x10 pixels), use that as the area.
        // Otherwise, let the click handler use the DOM element.
        if (width > 10 && height > 10) {
            e.preventDefault();
            e.stopPropagation();

            var rect = {
                left: Math.min(startBoxX, endX) + window.scrollX,
                top: Math.min(startBoxY, endY) + window.scrollY,
                width: width,
                height: height
            };

            var event = {
                type: 'force_click_area',
                selector: 'drawn_box',
                time: Date.now() - window.recordingStartTime,
                url: window.location.href,
                rect: rect
            };

            if (window.Android && window.Android.recordEvent) {
                window.Android.recordEvent(JSON.stringify(event));
            }
            console.log("Recorded Force Click Area (Drawn Box): ", JSON.stringify(event));

            disableForceAreaSelectionMode();

            // Prevent the subsequent click event from firing by temporarily capturing it
            var captureClick = function(ev) {
                ev.preventDefault();
                ev.stopPropagation();
                document.removeEventListener('click', captureClick, true);
            };
            document.addEventListener('click', captureClick, true);
            setTimeout(function() { document.removeEventListener('click', captureClick, true); }, 100);
        } else {
            // It was just a click, cleanup the box and let the click handler handle the DOM element
            if (drawBoxRect && drawBoxRect.parentNode) {
                drawBoxRect.parentNode.removeChild(drawBoxRect);
            }
            drawBoxRect = null;
        }
    }, true);

    window.enableSelectionModeStylesOnly = function() {
        if (!styleElement) {
            styleElement = document.createElement('style');
            styleElement.innerHTML = `
                * { cursor: crosshair !important; }
                .dtech-highlight { outline: 2px solid #ff0000 !important; background-color: rgba(255, 0, 0, 0.1) !important; }
            `;
            document.head.appendChild(styleElement);
            document.addEventListener('mouseover', onMouseOver, true);
            document.addEventListener('mouseout', onMouseOut, true);
        }
    };

    function disableSelectionModeStylesOnly() {
        if (styleElement) {
            document.head.removeChild(styleElement);
            styleElement = null;
        }
        document.removeEventListener('mouseover', onMouseOver, true);
        document.removeEventListener('mouseout', onMouseOut, true);
    }

    // CLICK
    document.addEventListener('click', function(e) {
        if (window.forceClickModeActive) {
            e.preventDefault();
            e.stopPropagation();

            var extra = {
                x: e.pageX,
                y: e.pageY,
                clientX: e.clientX,
                clientY: e.clientY
            };

            // Record a special force click event using coordinates only
            var event = {
                type: 'force_coordinate_click',
                time: Date.now() - window.recordingStartTime,
                url: window.location.href,
                x: e.pageX,
                y: e.pageY,
                clientX: e.clientX,
                clientY: e.clientY
            };

            if (window.Android && window.Android.recordEvent) {
                window.Android.recordEvent(JSON.stringify(event));
            }
            console.log("Recorded Force Coordinate Click: ", JSON.stringify(event));

            // Disable force click mode after one use
            window.forceClickModeActive = false;
            return;
        }

        // Check if Force Area Selection mode is active (for single clicks / DOM elements)
        if (window.forceAreaSelectionModeActive) {
            e.preventDefault();
            e.stopPropagation();

            var rect = e.target.getBoundingClientRect();
            var extra = {
                x: e.pageX,
                y: e.pageY,
                clientX: e.clientX,
                clientY: e.clientY,
                rect: {
                    top: rect.top + window.scrollY,
                    left: rect.left + window.scrollX,
                    width: rect.width,
                    height: rect.height
                }
            };

            var event = {
                type: 'force_click_area',
                selector: getSelector(e.target),
                time: Date.now() - window.recordingStartTime,
                url: window.location.href,
                rect: extra.rect
            };

            if (window.Android && window.Android.recordEvent) {
                window.Android.recordEvent(JSON.stringify(event));
            }
            console.log("Recorded Force Click Area (Element): ", JSON.stringify(event));

            disableForceAreaSelectionMode();
            return;
        }

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

        // Capture both page-relative (for scrolling) and viewport-relative (for elementFromPoint)
        // Also capture bounding box if possible to help with center-clicking
        var extra = {
            x: e.pageX,
            y: e.pageY,
            clientX: e.clientX,
            clientY: e.clientY
        };

        if (e.target && e.target.getBoundingClientRect) {
            var rect = e.target.getBoundingClientRect();
            extra.rect = {
                top: rect.top,
                left: rect.left,
                width: rect.width,
                height: rect.height
            };
        }

        recordEvent('click', e.target, null, extra);
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
        window.enableSelectionModeStylesOnly();
        console.log("Selection Mode Enabled");
    };

    function disableSelectionMode() {
        window.selectionModeActive = false;
        disableSelectionModeStylesOnly();
        console.log("Selection Mode Disabled");
    }

    function onMouseOver(e) {
        if (!window.selectionModeActive && !window.forceAreaSelectionModeActive) return;
        e.target.classList.add('dtech-highlight');
    }

    function onMouseOut(e) {
        if (!window.selectionModeActive && !window.forceAreaSelectionModeActive) return;
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
