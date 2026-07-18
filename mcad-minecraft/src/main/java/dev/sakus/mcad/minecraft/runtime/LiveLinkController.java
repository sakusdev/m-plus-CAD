/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.runtime;

import dev.sakus.mcad.api.BlockPosition;
import dev.sakus.mcad.api.CancellationToken;
import dev.sakus.mcad.api.ProgressReporter;
import dev.sakus.mcad.api.ProjectSettings;
import dev.sakus.mcad.api.StructureSnapshot;
import dev.sakus.mcad.livelink.LiveLinkProtocol;
import dev.sakus.mcad.livelink.LiveLinkSession;
import dev.sakus.mcad.markers.MarkerRuleSet;
import dev.sakus.mcad.minecraft.selection.SelectionBounds;
import dev.sakus.mcad.minecraft.selection.SelectionController;
import dev.sakus.mcad.minecraft.snapshot.FabricClientSnapshotWorldView;
import dev.sakus.mcad.minecraft.snapshot.SnapshotCaptureSession;
import dev.sakus.mcad.minecraft.snapshot.SnapshotCaptureStatus;
import dev.sakus.mcad.minecraft.snapshot.SnapshotLimits;
import dev.sakus.mcad.minecraft.snapshot.SnapshotOptions;
import dev.sakus.mcad.minecraft.snapshot.SnapshotSelection;
import dev.sakus.mcad.minecraft.snapshot.WorldSnapshotAdapter;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;

/** Client-thread Snapshot scheduler and detached GeneratedScene publisher for Blender Live Link. */
public final class LiveLinkController implements AutoCloseable {
    public record Status(
            LiveLinkSession.Status session,
            boolean capturing,
            boolean building,
            String message) {
        public Status {
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(message, "message");
        }
    }

    private static final int SNAPSHOT_CELLS_PER_TICK = 4_096;
    private static final int POLL_INTERVAL_TICKS = 20;

    private final MvpPipeline pipeline;
    private final MarkerRuleSet markerRules;
    private final WorldSnapshotAdapter snapshotAdapter = new WorldSnapshotAdapter();
    private final LiveLinkSession session = new LiveLinkSession();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "m+CAD-live-link-scene-worker");
        thread.setDaemon(true);
        return thread;
    });

    private SnapshotCaptureSession captureSession;
    private Future<?> buildFuture;
    private AtomicBoolean buildCancellation = new AtomicBoolean();
    private ProjectSettings captureSettings;
    private long generation;
    private long observedSelectionRevision = -1L;
    private int observedSettingsHash;
    private int publishedSettingsHash;
    private String publishedSnapshotId;
    private int ticksUntilPoll;
    private int previousClientCount;
    private boolean forceCapture = true;
    private boolean captureForceFull;
    private boolean scenePublished;
    private String message = "停止中";

    public LiveLinkController(MvpPipeline pipeline, MarkerRuleSet markerRules) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.markerRules = Objects.requireNonNull(markerRules, "markerRules");
    }

    public Status start() throws IOException {
        LiveLinkSession.Status started = session.start(LiveLinkProtocol.DEFAULT_PORT);
        forceCapture = true;
        ticksUntilPoll = 0;
        message = "Blender接続待機中: " + started.url().orElse("");
        return status();
    }

    public void stop() {
        cancelPending();
        session.close();
        scenePublished = false;
        publishedSnapshotId = null;
        previousClientCount = 0;
        message = "停止中";
    }

    public Status toggle() throws IOException {
        if (session.status().running()) {
            stop();
            return status();
        }
        return start();
    }

    public void requestSync() {
        if (!session.status().running()) {
            throw new IllegalStateException("Live Link is not running");
        }
        forceCapture = true;
        ticksUntilPoll = 0;
        message = "即時同期を予約しました";
    }

    public void tick(
            Minecraft minecraft,
            SelectionController selectionController,
            ProjectSettings settings,
            boolean exportBusy) {
        Objects.requireNonNull(minecraft, "minecraft");
        Objects.requireNonNull(selectionController, "selectionController");
        Objects.requireNonNull(settings, "settings");
        if (!session.status().running()) {
            return;
        }

        if (minecraft.level == null || minecraft.player == null) {
            if (captureSession != null || (buildFuture != null && !buildFuture.isDone())) {
                cancelPending();
            }
            if (scenePublished) {
                session.clear("world-unavailable");
                scenePublished = false;
                publishedSnapshotId = null;
            }
            observedSelectionRevision = -1L;
            forceCapture = true;
            ticksUntilPoll = 0;
            message = "ワールド参加待ち";
            return;
        }

        if (exportBusy) {
            if (captureSession != null || (buildFuture != null && !buildFuture.isDone())) {
                cancelPending();
            }
            forceCapture = true;
            ticksUntilPoll = 0;
            message = "通常Export完了待ち";
            return;
        }

        int clients = session.status().clientCount();
        if (clients > previousClientCount) {
            forceCapture = true;
            ticksUntilPoll = 0;
        }
        previousClientCount = clients;

        SelectionController.Snapshot selection = selectionController.snapshot();
        int settingsHash = settings.hashCode();
        if (selection.revision() != observedSelectionRevision || settingsHash != observedSettingsHash) {
            observedSelectionRevision = selection.revision();
            observedSettingsHash = settingsHash;
            forceCapture = true;
            ticksUntilPoll = 0;
            cancelPending();
        }

        if (captureSession != null) {
            stepCapture(minecraft);
            return;
        }
        if (buildFuture != null && !buildFuture.isDone()) {
            return;
        }
        if (clients == 0) {
            return;
        }
        if (!forceCapture && ++ticksUntilPoll < POLL_INTERVAL_TICKS) {
            return;
        }
        ticksUntilPoll = 0;

        Optional<SelectionBounds> bounds = selection.validation().bounds();
        if (!selection.validation().valid() || bounds.isEmpty()) {
            if (scenePublished) {
                session.clear("selection-unavailable");
                scenePublished = false;
                publishedSnapshotId = null;
            }
            message = selection.validation().message();
            forceCapture = false;
            return;
        }
        beginCapture(minecraft, bounds.orElseThrow(), settings);
    }

    public Status status() {
        return new Status(
                session.status(),
                captureSession != null,
                buildFuture != null && !buildFuture.isDone(),
                message);
    }

    @Override
    public void close() {
        stop();
        worker.shutdownNow();
    }

    private void beginCapture(Minecraft minecraft, SelectionBounds bounds, ProjectSettings settings) {
        try {
            SnapshotSelection selection = new SnapshotSelection(
                    new BlockPosition(
                            bounds.minInclusive().x(),
                            bounds.minInclusive().y(),
                            bounds.minInclusive().z()),
                    new BlockPosition(
                            Math.toIntExact(bounds.maxExclusiveX()),
                            Math.toIntExact(bounds.maxExclusiveY()),
                            Math.toIntExact(bounds.maxExclusiveZ())));
            long configuredMaximum = settings.selection().maximumBlockCount();
            long maxCells = Math.min(configuredMaximum, SnapshotLimits.defaults().maxCells());
            SnapshotLimits limits = new SnapshotLimits(
                    512,
                    512,
                    512,
                    maxCells,
                    (int) Math.min(maxCells, Integer.MAX_VALUE),
                    1_024);
            captureSettings = settings;
            captureForceFull = forceCapture;
            buildCancellation = new AtomicBoolean();
            CancellationToken cancellation = buildCancellation::get;
            captureSession = snapshotAdapter.beginCapture(
                    FabricClientSnapshotWorldView.current(minecraft),
                    selection,
                    limits,
                    SnapshotOptions.defaults(),
                    ProgressReporter.NONE,
                    cancellation);
            message = "Live Link Snapshot取得中";
            forceCapture = false;
        } catch (IllegalArgumentException | ArithmeticException exception) {
            message = "Live Link Snapshotを開始できません: " + bounded(exception.getMessage());
            captureSession = null;
            captureForceFull = false;
        }
    }

    private void stepCapture(Minecraft minecraft) {
        try {
            if (captureSession.step(SNAPSHOT_CELLS_PER_TICK) != SnapshotCaptureStatus.COMPLETE) {
                return;
            }
            StructureSnapshot snapshot = captureSession.snapshot();
            captureSession = null;
            ProjectSettings settings = Objects.requireNonNull(captureSettings, "captureSettings");
            captureSettings = null;
            boolean forceFull = captureForceFull;
            captureForceFull = false;
            int settingsHash = settings.hashCode();
            if (!forceFull
                    && snapshot.snapshotId().equals(publishedSnapshotId)
                    && settingsHash == publishedSettingsHash) {
                message = "Live Link変更なし";
                return;
            }
            submitSceneBuild(minecraft, snapshot, settings, settingsHash, forceFull);
        } catch (CancellationException exception) {
            captureSession = null;
            captureSettings = null;
            captureForceFull = false;
        } catch (RuntimeException exception) {
            captureSession = null;
            captureSettings = null;
            captureForceFull = false;
            message = "Live Link Snapshot失敗: " + bounded(exception.getMessage());
        }
    }

    private void submitSceneBuild(
            Minecraft minecraft,
            StructureSnapshot snapshot,
            ProjectSettings settings,
            int settingsHash,
            boolean forceFull) {
        long requestedGeneration = Math.incrementExact(generation);
        AtomicBoolean cancellation = buildCancellation;
        buildFuture = worker.submit(() -> {
            try {
                MvpPipeline.SceneBuild build = pipeline.buildScene(
                        snapshot,
                        settings,
                        markerRules,
                        ProgressReporter.NONE,
                        cancellation::get);
                minecraft.execute(() -> finishBuild(
                        requestedGeneration, snapshot.snapshotId(), settingsHash, forceFull, build));
            } catch (CancellationException ignored) {
                // Superseded selection/settings builds are intentionally discarded.
            } catch (RuntimeException exception) {
                minecraft.execute(() -> message = "Live Link Scene生成失敗: " + bounded(exception.getMessage()));
            }
        });
        message = "Live Link Scene生成中";
    }

    private void finishBuild(
            long requestedGeneration,
            String snapshotId,
            int settingsHash,
            boolean forceFull,
            MvpPipeline.SceneBuild build) {
        if (requestedGeneration != generation || !session.status().running()) {
            return;
        }
        LiveLinkSession.PublishResult result = session.publish(build.scene(), forceFull || !scenePublished);
        scenePublished = true;
        publishedSnapshotId = snapshotId;
        publishedSettingsHash = settingsHash;
        message = result.sent()
                ? "Live Link revision " + result.revision() + ": upsert "
                        + result.upsertCount() + " / remove " + result.removeCount()
                : "Live Link変更なし";
    }

    private void cancelPending() {
        generation = Math.incrementExact(generation);
        buildCancellation.set(true);
        captureSession = null;
        captureSettings = null;
        captureForceFull = false;
        if (buildFuture != null && !buildFuture.isDone()) {
            buildFuture.cancel(true);
        }
        buildFuture = null;
    }

    private static String bounded(String value) {
        if (value == null || value.isBlank()) {
            return "不明なエラー";
        }
        return value.length() <= 240 ? value : value.substring(0, 240) + "…";
    }
}
