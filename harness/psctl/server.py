"""HTTP control server that holds one Remote Play session open.

Opening a session takes seconds and pops a banner on the TV, so tests and agents
talk to this long-lived server instead of reconnecting per action.

    GET  /               browser remote: live frame + buttons (arrows/Enter/Backspace work too)
    GET  /status         console + session status (JSON)
    GET  /frame.png      latest frame (also /frame.jpg); ?max_age=SECONDS rejects a stalled stream
    POST /press          body {"sequence": "ps 2s down*2 cross", "gap": 0.35}
    POST /stick          body {"stick": "left", "x": 0, "y": -1, "duration": 0.3}
    POST /connect        (re)connect; also done lazily by every action
    POST /disconnect
    POST /wake
    POST /standby
"""

from __future__ import annotations

import json
import logging
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

from .console import Console, ConsoleError
from .inputs import BUTTONS

_LOGGER = logging.getLogger(__name__)


def make_handler(console: Console):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, fmt, *args):
            _LOGGER.debug("%s - %s", self.address_string(), fmt % args)

        def do_GET(self):
            url = urlparse(self.path)
            query = parse_qs(url.query)
            try:
                if url.path == "/":
                    self._send(200, PAGE.encode(), "text/html; charset=utf-8")
                elif url.path == "/status":
                    try:
                        status = console.status()
                    except ConsoleError as error:
                        status = {"error": str(error)}
                    self._json(200, {"console": status, "session": console.session_info()})
                elif url.path in ("/frame.png", "/frame.jpg"):
                    fmt = "PNG" if url.path.endswith(".png") else "JPEG"
                    max_age = float(query["max_age"][0]) if "max_age" in query else None
                    self._send(200, console.frame(fmt, max_age), f"image/{fmt.lower()}")
                else:
                    self._json(404, {"error": "not found"})
            except (ConsoleError, ValueError) as error:
                self._json(503, {"error": str(error)})

        def do_POST(self):
            path = urlparse(self.path).path
            try:
                body = self._body()
                if path == "/press":
                    done = console.press(body["sequence"], float(body.get("gap", 0.35)))
                    self._json(200, {"pressed": done})
                elif path == "/stick":
                    console.stick(body.get("stick", "left"), float(body["x"]), float(body["y"]),
                                  float(body.get("duration", 0.3)))
                    self._json(200, {"ok": True})
                elif path == "/connect":
                    console.connect()
                    self._json(200, console.session_info())
                elif path == "/disconnect":
                    console.disconnect()
                    self._json(200, console.session_info())
                elif path == "/wake":
                    console.wake()
                    self._json(200, console.status())
                elif path == "/standby":
                    console.standby()
                    self._json(200, {"ok": True})
                else:
                    self._json(404, {"error": "not found"})
            except (ConsoleError, ValueError, KeyError) as error:
                self._json(400 if isinstance(error, (ValueError, KeyError)) else 503, {"error": str(error)})

        def _body(self) -> dict:
            length = int(self.headers.get("Content-Length") or 0)
            if not length:
                return {}
            return json.loads(self.rfile.read(length))

        def _json(self, code: int, payload: dict):
            self._send(code, json.dumps(payload, indent=2).encode(), "application/json")

        def _send(self, code: int, data: bytes, content_type: str):
            self.send_response(code)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(data)

    return Handler


def serve(console: Console, bind: str, port: int) -> None:
    server = ThreadingHTTPServer((bind, port), make_handler(console))
    print(f"psctl serving {console.host} on http://{bind}:{port}/  (Ctrl+C to stop)")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
        console.disconnect()


_BUTTON_HTML = "".join(f'<button data-b="{b}">{b}</button>' for b in BUTTONS)

PAGE = """<!doctype html>
<html><head><meta charset="utf-8"><title>psctl</title>
<style>
 body{font:14px system-ui,sans-serif;margin:16px;background:#111;color:#eee}
 img{max-width:100%;border:1px solid #444;display:block;margin-bottom:8px;background:#000;min-height:120px}
 button{margin:2px;padding:6px 10px} #log{white-space:pre-wrap;color:#9c9}
</style></head><body>
<img id="f" alt="no frame yet">
<div>""" + _BUTTON_HTML + """</div>
<p>Keys: arrows, Enter=cross, Backspace=circle, Escape=ps, o=options, t=triangle, s=square</p>
<div id="log"></div>
<script>
const keys={ArrowUp:'up',ArrowDown:'down',ArrowLeft:'left',ArrowRight:'right',Enter:'cross',
 Backspace:'circle',Escape:'ps',o:'options',t:'triangle',s:'square'};
const log=t=>document.getElementById('log').textContent=t;
async function press(seq){
  const r=await fetch('/press',{method:'POST',body:JSON.stringify({sequence:seq})});
  log(await r.text());
}
document.querySelectorAll('button[data-b]').forEach(b=>b.onclick=()=>press(b.dataset.b));
document.onkeydown=e=>{if(keys[e.key]){e.preventDefault();press(keys[e.key]);}};
const img=document.getElementById('f');
function tick(){const n=new Image();n.onload=()=>{img.src=n.src;setTimeout(tick,300)};
 n.onerror=()=>setTimeout(tick,2000);n.src='/frame.jpg?t='+Date.now();}
tick();
</script></body></html>
"""
