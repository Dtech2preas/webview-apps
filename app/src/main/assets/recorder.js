
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
        if (el.id) return '#' + el.id;

        var path = [];
        var current = el;
        while (current && current.nodeType === 1) {
            var selector = current.nodeName.toLowerCase();
            if (current.id) {
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

    function recordEvent(type, target, value) {
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

        // Send directly to Java to persist across page loads
        if (window.Android && window.Android.recordEvent) {
             window.Android.recordEvent(JSON.stringify(event));
        }
        console.log("Recorded: ", JSON.stringify(event));
    }

    // CLICK
    document.addEventListener('click', function(e) {
        // Don't record clicks on recorder UI if we ever add one
        recordEvent('click', e.target, null);
    }, true);

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
