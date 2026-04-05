(function() {
    if (window.isRecording) return;
    window.isRecording = true;

    if (!window.recordingStartTime) {
        window.recordingStartTime = Date.now();
    }

    console.log("Recorder started at " + window.recordingStartTime);

    // AI HEURISTIC TARGETING OVERHAUL
    function getHeuristicSelector(el) {
        if (!el || el.nodeType !== 1) return null;

        let strategies = [];

        // 1. Data Attributes (Most Stable)
        var dataAttrs = ["data-testid", "data-test-id", "data-qa", "data-cy", "data-automation-id", "data-component"];
        for (let attr of dataAttrs) {
            if (el.hasAttribute(attr)) {
                let sel = '[' + attr + '="' + el.getAttribute(attr).replace(/"/g, '\\"') + '"]';
                if (document.querySelectorAll(sel).length === 1) strategies.push({ type: 'data_attr', val: sel });
            }
        }

        // 2. Stable ID
        if (el.id && !/\d/.test(el.id)) {
            strategies.push({ type: 'id', val: '#' + el.id });
        }

        // 3. Name Attribute (Form inputs)
        if (el.name && document.getElementsByName(el.name).length === 1) {
            strategies.push({ type: 'name', val: '[name="' + el.name.replace(/"/g, '\\"') + '"]' });
        }

        // 4. Accessibility / Placeholders
        if (el.hasAttribute('aria-label')) {
            let sel = '[aria-label="' + el.getAttribute('aria-label').replace(/"/g, '\\"') + '"]';
            if (document.querySelectorAll(sel).length === 1) strategies.push({ type: 'aria', val: sel });
        }
        if (el.placeholder) {
            let sel = '[placeholder="' + el.placeholder.replace(/"/g, '\\"') + '"]';
            if (document.querySelectorAll(sel).length === 1) strategies.push({ type: 'placeholder', val: sel });
        }

        // 5. CSS Path
        let path = [];
        let current = el;
        while (current && current.nodeType === 1) {
            let selector = current.nodeName.toLowerCase();
            if (current.id && !/\d/.test(current.id)) {
                selector = '#' + current.id;
                path.unshift(selector);
                break;
            } else {
                let sib = current, nth = 1;
                while (sib = sib.previousElementSibling) {
                    if (sib.nodeName.toLowerCase() == selector) nth++;
                }
                if (nth != 1) selector += ":nth-of-type("+nth+")";
            }
            path.unshift(selector);
            current = current.parentNode;
        }
        strategies.push({ type: 'css_path', val: path.join(" > ") });

        return JSON.stringify(strategies); // Send all strategies for robust fallback during replay
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
            selector: getHeuristicSelector(target), // Now contains multiple strategies
            xpath: getRobustXPath(target),
            time: Date.now() - window.recordingStartTime,
            url: window.location.href,
            value: value,

            tagName: target.tagName.toLowerCase(),
            id: target.id || "",
            name: target.name || "",
            className: target.className || "",
            innerText: (target.innerText || "").trim().substring(0, 100),
            placeholder: target.placeholder || "",
            href: (target.href || ""),
            inputType: target.type || ""
        };

        if (extra) {
            for (var key in extra) {
                event[key] = extra[key];
            }
        }

        if (window.Android && window.Android.recordEvent) {
             window.Android.recordEvent(JSON.stringify(event));
        }
        console.log("Recorded: ", JSON.stringify(event));
    }

    document.addEventListener('click', function(e) {
        if (window.selectionModeActive) {
            e.preventDefault();
            e.stopPropagation();

            var selData = getHeuristicSelector(e.target);
            var parsed = JSON.parse(selData);
            var bestSelector = parsed.length > 0 ? parsed[0].val : getRobustXPath(e.target);

            if (window.Android && window.Android.onSuccessElementSelected) {
                window.Android.onSuccessElementSelected(bestSelector);
            }
            window.selectionModeActive = false;
            return;
        }

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

    document.addEventListener('input', function(e) {
        recordEvent('input', e.target, e.target.value);
    }, true);

    console.log("Recorder V2 injected successfully.");
})();
