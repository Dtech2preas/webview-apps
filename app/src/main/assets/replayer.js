(function() {
    console.log("Replayer V2 injected.");

    if (!window.replayEvents) return;

    var events = window.replayEvents;
    var startIndex = (typeof window.lastExecutedIndex === 'number') ? window.lastExecutedIndex + 1 : 0;

    var overrideEmail = window.overrideEmail || null;
    var overridePassword = window.overridePassword || null;

    // AI HEURISTIC ELEMENT FINDER
    function findTargetHeuristics(event) {
        // 1. Try strategies array from new recorder
        if (event.selector) {
            try {
                let strategies = JSON.parse(event.selector);
                for (let strat of strategies) {
                    try {
                        let el = document.querySelector(strat.val);
                        if (el) return el;
                    } catch(e) {}
                }
            } catch(e) {
                // Legacy support if selector is just a string
                try {
                    let el = document.querySelector(event.selector);
                    if (el) return el;
                } catch(e) {}
            }
        }

        // 2. XPath Fallback
        if (event.xpath) {
            try {
                var resXPath = document.evaluate(event.xpath, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null);
                if (resXPath.singleNodeValue) return resXPath.singleNodeValue;
            } catch(e) {}
        }

        // 3. Inner Text Fallback (Deep search)
        if (event.innerText && event.innerText.length > 2) {
            var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
            var node;
            while (node = walker.nextNode()) {
                if (node.nodeValue.trim() === event.innerText) {
                    if (node.parentElement && node.parentElement.tagName !== 'SCRIPT') {
                        return node.parentElement;
                    }
                }
            }
        }

        return null;
    }

    function checkConditions(el) {
        var style = window.getComputedStyle(el);
        var isVisible = style.display !== 'none' && style.visibility !== 'hidden' && (el.offsetWidth > 0 || el.offsetHeight > 0);
        var isDisabled = el.disabled === true || el.getAttribute("aria-disabled") === "true";
        return isVisible && !isDisabled && el.isConnected;
    }

    // IDLE WAIT INSTEAD OF STATIC POLLING
    function waitForElementIntelligent(event, maxWaitMs) {
        return new Promise(function(resolve) {
            var el = findTargetHeuristics(event);
            if (el && checkConditions(el)) {
                resolve(el);
                return;
            }

            var timeout = setTimeout(() => {
                observer.disconnect();
                resolve(findTargetHeuristics(event)); // Final check
            }, maxWaitMs);

            var observer = new MutationObserver(() => {
                var currentEl = findTargetHeuristics(event);
                if (currentEl && checkConditions(currentEl)) {
                    clearTimeout(timeout);
                    observer.disconnect();
                    // Let animations settle
                    setTimeout(() => resolve(currentEl), 300);
                }
            });

            observer.observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ['style', 'class'] });
        });
    }

    function triggerAction(el, event) {
        try {
            if (el && el.scrollIntoView && el !== document.body) {
                el.scrollIntoView({ behavior: 'smooth', block: 'center' });
            }
        } catch(e) {}

        if (event.type === 'click') {
            if (el.hasAttribute && el.hasAttribute('disabled')) el.removeAttribute('disabled');

            var rect = el.getBoundingClientRect();
            var cx = rect.left + (rect.width / 2);
            var cy = rect.top + (rect.height / 2);

            var clickEvent = new MouseEvent('click', { view: window, bubbles: true, cancelable: true, clientX: cx, clientY: cy });
            var mDown = new MouseEvent('mousedown', { view: window, bubbles: true, cancelable: true, clientX: cx, clientY: cy });
            var mUp = new MouseEvent('mouseup', { view: window, bubbles: true, cancelable: true, clientX: cx, clientY: cy });

            el.dispatchEvent(mDown);
            el.dispatchEvent(mUp);
            el.dispatchEvent(clickEvent);

            setTimeout(() => {
                try { el.click(); } catch(e){}
            }, 10);

        } else if (event.type === 'input') {
            var val = event.value;

            if ((el.type === 'password' || event.inputType === 'password') && overridePassword) {
                val = overridePassword;
            } else if (overrideEmail) {
                var keywords = ["email", "user", "login", "phone"];
                var idName = ((el.id||"") + (el.name||"")).toLowerCase();
                if (el.type === 'email' || keywords.some(k => idName.includes(k))) {
                    val = overrideEmail;
                }
            }

            el.focus();
            var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
            if (setter) setter.call(el, val);
            else el.value = val;

            el.dispatchEvent(new Event('input', { bubbles: true }));
            el.dispatchEvent(new Event('change', { bubbles: true }));
        }
    }

    function processNextEvent(index) {
        if (index >= events.length) return;

        var event = events[index];
        console.log("Processing Event #" + index, event);

        waitForElementIntelligent(event, 15000).then(function(el) {
            if (el) {
                triggerAction(el, event);
            } else if (event.clientX && event.clientY) {
                console.log("Fallback Coordinate Click");
                var fallbackEl = document.elementFromPoint(event.clientX, event.clientY) || document.body;
                triggerAction(fallbackEl, event);
            }

            if (window.Android && window.Android.eventExecuted) {
                window.Android.eventExecuted(index);
            }

            setTimeout(() => processNextEvent(index + 1), 200);
        });
    }

    // Wait for initial page load before starting
    if (document.readyState === 'complete') {
        processNextEvent(startIndex);
    } else {
        window.addEventListener('load', () => processNextEvent(startIndex));
    }
})();
