
(function() {
    console.log("Replayer injected.");

    // window.replayEvents, window.replayStartTime, and window.lastExecutedIndex should be set by Java
    if (!window.replayEvents || !window.replayStartTime) {
        console.error("Replay data missing");
        return;
    }

    var events = window.replayEvents;
    var startTime = window.replayStartTime;
    var lastIndex = (typeof window.lastExecutedIndex === 'number') ? window.lastExecutedIndex : -1;

    // Sort events
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
            el.value = event.value;
            el.dispatchEvent(new Event('input', { bubbles: true }));
            el.dispatchEvent(new Event('change', { bubbles: true }));
        } else if (event.type === 'scroll') {
            window.scrollTo(event.value.x, event.value.y);
        }

        // Notify Java that this event is done
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

    // Schedule events
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
