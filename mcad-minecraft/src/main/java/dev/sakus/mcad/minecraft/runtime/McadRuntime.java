/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.runtime;

import dev.sakus.mcad.api.BlockPosition;
import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.ExportResult;
import dev.sakus.mcad.api.ExportStatus;
import dev.sakus.mcad.api.ModelExporter;
import dev.sakus.mcad.api.ProjectSettings;
import dev.sakus.mcad.api.StructureSnapshot;
import dev.sakus.mcad.markers.MarkerRuleSet;
import dev.sakus.mcad.minecraft.gui.OperationProgressBridge;
import dev.sakus.mcad.minecraft.gui.OperationProgressModel;
import dev.sakus.mcad.minecraft.gui.settings.ProjectSettingsCodec;
import dev.sakus.mcad.minecraft.gui.settings.ProjectSettingsDraft;
import dev.sakus.mcad.minecraft.gui.settings.ProjectSettingsStore;
import dev.sakus.mcad.minecraft.gui.settings.SettingsShellController;
import dev.sakus.mcad.minecraft.selection.SelectionBounds;
import dev.sakus.mcad.minecraft.selection.SelectionController;
import dev.sakus.mcad.minecraft.selection.SelectionInteractionController;
import dev.sakus.mcad.minecraft.selection.SelectionOverlayModel;
import dev.sakus.mcad.minecraft.selection.SelectionPoint;
import dev.sakus.mcad.minecraft.snapshot.FabricClientSnapshotWorldView;
import dev.sakus.mcad.minecraft.snapshot.SnapshotCaptureSession;
import dev.sakus.mcad.minecraft.snapshot.SnapshotCaptureStatus;
import dev.sakus.mcad.minecraft.snapshot.SnapshotLimits;
import dev.sakus.mcad.minecraft.snapshot.SnapshotOptions;
import dev.sakus.mcad.minecraft.snapshot.SnapshotSelection;
import dev.sakus.mcad.minecraft.snapshot.WorldSnapshotAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Owns all client-side m+CAD state and coordinates safe staged export operations. */
public final class McadRuntime implements AutoCloseable {
    private static final Path SETTINGS_FILE = Path.of("settings.mcad");
    private static final int SNAPSHOT_CELLS_PER_TICK = 8_192;

    private final SelectionController selectionController;
    private final SelectionInteractionController selectionInteraction;
    private final SettingsShellController settingsController;
    private final OperationProgressModel progressModel = new OperationProgressModel();
    private final OperationProgressBridge progressBridge = new OperationProgressBridge(progressModel);
    private final ExporterRegistry exporterRegistry = ExporterRegistry.builtIns();
    private final WorldSnapshotAdapter snapshotAdapter = new WorldSnapshotAdapter();
    private final MvpPipeline pipeline = new MvpPipeline();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "m+CAD-export-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final ProjectSettingsStore settingsStore;
    private final Path outputRoot;
    private final MarkerRuleSet markerRules = new MarkerRuleSet(MarkerRuleSet.CURRENT_SCHEMA_VERSION, List.of());

    private SnapshotCaptureSession captureSession;
    private ProjectSettings activeSettings;
    private ModelExporter activeExporter;
    private Path activeDestination;
    private Future<?> activeWorker;
    private MvpPipeline.Output lastOutput;
    private String lastMessage = "準備完了";

    public McadRuntime(Minecraft minecraft) {
        Objects.requireNonNull(minecraft, "minecraft");
        Path gameRoot = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        outputRoot = gameRoot.resolve("m-plus-cad").normalize();
        try {
            Files.createDirectories(outputRoot);
            settingsStore = new ProjectSettingsStore(
                    gameRoot.resolve("config/m-plus-cad"), new ProjectSettingsCodec());
        } catch (IOException exception) {
            throw new IllegalStateException("m+CAD runtime directories could not be initialized", exception);
        }

        ProjectSettings initial = loadSettings();
        settingsController = new SettingsShellController(initial);
        selectionController = new SelectionController(initial.selection().maximumBlockCount());
        selectionInteraction = new SelectionInteractionController(selectionController);
        exporterRegistry.find(initial.output().exporterId()).ifPresent(exporter ->
                settingsController.selectExporter(exporter.formatId(), exporter.capabilities()));
    }

    public SelectionController selectionController() {
        return selectionController;
    }

    public SelectionInteractionController selectionInteraction() {
        return selectionInteraction;
    }

    public SettingsShellController settingsController() {
        return settingsController;
    }

    public ExporterRegistry exporterRegistry() {
        return exporterRegistry;
    }

    public OperationProgressModel.Snapshot progress() {
        return progressModel.snapshot();
    }

    public SelectionOverlayModel selectionOverlay() {
        return SelectionOverlayModel.from(selectionController.snapshot());
    }

    public ProjectSettings settings() {
        return settingsController.snapshot().draft().value();
    }

    public String lastMessage() {
        return lastMessage;
    }

    public Optional<MvpPipeline.Output> lastOutput() {
        return Optional.ofNullable(lastOutput);
    }

    public boolean busy() {
        return captureSession != null || (activeWorker != null && !activeWorker.isDone())
                || progressModel.snapshot().active();
    }

    public Optional<SelectionPoint> targetedBlock(Minecraft minecraft) {
        Objects.requireNonNull(minecraft, "minecraft");
        HitResult hit = minecraft.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return Optional.empty();
        }
        var pos = blockHit.getBlockPos();
        return Optional.of(new SelectionPoint(pos.getX(), pos.getY(), pos.getZ()));
    }

    public void tick(Minecraft minecraft) {
        Objects.requireNonNull(minecraft, "minecraft");
        if (captureSession == null) {
            return;
        }
        try {
            SnapshotCaptureStatus status = captureSession.step(SNAPSHOT_CELLS_PER_TICK);
            if (status == SnapshotCaptureStatus.COMPLETE) {
                StructureSnapshot snapshot = captureSession.snapshot();
                captureSession = null;
                submitDetachedPipeline(minecraft, snapshot);
            }
        } catch (CancellationException exception) {
            captureSession = null;
            finishCancelled(minecraft, "エクスポートをキャンセルしました");
        } catch (RuntimeException exception) {
            captureSession = null;
            finishFailure(minecraft, "Snapshot取得に失敗しました: " + bounded(exception.getMessage()));
        }
    }

    public boolean startExport(Minecraft minecraft) {
        Objects.requireNonNull(minecraft, "minecraft");
        if (busy()) {
            notifyPlayer(minecraft, "m+CAD: 既に処理中です");
            return false;
        }
        if (minecraft.level == null || minecraft.player == null) {
            notifyPlayer(minecraft, "m+CAD: ワールドに参加してから実行してください");
            return false;
        }

        var selectionSnapshot = selectionController.snapshot();
        Optional<SelectionBounds> bounds = selectionSnapshot.validation().bounds();
        if (!selectionSnapshot.validation().valid() || bounds.isEmpty()) {
            notifyPlayer(minecraft, "m+CAD: " + selectionSnapshot.validation().message());
            return false;
        }

        activeSettings = settings();
        activeExporter = exporterRegistry.find(activeSettings.output().exporterId()).orElse(null);
        if (activeExporter == null) {
            notifyPlayer(minecraft, "m+CAD: 未知の出力形式です: " + activeSettings.output().exporterId());
            clearActiveRequest();
            return false;
        }

        try {
            activeDestination = resolveOutput(activeSettings.output().destination());
            Files.createDirectories(Objects.requireNonNull(activeDestination.getParent(), "output parent"));
            SnapshotSelection selection = snapshotSelection(bounds.orElseThrow());
            long maxCells = activeSettings.selection().maximumBlockCount();
            SnapshotLimits limits = new SnapshotLimits(
                    512,
                    512,
                    512,
                    maxCells,
                    (int) Math.min(maxCells, Integer.MAX_VALUE),
                    1_024);

            resetProgressIfFinished();
            progressBridge.begin(
                    "snapshot-read",
                    OptionalLong.of(selection.volume()),
                    "選択範囲をSnapshotへコピーしています",
                    true);
            captureSession = snapshotAdapter.beginCapture(
                    FabricClientSnapshotWorldView.current(minecraft),
                    selection,
                    limits,
                    SnapshotOptions.defaults(),
                    progressBridge,
                    progressBridge);
            lastMessage = "Snapshot取得中";
            notifyPlayer(minecraft, "m+CAD: エクスポートを開始しました");
            return true;
        } catch (IOException | IllegalArgumentException | ArithmeticException exception) {
            clearActiveRequest();
            finishFailure(minecraft, "エクスポートを開始できません: " + bounded(exception.getMessage()));
            return false;
        }
    }

    public boolean requestCancellation() {
        if (!busy()) {
            return false;
        }
        return progressModel.requestCancellation();
    }

    public void selectExporter(CanonicalIdentifier exporterId) {
        ModelExporter exporter = exporterRegistry.require(exporterId);
        settingsController.selectExporter(exporterId, exporter.capabilities());
    }

    public void updateDestination(String destination) {
        Objects.requireNonNull(destination, "destination");
        settingsController.edit(draft -> {
            ProjectSettings.OutputSettings previous = draft.value().output();
            return draft.withOutput(new ProjectSettings.OutputSettings(
                    previous.exporterId(), destination, previous.lossPolicy(), previous.exporterOptions()));
        });
    }

    public void toggleHiddenFaceRemoval() {
        settingsController.edit(draft -> {
            ProjectSettings.GeometrySettings previous = draft.value().geometry();
            return draft.withGeometry(new ProjectSettings.GeometrySettings(
                    !previous.hiddenFaceRemoval(), previous.rejectDegenerateTriangles()));
        });
    }

    public void cycleMaterialMode() {
        settingsController.edit(draft -> {
            ProjectSettings.MaterialMode previous = draft.value().materials().mode();
            ProjectSettings.MaterialMode[] values = ProjectSettings.MaterialMode.values();
            ProjectSettings.MaterialMode next = values[(previous.ordinal() + 1) % values.length];
            if (next == ProjectSettings.MaterialMode.USER_DEFINED_MAPPING) {
                next = ProjectSettings.MaterialMode.DETERMINISTIC_IDENTIFICATION_COLOURS;
            }
            return draft.withMaterials(new ProjectSettings.MaterialSettings(next, Optional.empty()));
        });
    }

    public Path saveSettings() throws IOException {
        ProjectSettings saved = settings();
        Path path = settingsStore.save(SETTINGS_FILE, saved);
        selectionController.setMaximumBlockCount(saved.selection().maximumBlockCount());
        lastMessage = "設定を保存しました";
        return path;
    }

    @Override
    public void close() {
        requestCancellation();
        if (activeWorker != null) {
            activeWorker.cancel(true);
        }
        worker.shutdownNow();
    }

    private void submitDetachedPipeline(Minecraft minecraft, StructureSnapshot snapshot) {
        ProjectSettings requestSettings = Objects.requireNonNull(activeSettings, "activeSettings");
        ModelExporter exporter = Objects.requireNonNull(activeExporter, "activeExporter");
        Path destination = Objects.requireNonNull(activeDestination, "activeDestination");
        activeWorker = worker.submit(() -> {
            try {
                MvpPipeline.Output output = pipeline.run(
                        snapshot,
                        requestSettings,
                        markerRules,
                        exporter,
                        destination,
                        progressBridge,
                        progressBridge);
                minecraft.execute(() -> finishOutput(minecraft, output));
            } catch (CancellationException exception) {
                minecraft.execute(() -> finishCancelled(minecraft, "エクスポートをキャンセルしました"));
            } catch (IOException | RuntimeException exception) {
                minecraft.execute(() -> finishFailure(
                        minecraft, "エクスポートに失敗しました: " + bounded(exception.getMessage())));
            }
        });
    }

    private void finishOutput(Minecraft minecraft, MvpPipeline.Output output) {
        lastOutput = output;
        ExportResult result = output.exportResult();
        if (result.status() == ExportStatus.SUCCESS) {
            String message = "エクスポート完了: " + activeDestination;
            lastMessage = message;
            progressBridge.succeed(message);
            notifyPlayer(minecraft, "m+CAD: " + message);
        } else if (result.status() == ExportStatus.CANCELLED) {
            finishCancelled(minecraft, "エクスポートをキャンセルしました");
        } else {
            String diagnostic = result.diagnostics().stream()
                    .filter(value -> value.severity() == dev.sakus.mcad.api.DiagnosticSeverity.ERROR)
                    .map(dev.sakus.mcad.api.Diagnostic::message)
                    .findFirst()
                    .orElse("Exporterが失敗を返しました");
            finishFailure(minecraft, diagnostic);
        }
        clearActiveRequest();
    }

    private void finishFailure(Minecraft minecraft, String message) {
        lastMessage = message;
        if (progressModel.snapshot().active()) {
            progressBridge.fail(message);
        }
        notifyPlayer(minecraft, "m+CAD: " + message);
        clearActiveRequest();
    }

    private void finishCancelled(Minecraft minecraft, String message) {
        lastMessage = message;
        if (progressModel.snapshot().active()) {
            progressBridge.cancelled(message);
        }
        notifyPlayer(minecraft, "m+CAD: " + message);
        clearActiveRequest();
    }

    private void resetProgressIfFinished() {
        if (progressModel.snapshot().state() != OperationProgressModel.State.IDLE) {
            progressModel.reset();
        }
    }

    private ProjectSettings loadSettings() {
        try {
            if (Files.isRegularFile(settingsStore.root().resolve(SETTINGS_FILE))) {
                return settingsStore.load(SETTINGS_FILE, RuntimeDefaults.projectSettings()).settings();
            }
        } catch (IOException | IllegalArgumentException exception) {
            lastMessage = "設定読込に失敗したため既定値を使用します: " + bounded(exception.getMessage());
        }
        return RuntimeDefaults.projectSettings();
    }

    private Path resolveOutput(String relative) throws IOException {
        Path value = Path.of(relative);
        if (value.isAbsolute() || relative.contains("\\") || relative.contains(":")) {
            throw new IOException("出力先はportableな相対パスで指定してください");
        }
        Path resolved = outputRoot.resolve(value).normalize();
        if (!resolved.startsWith(outputRoot)) {
            throw new IOException("出力先がm+CAD output root外へ出ています");
        }
        return resolved;
    }

    private static SnapshotSelection snapshotSelection(SelectionBounds bounds) {
        int maxX = Math.toIntExact(bounds.maxExclusiveX());
        int maxY = Math.toIntExact(bounds.maxExclusiveY());
        int maxZ = Math.toIntExact(bounds.maxExclusiveZ());
        return new SnapshotSelection(
                new BlockPosition(
                        bounds.minInclusive().x(), bounds.minInclusive().y(), bounds.minInclusive().z()),
                new BlockPosition(maxX, maxY, maxZ));
    }

    private void clearActiveRequest() {
        captureSession = null;
        activeSettings = null;
        activeExporter = null;
        activeDestination = null;
        activeWorker = null;
    }

    private static void notifyPlayer(Minecraft minecraft, String message) {
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(message), true);
        }
    }

    private static String bounded(String value) {
        if (value == null || value.isBlank()) {
            return "不明なエラー";
        }
        return value.length() <= 300 ? value : value.substring(0, 297) + "...";
    }
}
