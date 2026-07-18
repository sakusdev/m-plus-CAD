/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.livelink;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/** Minimal RFC 6455 text broadcaster bound exclusively to IPv4 loopback. */
public final class LoopbackWebSocketServer implements AutoCloseable {
    public record Endpoint(int port, String token) {
        public Endpoint {
            if (port < 1 || port > 65_535) {
                throw new IllegalArgumentException("port is outside the valid range");
            }
            Objects.requireNonNull(token, "token");
            if (token.isBlank()) {
                throw new IllegalArgumentException("token must not be blank");
            }
        }

        public String url() {
            return "ws://127.0.0.1:" + port + "/mcad?token=" + token;
        }
    }

    private static final byte OPCODE_TEXT = 0x1;
    private static final byte OPCODE_CLOSE = 0x8;
    private static final byte OPCODE_PING = 0x9;
    private static final byte OPCODE_PONG = 0xA;
    private static final byte[] HTTP_UNAUTHORIZED = (
            "HTTP/1.1 401 Unauthorized\r\nConnection: close\r\nContent-Length: 0\r\n\r\n")
            .getBytes(StandardCharsets.US_ASCII);
    private static final byte[] HTTP_BAD_REQUEST = (
            "HTTP/1.1 400 Bad Request\r\nConnection: close\r\nContent-Length: 0\r\n\r\n")
            .getBytes(StandardCharsets.US_ASCII);

    private final AtomicBoolean running = new AtomicBoolean();
    private final Set<Client> clients = ConcurrentHashMap.newKeySet();
    private final AtomicInteger threadSequence = new AtomicInteger();
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "m+CAD-live-link-" + threadSequence.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });
    private final IntConsumer clientCountListener;

    private volatile ServerSocket serverSocket;
    private volatile String token;
    private volatile String initialMessage;

    public LoopbackWebSocketServer() {
        this(count -> {
        });
    }

    public LoopbackWebSocketServer(IntConsumer clientCountListener) {
        this.clientCountListener = Objects.requireNonNull(clientCountListener, "clientCountListener");
    }

    public synchronized Endpoint start(int requestedPort, String sessionToken) throws IOException {
        if (running.get()) {
            throw new IllegalStateException("live-link server is already running");
        }
        if (requestedPort < 1 || requestedPort > 65_535) {
            throw new IllegalArgumentException("requestedPort is outside the valid range");
        }
        Objects.requireNonNull(sessionToken, "sessionToken");
        if (sessionToken.isBlank()) {
            throw new IllegalArgumentException("sessionToken must not be blank");
        }

        ServerSocket socket = new ServerSocket();
        boolean bound = false;
        try {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), requestedPort), 8);
            bound = true;
            token = sessionToken;
            serverSocket = socket;
            running.set(true);
            executor.execute(this::acceptLoop);
            return new Endpoint(socket.getLocalPort(), sessionToken);
        } finally {
            if (!bound) {
                socket.close();
            }
        }
    }

    public boolean running() {
        return running.get();
    }

    public int clientCount() {
        return clients.size();
    }

    public void setInitialMessage(String message) {
        initialMessage = Objects.requireNonNull(message, "message");
    }

    public void clearInitialMessage() {
        initialMessage = null;
    }

    public int broadcast(String message) {
        byte[] frame = frame(OPCODE_TEXT, Objects.requireNonNull(message, "message").getBytes(StandardCharsets.UTF_8));
        int delivered = 0;
        for (Client client : clients) {
            try {
                client.send(frame);
                delivered++;
            } catch (IOException exception) {
                remove(client);
            }
        }
        return delivered;
    }

    @Override
    public synchronized void close() {
        if (!running.getAndSet(false)) {
            return;
        }
        ServerSocket current = serverSocket;
        serverSocket = null;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
                // Closing is best-effort; client sockets are closed below.
            }
        }
        for (Client client : clients) {
            remove(client);
        }
        initialMessage = null;
        token = null;
        executor.shutdownNow();
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                ServerSocket current = serverSocket;
                if (current == null) {
                    return;
                }
                Socket socket = current.accept();
                socket.setTcpNoDelay(true);
                executor.execute(() -> handle(socket));
            } catch (IOException exception) {
                if (running.get()) {
                    running.set(false);
                }
            }
        }
    }

    private void handle(Socket socket) {
        Client client = null;
        try (socket) {
            socket.setSoTimeout(10_000);
            Handshake request = readHandshake(socket.getInputStream());
            if (!authorized(request.target())) {
                socket.getOutputStream().write(HTTP_UNAUTHORIZED);
                return;
            }
            String webSocketKey = request.headers().get("sec-websocket-key");
            if (!validUpgrade(request, webSocketKey)) {
                socket.getOutputStream().write(HTTP_BAD_REQUEST);
                return;
            }
            writeHandshake(socket.getOutputStream(), webSocketKey);
            socket.setSoTimeout(0);

            client = new Client(socket);
            clients.add(client);
            clientCountListener.accept(clients.size());
            String currentInitial = initialMessage;
            if (currentInitial != null) {
                client.send(frame(OPCODE_TEXT, currentInitial.getBytes(StandardCharsets.UTF_8)));
            }
            readFrames(client);
        } catch (IOException | RuntimeException ignored) {
            // A malformed or disconnected local client is isolated from the runtime.
        } finally {
            if (client != null) {
                remove(client);
            }
        }
    }

    private void readFrames(Client client) throws IOException {
        InputStream input = client.socket().getInputStream();
        while (running.get()) {
            int first = input.read();
            if (first < 0) {
                return;
            }
            int second = required(input);
            boolean finalFrame = (first & 0x80) != 0;
            int opcode = first & 0x0F;
            boolean masked = (second & 0x80) != 0;
            long length = second & 0x7F;
            if (length == 126L) {
                length = ((long) required(input) << 8) | required(input);
            } else if (length == 127L) {
                length = 0L;
                for (int index = 0; index < 8; index++) {
                    length = (length << 8) | required(input);
                }
            }
            if (!finalFrame || !masked || length > LiveLinkProtocol.MAX_INBOUND_FRAME_BYTES) {
                return;
            }
            byte[] mask = readExact(input, 4);
            byte[] payload = readExact(input, Math.toIntExact(length));
            for (int index = 0; index < payload.length; index++) {
                payload[index] ^= mask[index & 3];
            }
            if (opcode == OPCODE_CLOSE) {
                client.send(frame(OPCODE_CLOSE, payload));
                return;
            }
            if (opcode == OPCODE_PING) {
                client.send(frame(OPCODE_PONG, payload));
            } else if (opcode != OPCODE_TEXT && opcode != OPCODE_PONG) {
                return;
            }
        }
    }

    private boolean authorized(String target) {
        String expected = token;
        if (expected == null) {
            return false;
        }
        int question = target.indexOf('?');
        if (question < 0 || !target.substring(0, question).equals("/mcad")) {
            return false;
        }
        String supplied = null;
        for (String part : target.substring(question + 1).split("&")) {
            int equals = part.indexOf('=');
            if (equals > 0 && part.substring(0, equals).equals("token")) {
                supplied = part.substring(equals + 1);
                break;
            }
        }
        return supplied != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean validUpgrade(Handshake request, String key) {
        String upgrade = request.headers().getOrDefault("upgrade", "");
        String connection = request.headers().getOrDefault("connection", "");
        String version = request.headers().getOrDefault("sec-websocket-version", "");
        return request.method().equals("GET")
                && upgrade.equalsIgnoreCase("websocket")
                && connection.toLowerCase(Locale.ROOT).contains("upgrade")
                && version.equals("13")
                && key != null
                && !key.isBlank();
    }

    private static Handshake readHandshake(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int matched = 0;
        while (bytes.size() < LiveLinkProtocol.MAX_HANDSHAKE_BYTES) {
            int value = input.read();
            if (value < 0) {
                throw new EOFException("connection closed during WebSocket handshake");
            }
            bytes.write(value);
            int expected = switch (matched) {
                case 0, 2 -> '\r';
                case 1, 3 -> '\n';
                default -> throw new IllegalStateException("invalid handshake matcher state");
            };
            if (value == expected) {
                matched++;
                if (matched == 4) {
                    break;
                }
            } else {
                matched = value == '\r' ? 1 : 0;
            }
        }
        if (matched != 4) {
            throw new IOException("WebSocket handshake exceeded size limit");
        }
        String request = bytes.toString(StandardCharsets.ISO_8859_1);
        String[] lines = request.split("\\r\\n");
        String[] requestLine = lines[0].split(" ", 3);
        if (requestLine.length != 3) {
            throw new IOException("invalid HTTP request line");
        }
        Map<String, String> headers = new TreeMap<>();
        for (int index = 1; index < lines.length; index++) {
            int separator = lines[index].indexOf(':');
            if (separator > 0) {
                headers.put(
                        lines[index].substring(0, separator).trim().toLowerCase(Locale.ROOT),
                        lines[index].substring(separator + 1).trim());
            }
        }
        return new Handshake(requestLine[0], requestLine[1], Map.copyOf(headers));
    }

    private static void writeHandshake(OutputStream output, String key) throws IOException {
        String accept;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            accept = Base64.getEncoder().encodeToString(digest.digest(
                    (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is required by RFC 6455", exception);
        }
        String response = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
        output.write(response.getBytes(StandardCharsets.US_ASCII));
        output.flush();
    }

    private static byte[] frame(byte opcode, byte[] payload) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(payload.length + 10);
        output.write(0x80 | opcode);
        if (payload.length <= 125) {
            output.write(payload.length);
        } else if (payload.length <= 65_535) {
            output.write(126);
            output.write((payload.length >>> 8) & 0xFF);
            output.write(payload.length & 0xFF);
        } else {
            output.write(127);
            long length = payload.length;
            for (int shift = 56; shift >= 0; shift -= 8) {
                output.write((int) (length >>> shift) & 0xFF);
            }
        }
        output.writeBytes(payload);
        return output.toByteArray();
    }

    private void remove(Client client) {
        if (clients.remove(client)) {
            client.close();
            clientCountListener.accept(clients.size());
        }
    }

    private static int required(InputStream input) throws IOException {
        int value = input.read();
        if (value < 0) {
            throw new EOFException("unexpected end of WebSocket frame");
        }
        return value;
    }

    private static byte[] readExact(InputStream input, int length) throws IOException {
        byte[] result = input.readNBytes(length);
        if (result.length != length) {
            throw new EOFException("unexpected end of WebSocket frame");
        }
        return result;
    }

    private record Handshake(String method, String target, Map<String, String> headers) {
        private Handshake {
            Objects.requireNonNull(method, "method");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(headers, "headers");
        }
    }

    private record Client(Socket socket) implements AutoCloseable {
        private Client {
            Objects.requireNonNull(socket, "socket");
        }

        synchronized void send(byte[] frame) throws IOException {
            OutputStream output = socket.getOutputStream();
            output.write(frame);
            output.flush();
        }

        @Override
        public void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Best-effort socket cleanup.
            }
        }
    }
}
