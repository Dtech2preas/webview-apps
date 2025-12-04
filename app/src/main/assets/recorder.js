
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
        while (el.nodeType === 1) {
            var selector = el.nodeName.toLowerCase();
            if (el.id) {
                selector = '#' + el.id;
                path.unshift(selector);
                break;
            } else {
                var sib = el, nth = 1;
                while (sib = sib.previousElementSibling) {
                    if (sib.nodeName.toLowerCase() == selector)
                       nth++;
                }
                if (nth != 1)
                    selector += ":nth-of-type("+nth+")";
            }
            path.unshift(selector);
            el = el.parentNode;
        }
        return path.join(" > ");
    }

    function recordEvent(type, target, value) {
        var event = {
            type: type,
            selector: getSelector(target),
            time: Date.now() - window.recordingStartTime,
            url: window.location.href, // Added URL logging
            value: value
        };

        // Send directly to Java to persist across page loads
        if (window.Android && window.Android.recordEvent) {
             window.Android.recordEvent(JSON.stringify(event));
        }
        console.log("Recorded: ", JSON.stringify(event));
    }

    // CLICK
    document.addEventListener('click', function(e) {
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
        if (now - lastScroll > 200) { // 200ms throttle
            recordEvent('scroll', document.scrollingElement || document.body, {
                x: window.scrollX,
                y: window.scrollY
            });
            lastScroll = now;
        }
    }, true);

    console.log("Recorder injected successfully.");
})();
