# SPDX-License-Identifier: MPL-2.0
"""Dependency-free RFC 6455 client used by the m+CAD Blender add-on."""

from __future__ import annotations

import base64
import hashlib
import os
import socket
import struct
import threading
import time
from collections.abc import Callable

_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
_MAX_MESSAGE_BYTES = 256 * 1024 * 1024


class LiveLinkClient:
    """Reconnectable background WebSocket receiver with no Blender API calls."""

    def __init__(
        self,
        host: str,
        port: int,
        token: str,
        on_message: Callable[[str], None],
        on_status: Callable[[str], None],
    ) -> None:
        if host not in {"127.0.0.1", "localhost"}:
            raise ValueError("m+CAD Live Link only accepts a loopback host")
        if not 1 <= port <= 65535:
            raise ValueError("port is outside the valid range")
        if not token:
            raise ValueError("session token is required")
        self._host = host
        self._port = port
        self._token = token
        self._on_message = on_message
        self._on_status = on_status
        self._stop = threading.Event()
        self._socket: socket.socket | None = None
        self._thread: threading.Thread | None = None
        self._send_lock = threading.Lock()

    def start(self) -> None:
        if self._thread and self._thread.is_alive():
            return
        self._stop.clear()
        self._thread = threading.Thread(target=self._run, name="mCAD-Live-Link", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        current = self._socket
        self._socket = None
        if current is not None:
            try:
                current.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
            try:
                current.close()
            except OSError:
                pass
        thread = self._thread
        if thread and thread is not threading.current_thread():
            thread.join(timeout=2.0)
        self._thread = None
        self._on_status("切断")

    def _run(self) -> None:
        delay = 0.5
        while not self._stop.is_set():
            try:
                current = self._connect()
                self._socket = current
                self._on_status(f"接続中  ws://{self._host}:{self._port}/mcad")
                delay = 0.5
                self._receive_loop(current)
            except (OSError, ValueError, RuntimeError) as error:
                if not self._stop.is_set():
                    self._on_status(f"再接続待機: {error}")
            finally:
                current = self._socket
                self._socket = None
                if current is not None:
                    try:
                        current.close()
                    except OSError:
                        pass
            if not self._stop.wait(delay):
                delay = min(delay * 1.7, 5.0)

    def _connect(self) -> socket.socket:
        current = socket.create_connection((self._host, self._port), timeout=5.0)
        current.settimeout(5.0)
        key = base64.b64encode(os.urandom(16)).decode("ascii")
        request = (
            f"GET /mcad?token={self._token} HTTP/1.1\r\n"
            f"Host: {self._host}:{self._port}\r\n"
            "Upgrade: websocket\r\n"
            "Connection: Upgrade\r\n"
            f"Sec-WebSocket-Key: {key}\r\n"
            "Sec-WebSocket-Version: 13\r\n\r\n"
        )
        current.sendall(request.encode("ascii"))
        header = self._read_http_header(current)
        lines = header.decode("iso-8859-1").split("\r\n")
        if not lines or " 101 " not in lines[0]:
            current.close()
            raise RuntimeError(lines[0] if lines else "invalid WebSocket response")
        headers: dict[str, str] = {}
        for line in lines[1:]:
            if ":" in line:
                name, value = line.split(":", 1)
                headers[name.strip().lower()] = value.strip()
        expected = base64.b64encode(hashlib.sha1((key + _MAGIC).encode("ascii")).digest()).decode("ascii")
        if headers.get("sec-websocket-accept") != expected:
            current.close()
            raise RuntimeError("WebSocket accept key mismatch")
        current.settimeout(1.0)
        return current

    def _receive_loop(self, current: socket.socket) -> None:
        while not self._stop.is_set():
            try:
                first = self._read_exact(current, 1)[0]
            except socket.timeout:
                continue
            second = self._read_exact(current, 1)[0]
            final_frame = bool(first & 0x80)
            opcode = first & 0x0F
            masked = bool(second & 0x80)
            length = second & 0x7F
            if length == 126:
                length = struct.unpack("!H", self._read_exact(current, 2))[0]
            elif length == 127:
                length = struct.unpack("!Q", self._read_exact(current, 8))[0]
            if not final_frame or masked or length > _MAX_MESSAGE_BYTES:
                raise RuntimeError("unsupported or oversized server frame")
            payload = self._read_exact(current, length)
            if opcode == 0x8:
                self._send_frame(current, 0x8, payload)
                return
            if opcode == 0x9:
                self._send_frame(current, 0xA, payload)
                continue
            if opcode == 0xA:
                continue
            if opcode != 0x1:
                raise RuntimeError(f"unsupported WebSocket opcode {opcode}")
            self._on_message(payload.decode("utf-8"))

    def _send_frame(self, current: socket.socket, opcode: int, payload: bytes) -> None:
        mask = os.urandom(4)
        first = bytes((0x80 | opcode,))
        length = len(payload)
        if length <= 125:
            header = first + bytes((0x80 | length,))
        elif length <= 65535:
            header = first + bytes((0x80 | 126,)) + struct.pack("!H", length)
        else:
            header = first + bytes((0x80 | 127,)) + struct.pack("!Q", length)
        masked = bytes(value ^ mask[index & 3] for index, value in enumerate(payload))
        with self._send_lock:
            current.sendall(header + mask + masked)

    @staticmethod
    def _read_http_header(current: socket.socket) -> bytes:
        result = bytearray()
        while b"\r\n\r\n" not in result:
            chunk = current.recv(1024)
            if not chunk:
                raise RuntimeError("connection closed during handshake")
            result.extend(chunk)
            if len(result) > 16384:
                raise RuntimeError("WebSocket handshake exceeded size limit")
        header, _, remainder = bytes(result).partition(b"\r\n\r\n")
        if remainder:
            raise RuntimeError("unexpected frame bytes in handshake response")
        return header + b"\r\n\r\n"

    @staticmethod
    def _read_exact(current: socket.socket, size: int) -> bytes:
        result = bytearray()
        while len(result) < size:
            chunk = current.recv(size - len(result))
            if not chunk:
                raise RuntimeError("connection closed")
            result.extend(chunk)
        return bytes(result)
