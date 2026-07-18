# SPDX-License-Identifier: MPL-2.0
from __future__ import annotations

import base64
import hashlib
import importlib.util
import socket
import struct
import threading
import time
import unittest
from pathlib import Path

_CLIENT_PATH = Path(__file__).resolve().parents[1] / "mcad_live_link" / "client.py"
_SPEC = importlib.util.spec_from_file_location("mcad_live_link_client_test", _CLIENT_PATH)
assert _SPEC is not None and _SPEC.loader is not None
_MODULE = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(_MODULE)
LiveLinkClient = _MODULE.LiveLinkClient


class FakeWebSocketServer:
    def __init__(self, token: str, payload: str) -> None:
        self.token = token
        self.payload = payload
        self.ready = threading.Event()
        self.finished = threading.Event()
        self.port = 0
        self.error: BaseException | None = None
        self.path = ""
        self._socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self._socket.bind(("127.0.0.1", 0))
        self._socket.listen(1)
        self.port = self._socket.getsockname()[1]
        self._thread = threading.Thread(target=self._run, daemon=True)

    def start(self) -> None:
        self._thread.start()
        self.ready.wait(timeout=2.0)

    def close(self) -> None:
        try:
            self._socket.close()
        except OSError:
            pass
        self._thread.join(timeout=2.0)

    def _run(self) -> None:
        self.ready.set()
        try:
            connection, _ = self._socket.accept()
            with connection:
                request = self._read_header(connection).decode("iso-8859-1")
                lines = request.split("\r\n")
                self.path = lines[0].split(" ", 2)[1]
                headers = {}
                for line in lines[1:]:
                    if ":" in line:
                        name, value = line.split(":", 1)
                        headers[name.strip().lower()] = value.strip()
                key = headers["sec-websocket-key"]
                accept = base64.b64encode(
                    hashlib.sha1((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encode("ascii")).digest()
                ).decode("ascii")
                response = (
                    "HTTP/1.1 101 Switching Protocols\r\n"
                    "Upgrade: websocket\r\n"
                    "Connection: Upgrade\r\n"
                    f"Sec-WebSocket-Accept: {accept}\r\n\r\n"
                ).encode("ascii")
                # Deliberately coalesce the HTTP upgrade and first WebSocket frame.
                encoded = self.payload.encode("utf-8")
                connection.sendall(response + self._frame(encoded))
                time.sleep(0.1)
        except BaseException as error:  # noqa: BLE001 - propagated to the unittest thread.
            self.error = error
        finally:
            self.finished.set()

    @staticmethod
    def _read_header(connection: socket.socket) -> bytes:
        result = bytearray()
        while b"\r\n\r\n" not in result:
            chunk = connection.recv(1024)
            if not chunk:
                raise RuntimeError("client closed during handshake")
            result.extend(chunk)
        return bytes(result)

    @staticmethod
    def _frame(payload: bytes) -> bytes:
        if len(payload) <= 125:
            return bytes((0x81, len(payload))) + payload
        if len(payload) <= 65535:
            return bytes((0x81, 126)) + struct.pack("!H", len(payload)) + payload
        return bytes((0x81, 127)) + struct.pack("!Q", len(payload)) + payload


class LiveLinkClientTest(unittest.TestCase):
    def test_connects_with_token_and_receives_coalesced_first_frame(self) -> None:
        token = "0123456789abcdef0123456789abcdef"
        payload = '{"protocol":"mcad-live-link","version":1}'
        server = FakeWebSocketServer(token, payload)
        server.start()
        messages: list[str] = []
        statuses: list[str] = []
        received = threading.Event()

        def on_message(value: str) -> None:
            messages.append(value)
            received.set()

        client = LiveLinkClient("127.0.0.1", server.port, token, on_message, statuses.append)
        try:
            client.start()
            self.assertTrue(received.wait(timeout=3.0))
            self.assertEqual([payload], messages)
            self.assertEqual(f"/mcad?token={token}", server.path)
            self.assertTrue(any("接続中" in status for status in statuses))
        finally:
            client.stop()
            server.close()
        if server.error is not None:
            raise server.error

    def test_rejects_non_loopback_host(self) -> None:
        with self.assertRaises(ValueError):
            LiveLinkClient("192.0.2.1", 8765, "token", lambda value: None, lambda value: None)


if __name__ == "__main__":
    unittest.main()
