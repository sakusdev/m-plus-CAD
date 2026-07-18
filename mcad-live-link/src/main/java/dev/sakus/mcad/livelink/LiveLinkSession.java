/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.livelink;

import dev.sakus.mcad.api.GeneratedScene;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** Stateful server-side scene publisher with reconnect-safe full snapshots and differential updates. */
public final class LiveLinkSession implements AutoCloseable {
    public record Status(
            boolean running,
            int port,
            String token,
            int clientCount,
            long revision,
            String message) {
        public Status {
            if (port < 0 || port > 65_535 || clientCount < 0 || revision < 0L) {
                throw new IllegalArgumentException("status numeric values are outside their valid range");
            }
            Objects.requireNonNull(token, "token");
            Objects.requireNonNull(message, "message");
        }

        public Optional<String> url() {
            return running ? Optional.of("ws://127.0.0.1:" + port + "/mcad?token=" + token) : Optional.empty();
        }
    }

    public record PublishResult(
            boolean sent,
            boolean full,
            long revision,
            int deliveredClients,
            int upsertCount,
            int removeCount) {
        public PublishResult {
            if (revision < 0L || deliveredClients < 0 || upsertCount < 0 || removeCount < 0) {
                throw new IllegalArgumentException("publish result values must be non-negative");
            }
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SceneDeltaEncoder encoder = new SceneDeltaEncoder();
    private final AtomicInteger clients = new AtomicInteger();

    private LoopbackWebSocketServer server;
    private LoopbackWebSocketServer.Endpoint endpoint;
    private LiveSceneSnapshot previous;
    private long revision;
    private String message = "停止中";

    public synchronized Status start(int port) throws IOException {
        if (server != null && server.running()) {
            return status();
        }
        String token = randomToken();
        LoopbackWebSocketServer created = new LoopbackWebSocketServer(clients::set);
        try {
            LoopbackWebSocketServer.Endpoint started = created.start(port, token);
            server = created;
            endpoint = started;
            message = "接続待機中";
            return status();
        } catch (IOException | RuntimeException exception) {
            created.close();
            throw exception;
        }
    }

    public synchronized PublishResult publish(GeneratedScene scene, boolean forceFull) {
        Objects.requireNonNull(scene, "scene");
        LoopbackWebSocketServer currentServer = requireRunning();
        LiveSceneSnapshot current = LiveSceneSnapshot.capture(scene);
        long nextRevision = Math.incrementExact(revision);
        SceneDeltaEncoder.EncodedUpdate update = encoder.encode(previous, current, nextRevision, forceFull);
        SceneDeltaEncoder.EncodedUpdate full = encoder.encode(null, current, nextRevision, true);
        currentServer.setInitialMessage(full.json());
        previous = current;
        revision = nextRevision;
        int delivered = update.changed() ? currentServer.broadcast(update.json()) : 0;
        message = update.changed()
                ? "revision " + revision + " を " + delivered + " clientへ送信"
                : "変更なし";
        return new PublishResult(
                update.changed(), update.full(), revision, delivered, update.upsertCount(), update.removeCount());
    }

    public synchronized PublishResult broadcastFull() {
        LoopbackWebSocketServer currentServer = requireRunning();
        if (previous == null) {
            return new PublishResult(false, true, revision, 0, 0, 0);
        }
        long nextRevision = Math.incrementExact(revision);
        SceneDeltaEncoder.EncodedUpdate full = encoder.encode(null, previous, nextRevision, true);
        currentServer.setInitialMessage(full.json());
        int delivered = currentServer.broadcast(full.json());
        revision = nextRevision;
        message = "full revision " + revision + " を " + delivered + " clientへ送信";
        return new PublishResult(true, true, revision, delivered, full.upsertCount(), 0);
    }

    public synchronized int clear(String reason) {
        Objects.requireNonNull(reason, "reason");
        LoopbackWebSocketServer currentServer = requireRunning();
        long nextRevision = Math.incrementExact(revision);
        String clear = encoder.clearMessage(nextRevision, reason);
        currentServer.clearInitialMessage();
        int delivered = currentServer.broadcast(clear);
        previous = null;
        revision = nextRevision;
        message = "Sceneをclearしました";
        return delivered;
    }

    public synchronized Status status() {
        boolean active = server != null && server.running() && endpoint != null;
        return new Status(
                active,
                active ? endpoint.port() : 0,
                active ? endpoint.token() : "",
                active ? clients.get() : 0,
                revision,
                message);
    }

    @Override
    public synchronized void close() {
        if (server != null) {
            server.close();
        }
        server = null;
        endpoint = null;
        previous = null;
        clients.set(0);
        message = "停止中";
    }

    private LoopbackWebSocketServer requireRunning() {
        if (server == null || !server.running()) {
            throw new IllegalStateException("live-link session is not running");
        }
        return server;
    }

    private static String randomToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
