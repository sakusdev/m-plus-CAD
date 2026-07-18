/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.livelink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class LoopbackWebSocketServerTest {
    @Test
    void rejectsWrongTokenAndSendsInitialAndDeltaFrames() throws Exception {
        int port = freePort();
        try (LoopbackWebSocketServer server = new LoopbackWebSocketServer()) {
            server.setInitialMessage("{\"type\":\"initial\"}");
            server.start(port, "0123456789abcdef0123456789abcdef");

            try (Socket unauthorized = new Socket("127.0.0.1", port)) {
                writeHandshake(unauthorized, port, "wrong");
                String response = readHeader(unauthorized.getInputStream());
                assertTrue(response.startsWith("HTTP/1.1 401"));
            }

            try (Socket authorized = new Socket("127.0.0.1", port)) {
                writeHandshake(authorized, port, "0123456789abcdef0123456789abcdef");
                String response = readHeader(authorized.getInputStream());
                assertTrue(response.startsWith("HTTP/1.1 101"));
                assertEquals("{\"type\":\"initial\"}", readTextFrame(authorized.getInputStream()));
                server.broadcast("{\"type\":\"delta\"}");
                assertEquals("{\"type\":\"delta\"}", readTextFrame(authorized.getInputStream()));
            }
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void writeHandshake(Socket socket, int port, String token) throws IOException {
        String key = Base64.getEncoder().encodeToString("0123456789abcdef".getBytes(StandardCharsets.US_ASCII));
        String request = "GET /mcad?token=" + token + " HTTP/1.1\r\n"
                + "Host: 127.0.0.1:" + port + "\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + key + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n\r\n";
        socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
    }

    private static String readHeader(InputStream input) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        int matched = 0;
        while (matched < 4) {
            int value = input.read();
            if (value < 0) {
                throw new IOException("connection closed during HTTP response");
            }
            result.write(value);
            int expected = matched == 0 || matched == 2 ? '\r' : '\n';
            if (value == expected) {
                matched++;
            } else {
                matched = value == '\r' ? 1 : 0;
            }
        }
        return result.toString(StandardCharsets.ISO_8859_1);
    }

    private static String readTextFrame(InputStream input) throws IOException {
        int first = required(input);
        assertEquals(0x81, first);
        int lengthByte = required(input);
        int length;
        if (lengthByte == 126) {
            length = (required(input) << 8) | required(input);
        } else {
            length = lengthByte;
        }
        byte[] payload = input.readNBytes(length);
        if (payload.length != length) {
            throw new IOException("short WebSocket frame");
        }
        return new String(payload, StandardCharsets.UTF_8);
    }

    private static int required(InputStream input) throws IOException {
        int value = input.read();
        if (value < 0) {
            throw new IOException("unexpected end of stream");
        }
        return value;
    }
}
