/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.gui;

import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe progress and cancellation view model for snapshot/export operations.
 */
public final class OperationProgressModel {
    public enum State {
        IDLE,
        RUNNING,
        CANCELLING,
        SUCCEEDED,
        FAILED,
        CANCELLED
    }

    @FunctionalInterface
    public interface Listener {
        void progressChanged(Snapshot snapshot);
    }

    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }

    public record Snapshot(
            State state,
            String phase,
            long completed,
            OptionalLong total,
            String message,
            boolean cancellable) {
        public Snapshot {
            Objects.requireNonNull(state, "state");
            phase = Objects.requireNonNull(phase, "phase");
            total = Objects.requireNonNull(total, "total");
            message = Objects.requireNonNull(message, "message");
            if (completed < 0L) {
                throw new IllegalArgumentException("completed must be non-negative");
            }
            if (total.isPresent()) {
                if (total.getAsLong() < 0L) {
                    throw new IllegalArgumentException("total must be non-negative");
                }
                if (completed > total.getAsLong()) {
                    throw new IllegalArgumentException("completed must not exceed total");
                }
            }
            if ((state == State.RUNNING || state == State.CANCELLING) && phase.isBlank()) {
                throw new IllegalArgumentException("active operation requires a phase");
            }
            if (state != State.RUNNING && cancellable) {
                throw new IllegalArgumentException("only a running operation may be cancellable");
            }
        }

        public boolean active() {
            return state == State.RUNNING || state == State.CANCELLING;
        }
    }

    private static final Runnable NO_CANCELLATION = () -> { };

    private final Object lock = new Object();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private Snapshot snapshot = idleSnapshot();
    private Runnable cancellationAction = NO_CANCELLATION;

    public Snapshot snapshot() {
        synchronized (lock) {
            return snapshot;
        }
    }

    public Snapshot begin(
            String phase,
            OptionalLong total,
            String message,
            boolean cancellable,
            Runnable cancellationAction) {
        requirePhase(phase);
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(message, "message");
        Runnable checkedAction = Objects.requireNonNull(cancellationAction, "cancellationAction");
        Snapshot updated;
        synchronized (lock) {
            if (snapshot.active()) {
                throw new IllegalStateException("an operation is already active");
            }
            this.cancellationAction = cancellable ? checkedAction : NO_CANCELLATION;
            updated = new Snapshot(State.RUNNING, phase, 0L, total, message, cancellable);
            snapshot = updated;
        }
        publish(updated);
        return updated;
    }

    public Snapshot report(String phase, long completed, OptionalLong total, String message) {
        requirePhase(phase);
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(message, "message");
        Snapshot updated;
        synchronized (lock) {
            if (snapshot.state() != State.RUNNING) {
                throw new IllegalStateException("progress can only be reported while running");
            }
            updated = new Snapshot(State.RUNNING, phase, completed, total, message, snapshot.cancellable());
            snapshot = updated;
        }
        publish(updated);
        return updated;
    }

    public boolean requestCancellation() {
        Runnable action;
        Snapshot updated;
        synchronized (lock) {
            if (snapshot.state() != State.RUNNING || !snapshot.cancellable()) {
                return false;
            }
            updated = new Snapshot(
                    State.CANCELLING,
                    snapshot.phase(),
                    snapshot.completed(),
                    snapshot.total(),
                    "キャンセルしています…",
                    false);
            snapshot = updated;
            action = cancellationAction;
            cancellationAction = NO_CANCELLATION;
        }
        publish(updated);
        action.run();
        return true;
    }

    public Snapshot succeed(String message) {
        return finish(State.SUCCEEDED, message);
    }

    public Snapshot fail(String message) {
        return finish(State.FAILED, requireNonBlank(message, "message"));
    }

    public Snapshot cancelled(String message) {
        return finish(State.CANCELLED, message);
    }

    public Snapshot reset() {
        Snapshot updated = idleSnapshot();
        synchronized (lock) {
            if (snapshot.active()) {
                throw new IllegalStateException("cannot reset an active operation");
            }
            snapshot = updated;
            cancellationAction = NO_CANCELLATION;
        }
        publish(updated);
        return updated;
    }

    public Subscription subscribe(Listener listener) {
        Listener checked = Objects.requireNonNull(listener, "listener");
        listeners.add(checked);
        return () -> listeners.remove(checked);
    }

    private Snapshot finish(State state, String message) {
        Objects.requireNonNull(message, "message");
        Snapshot updated;
        synchronized (lock) {
            if (!snapshot.active()) {
                throw new IllegalStateException("no active operation to finish");
            }
            updated = new Snapshot(
                    state,
                    snapshot.phase(),
                    snapshot.completed(),
                    snapshot.total(),
                    message,
                    false);
            snapshot = updated;
            cancellationAction = NO_CANCELLATION;
        }
        publish(updated);
        return updated;
    }

    private void publish(Snapshot updated) {
        for (Listener listener : listeners) {
            listener.progressChanged(updated);
        }
    }

    private static Snapshot idleSnapshot() {
        return new Snapshot(State.IDLE, "", 0L, OptionalLong.empty(), "", false);
    }

    private static void requirePhase(String phase) {
        String checked = requireNonBlank(phase, "phase");
        if (!checked.matches("[a-z0-9._-]+")) {
            throw new IllegalArgumentException("phase must be a stable lowercase identifier");
        }
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
