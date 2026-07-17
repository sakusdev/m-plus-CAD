/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.selection;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe client-side owner of the current selection.
 *
 * <p>Minecraft interaction code may update this controller on the client thread while overlays and
 * screens consume immutable snapshots. No world or loader object is retained.</p>
 */
public final class SelectionController {
    @FunctionalInterface
    public interface Listener {
        void selectionChanged(Snapshot snapshot);
    }

    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }

    public record Snapshot(long revision, TwoPointSelection selection, SelectionValidation validation) {
        public Snapshot {
            if (revision < 0L) {
                throw new IllegalArgumentException("revision must be non-negative");
            }
            Objects.requireNonNull(selection, "selection");
            Objects.requireNonNull(validation, "validation");
        }
    }

    private final Object lock = new Object();
    private final Object publishLock = new Object();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final ArrayDeque<Snapshot> pendingPublications = new ArrayDeque<>();
    private TwoPointSelection selection = TwoPointSelection.empty();
    private long maximumBlockCount;
    private long revision;
    private long highestQueuedRevision = -1L;
    private boolean publishing;

    public SelectionController(long maximumBlockCount) {
        requirePositive(maximumBlockCount);
        this.maximumBlockCount = maximumBlockCount;
    }

    public Snapshot snapshot() {
        synchronized (lock) {
            return snapshotLocked();
        }
    }

    public Snapshot setCorner(TwoPointSelection.Corner corner, SelectionPoint point) {
        Objects.requireNonNull(corner, "corner");
        Objects.requireNonNull(point, "point");
        return update(current -> current.withCorner(corner, point));
    }

    public Snapshot clearCorner(TwoPointSelection.Corner corner) {
        Objects.requireNonNull(corner, "corner");
        return update(current -> current.clearCorner(corner));
    }

    public Snapshot clear() {
        return update(TwoPointSelection::clear);
    }

    public Snapshot setMaximumBlockCount(long newMaximumBlockCount) {
        requirePositive(newMaximumBlockCount);
        Snapshot updated;
        synchronized (lock) {
            if (maximumBlockCount == newMaximumBlockCount) {
                return snapshotLocked();
            }
            long nextRevision = Math.incrementExact(revision);
            maximumBlockCount = newMaximumBlockCount;
            revision = nextRevision;
            updated = snapshotLocked();
        }
        publish(updated);
        return updated;
    }

    public Subscription subscribe(Listener listener) {
        Listener checked = Objects.requireNonNull(listener, "listener");
        listeners.add(checked);
        return () -> listeners.remove(checked);
    }

    private Snapshot update(java.util.function.UnaryOperator<TwoPointSelection> operation) {
        Snapshot updated;
        synchronized (lock) {
            TwoPointSelection next = Objects.requireNonNull(operation.apply(selection), "updated selection");
            if (next.equals(selection)) {
                return snapshotLocked();
            }
            long nextRevision = Math.incrementExact(revision);
            selection = next;
            revision = nextRevision;
            updated = snapshotLocked();
        }
        publish(updated);
        return updated;
    }

    private Snapshot snapshotLocked() {
        return new Snapshot(revision, selection, selection.validate(maximumBlockCount));
    }

    private void publish(Snapshot snapshot) {
        synchronized (publishLock) {
            if (snapshot.revision() <= highestQueuedRevision) {
                return;
            }
            pendingPublications.addLast(snapshot);
            highestQueuedRevision = snapshot.revision();
            if (publishing) {
                return;
            }
            publishing = true;
        }

        RuntimeException failure = null;
        try {
            while (true) {
                Snapshot next;
                synchronized (publishLock) {
                    next = pendingPublications.pollFirst();
                    if (next == null) {
                        publishing = false;
                        break;
                    }
                }
                for (Listener listener : listeners) {
                    try {
                        listener.selectionChanged(next);
                    } catch (RuntimeException exception) {
                        if (failure == null) {
                            failure = exception;
                        } else if (failure != exception) {
                            failure.addSuppressed(exception);
                        }
                    }
                }
            }
        } catch (Error error) {
            synchronized (publishLock) {
                publishing = false;
            }
            throw error;
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static void requirePositive(long value) {
        if (value <= 0L) {
            throw new IllegalArgumentException("maximumBlockCount must be positive");
        }
    }
}
