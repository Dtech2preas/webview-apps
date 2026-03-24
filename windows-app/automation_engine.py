import json
from PyQt5.QtWebEngineWidgets import QWebEnginePage

class WebAutomationEngine:
    def __init__(self, page: QWebEnginePage, overlay):
        self.page = page
        self.overlay = overlay
        self.recorded_events = []

        # Inject custom JS channel logic here if needed,
        # For simplicity in PyQt, we inject JS that logs to the console,
        # and we capture console messages.

    def inject_recording_script(self):
        js = """
        (function() {
            if (window.__dtech_recording_injected) return;
            window.__dtech_recording_injected = true;

            function getCssPath(el) {
                if (!(el instanceof Element)) return;
                var path = [];
                while (el.nodeType === Node.ELEMENT_NODE) {
                    var selector = el.nodeName.toLowerCase();
                    if (el.id) {
                        selector += '#' + el.id;
                        path.unshift(selector);
                        break;
                    } else {
                        var sib = el, nth = 1;
                        while (sib = sib.previousElementSibling) {
                            if (sib.nodeName.toLowerCase() == selector) nth++;
                        }
                        if (nth != 1) selector += ":nth-of-type("+nth+")";
                    }
                    path.unshift(selector);
                    el = el.parentNode;
                }
                return path.join(" > ");
            }

            document.addEventListener('click', function(e) {
                var path = getCssPath(e.target);
                console.log('DTECH_EVENT|click|' + path + '|' + Date.now());
            }, true);

            document.addEventListener('change', function(e) {
                var path = getCssPath(e.target);
                var val = e.target.value || '';
                console.log('DTECH_EVENT|input|' + path + '|' + val + '|' + Date.now());
            }, true);

            console.log("Recording script injected.");
        })();
        """
        self.page.runJavaScript(js)

    def parse_console_message(self, message):
        if message.startswith("DTECH_EVENT|"):
            parts = message.split('|')
            if len(parts) >= 4:
                event_type = parts[1]
                target = parts[2]
                value = parts[3] if len(parts) > 4 else ""
                timestamp = int(parts[-1]) if parts[-1].isdigit() else 0

                event = {
                    "type": event_type,
                    "target": target,
                    "value": value,
                    "timestamp": timestamp,
                    "optional": False
                }
                self.recorded_events.append(event)
                self.overlay.log(f"Recorded: {event_type} on {target}", color="#69F0AE")

    def execute_script(self, script_json: str, email: str, password: str):
        # Escape strings for JS
        safe_email = email.replace('"', '\\"')
        safe_pass = password.replace('"', '\\"')
        safe_script = script_json.replace('"', '\\"').replace('\n', '')

        js = f"""
        (function() {{
            window.overrideEmail = "{safe_email}";
            window.overridePassword = "{safe_pass}";

            var events = JSON.parse("{safe_script}");
            var i = 0;

            function nextEvent() {{
                if (i >= events.length) {{
                    console.log("DTECH_RUN|COMPLETE");
                    return;
                }}
                var ev = events[i];
                var el = document.querySelector(ev.target);
                if (el) {{
                    if (ev.type === 'click') {{
                        el.click();
                        console.log("DTECH_RUN|STEP|Clicked " + ev.target);
                    }} else if (ev.type === 'input') {{
                        var val = ev.value;
                        if (val === '{{{{email}}}}' || val.includes('email')) val = window.overrideEmail;
                        if (val === '{{{{password}}}}' || val.includes('pass')) val = window.overridePassword;
                        el.value = val;
                        el.dispatchEvent(new Event('change', {{bubbles: true}}));
                        console.log("DTECH_RUN|STEP|Input " + ev.target);
                    }}
                }} else {{
                    console.log("DTECH_RUN|STEP|Element not found: " + ev.target);
                }}
                i++;
                setTimeout(nextEvent, 1000); // 1s delay between steps
            }}
            nextEvent();
        }})();
        """
        self.page.runJavaScript(js)
