
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
    var editableBox = null;
    var editableBoxContainer = null;
    var controlsPanel = null;
    var isDraggingBox = false;
    var dragOffsetX = 0;
    var dragOffsetY = 0;

    function onBoxDragMove(e) {
        if (!isDraggingBox) return;
        var newLeft = e.pageX - dragOffsetX;
        var newTop = e.pageY - dragOffsetY;
        editableBoxContainer.style.left = newLeft + 'px';
        editableBoxContainer.style.top = newTop + 'px';
    }

    function onBoxDragEnd(e) {
        isDraggingBox = false;
    }

    window.enableForceAreaSelectionMode = function() {
        window.forceAreaSelectionModeActive = true;
        // Don't show generic crosshair yet
        disableSelectionModeStylesOnly();

        // If we already have a box, just make sure it's visible. Otherwise create it.
        if (!editableBoxContainer) {
            createEditableBox();
        } else {
            editableBoxContainer.style.display = 'block';
        }
    };

    function createEditableBox() {
        // Container that handles positioning
        editableBoxContainer = document.createElement('div');
        editableBoxContainer.style.position = 'absolute';
        // Start in middle of screen
        var startLeft = window.scrollX + (window.innerWidth / 4);
        var startTop = window.scrollY + (window.innerHeight / 4);
        editableBoxContainer.style.left = startLeft + 'px';
        editableBoxContainer.style.top = startTop + 'px';
        editableBoxContainer.style.zIndex = '999999';
        editableBoxContainer.id = 'dtech-editable-box-container';
        editableBoxContainer.style.pointerEvents = 'auto'; // allow interaction

        // The actual resizable box
        editableBox = document.createElement('div');
        editableBox.style.width = '200px';
        editableBox.style.height = '150px';
        editableBox.style.border = '3px solid #00E5FF';
        editableBox.style.backgroundColor = 'rgba(0, 229, 255, 0.2)';
        editableBox.style.resize = 'both';
        editableBox.style.overflow = 'hidden';
        editableBox.style.position = 'relative';
        editableBox.style.cursor = 'move';
        editableBox.style.boxSizing = 'border-box';

        // Add a small label
        var label = document.createElement('div');
        label.innerText = 'Drag to move, corner to resize';
        label.style.position = 'absolute';
        label.style.top = '5px';
        label.style.left = '5px';
        label.style.color = '#fff';
        label.style.fontSize = '12px';
        label.style.textShadow = '1px 1px 2px #000';
        label.style.pointerEvents = 'none';
        editableBox.appendChild(label);

        // Controls Panel (Confirm / Cancel)
        controlsPanel = document.createElement('div');
        controlsPanel.style.position = 'absolute';
        controlsPanel.style.top = '-35px';
        controlsPanel.style.right = '0px';
        controlsPanel.style.display = 'flex';
        controlsPanel.style.gap = '5px';
        controlsPanel.style.pointerEvents = 'auto';

        var btnConfirm = document.createElement('button');
        btnConfirm.innerText = '✓ Confirm';
        btnConfirm.style.backgroundColor = '#4CAF50';
        btnConfirm.style.color = 'white';
        btnConfirm.style.border = 'none';
        btnConfirm.style.padding = '5px 10px';
        btnConfirm.style.cursor = 'pointer';
        btnConfirm.style.borderRadius = '3px';
        btnConfirm.style.fontWeight = 'bold';

        var btnCancel = document.createElement('button');
        btnCancel.innerText = '✗ Cancel';
        btnCancel.style.backgroundColor = '#f44336';
        btnCancel.style.color = 'white';
        btnCancel.style.border = 'none';
        btnCancel.style.padding = '5px 10px';
        btnCancel.style.cursor = 'pointer';
        btnCancel.style.borderRadius = '3px';
        btnCancel.style.fontWeight = 'bold';

        btnConfirm.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            confirmForceClickArea();
        }, true);

        btnConfirm.addEventListener('mousedown', function(e) { e.stopPropagation(); }, true);
        btnConfirm.addEventListener('mouseup', function(e) { e.stopPropagation(); }, true);

        btnCancel.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            disableForceAreaSelectionMode();
        }, true);

        btnCancel.addEventListener('mousedown', function(e) { e.stopPropagation(); }, true);
        btnCancel.addEventListener('mouseup', function(e) { e.stopPropagation(); }, true);

        controlsPanel.appendChild(btnConfirm);
        controlsPanel.appendChild(btnCancel);

        editableBoxContainer.appendChild(controlsPanel);
        editableBoxContainer.appendChild(editableBox);
        document.body.appendChild(editableBoxContainer);

        // Dragging Logic
        editableBox.addEventListener('mousedown', function(e) {
            // If clicking the resize handle (bottom right corner), don't drag
            var rect = editableBox.getBoundingClientRect();
            if (e.clientX > rect.right - 20 && e.clientY > rect.bottom - 20) {
                return; // Let native CSS resize handle it
            }

            isDraggingBox = true;
            dragOffsetX = e.pageX - editableBoxContainer.offsetLeft;
            dragOffsetY = e.pageY - editableBoxContainer.offsetTop;
            e.preventDefault(); // prevent text selection
        });

        document.addEventListener('mousemove', onBoxDragMove);
        document.addEventListener('mouseup', onBoxDragEnd);

        // Prevent our UI clicks from bubbling up to the global capture handlers
        // Let the events bubble, but ensure they don't trigger the global click listener
        // The global click listener now checks `e.target.closest('#dtech-editable-box-container')`
    }

    function confirmForceClickArea() {
        if (!editableBoxContainer || !editableBox) return;

        var rect = editableBox.getBoundingClientRect();

        var finalRect = {
            left: rect.left + window.scrollX,
            top: rect.top + window.scrollY,
            width: rect.width,
            height: rect.height
        };

        var event = {
            type: 'force_click_area',
            selector: 'drawn_box',
            time: Date.now() - window.recordingStartTime,
            url: window.location.href,
            rect: finalRect
        };

        if (window.Android && window.Android.recordEvent) {
            window.Android.recordEvent(JSON.stringify(event));
        }
        console.log("Recorded Force Click Area (Editable Box): ", JSON.stringify(event));

        disableForceAreaSelectionMode();
    }

    function disableForceAreaSelectionMode() {
        window.forceAreaSelectionModeActive = false;
        if (editableBoxContainer && editableBoxContainer.parentNode) {
            editableBoxContainer.parentNode.removeChild(editableBoxContainer);
            editableBoxContainer = null;
            editableBox = null;
        }
        document.removeEventListener('mousemove', onBoxDragMove);
        document.removeEventListener('mouseup', onBoxDragEnd);
    }

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

        // Check if Force Area Selection mode is active
        if (window.forceAreaSelectionModeActive) {
            // Check if the click is inside our UI box. If so, let it pass through.
            if (e.target && e.target.closest && e.target.closest('#dtech-editable-box-container')) {
                return;
            }

            // Otherwise, it's a random click on the page while the box is active. Ignore it.
            e.preventDefault();
            e.stopPropagation();
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
