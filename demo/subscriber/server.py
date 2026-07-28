import hashlib
import hmac
import json
import os
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse


SIGNING_SECRET = os.environ.get(
    "EVENTMESH_DEMO_SIGNING_SECRET",
    "eventmesh-demo-signing-secret-change-me-32",
).encode()

lock = threading.Lock()
requests = []
remaining_failures = 0


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/health":
            self._json(200, {"status": "UP"})
            return
        if self.path == "/requests":
            with lock:
                snapshot = list(requests)
            self._json(200, {"count": len(snapshot), "requests": snapshot})
            return
        self._json(404, {"error": "not_found"})

    def do_POST(self):
        global remaining_failures

        parsed = urlparse(self.path)
        if parsed.path == "/reset":
            failures = int(parse_qs(parsed.query).get("failures", ["0"])[0])
            with lock:
                requests.clear()
                remaining_failures = max(0, failures)
            self._json(200, {"status": "reset", "failures": remaining_failures})
            return

        if parsed.path != "/webhooks":
            self._json(404, {"error": "not_found"})
            return

        body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        timestamp = self.headers.get("X-EventMesh-Timestamp", "")
        delivery_id = self.headers.get("X-EventMesh-Delivery", "")
        signature = self.headers.get("X-EventMesh-Signature", "")
        canonical = timestamp.encode() + b"." + delivery_id.encode() + b"." + body
        expected = "v1=" + hmac.new(
            SIGNING_SECRET, canonical, hashlib.sha256
        ).hexdigest()
        try:
            fresh = abs(int(time.time()) - int(timestamp)) <= 300
        except ValueError:
            fresh = False
        valid = fresh and hmac.compare_digest(signature, expected)

        try:
            event = json.loads(body)
        except json.JSONDecodeError:
            event = {"invalidJson": True}

        with lock:
            should_fail = remaining_failures > 0
            response_status = 401 if not valid else (503 if should_fail else 204)
            requests.append(
                {
                    "deliveryId": delivery_id,
                    "attempt": int(self.headers.get("X-EventMesh-Attempt", "0")),
                    "validSignature": valid,
                    "responseStatus": response_status,
                    "event": event,
                }
            )
            if should_fail:
                remaining_failures -= 1

        if not valid:
            self._json(401, {"error": "invalid_signature"})
        elif should_fail:
            self._json(503, {"error": "planned_demo_failure"})
        else:
            self.send_response(204)
            self.end_headers()

    def log_message(self, format, *args):
        return

    def _json(self, status, payload):
        encoded = json.dumps(payload, separators=(",", ":")).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)


if __name__ == "__main__":
    server = ThreadingHTTPServer(("0.0.0.0", 8090), Handler)
    server.serve_forever()
