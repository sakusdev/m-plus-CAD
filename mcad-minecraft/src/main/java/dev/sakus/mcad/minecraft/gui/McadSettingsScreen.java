/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.gui;

import dev.sakus.mcad.api.CanonicalIdentifier;
import dev.sakus.mcad.api.ProjectSettings;
import dev.sakus.mcad.minecraft.runtime.McadRuntime;
import dev.sakus.mcad.minecraft.selection.SelectionOverlayModel;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Functional Minecraft 26.2 settings and operation screen for the MVP. */
public final class McadSettingsScreen extends Screen {
    private static final CanonicalIdentifier GLTF = CanonicalIdentifier.parse("mcad:gltf");
    private static final CanonicalIdentifier OBJ = CanonicalIdentifier.parse("mcad:obj");
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_STEP = 23;

    private final McadRuntime runtime;
    private final Screen parent;

    public McadSettingsScreen(McadRuntime runtime, Screen parent) {
        super(Component.literal("m+CAD"));
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.parent = parent;
    }

    @Override
    protected void init() {
        ProjectSettings settings = runtime.settings();
        int columnWidth = Math.min(210, Math.max(130, (width - 36) / 2));
        int left = width / 2 - columnWidth - 4;
        int right = width / 2 + 4;
        int y = 42;

        add(left, y, columnWidth, "出力形式: " + outputLabel(settings), this::cycleOutput);
        add(right, y, columnWidth, "出力先: " + settings.output().destination(), this::cycleDestinationName);
        y += ROW_STEP;
        add(left, y, columnWidth,
                "内部面除去: " + onOff(settings.geometry().hiddenFaceRemoval()), runtime::toggleHiddenFaceRemoval);
        add(right, y, columnWidth, "マテリアル: " + settings.materials().mode(), runtime::cycleMaterialMode);
        y += ROW_STEP;
        add(left, y, columnWidth, "単位倍率: " + settings.transform().unitScale(), this::cycleScale);
        add(right, y, columnWidth, "座標軸: " + settings.transform().targetAxis(), this::cycleAxis);
        y += ROW_STEP;
        add(left, y, columnWidth, "原点: " + settings.transform().originMode(), this::cycleOrigin);
        add(right, y, columnWidth, "マーカー: " + onOff(settings.markers().enabled()), this::toggleMarkers);
        y += ROW_STEP;
        add(left, y, columnWidth, "コリジョン: " + onOff(settings.collision().enabled()), this::toggleCollision);
        add(right, y, columnWidth, "損失時: " + settings.output().lossPolicy(), this::toggleLossPolicy);
        y += ROW_STEP;
        add(left, y, columnWidth, "選択枠: " + onOff(settings.preview().selectionOutline()), this::toggleOutline);
        add(right, y, columnWidth, "最大ブロック: " + settings.selection().maximumBlockCount(), this::cycleMaximumBlocks);
        y += ROW_STEP;
        add(left, y, columnWidth, "設定を保存", this::save);
        add(right, y, columnWidth, runtime.busy() ? "処理中—キャンセル" : "エクスポート開始", this::startOrCancel);
        y += ROW_STEP;
        addRenderableWidget(Button.builder(Component.literal("閉じる"), button -> onClose())
                .bounds(width / 2 - columnWidth / 2, y, columnWidth, BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(font, title, width / 2 - font.width(title) / 2, 14, 0xFFFFFFFF, true);

        SelectionOverlayModel overlay = runtime.selectionOverlay();
        String selection = overlay.wireframe().isPresent()
                ? "選択: " + overlay.dimensionsLabel() + " / " + overlay.blockCountLabel()
                : "選択: 始点と終点をキーバインドで指定してください";
        graphics.text(font, selection, 18, height - 42, 0xFFE0E0E0, true);

        OperationProgressModel.Snapshot progress = runtime.progress();
        graphics.text(font, progress.state() + "  " + progress.message(), 18, height - 29,
                progress.active() ? 0xFFFFFF55 : 0xFFAAAAAA, true);
        graphics.text(font, runtime.lastMessage(), 18, height - 16, 0xFFAAAAAA, true);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    private void add(int x, int y, int buttonWidth, String label, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), button -> {
            action.run();
            refresh();
        }).bounds(x, y, buttonWidth, BUTTON_HEIGHT).build());
    }

    private void refresh() {
        Minecraft.getInstance().setScreenAndShow(new McadSettingsScreen(runtime, parent));
    }

    private void cycleOutput() {
        String current = runtime.settings().output().destination().toLowerCase(java.util.Locale.ROOT);
        if (current.endsWith(".glb")) {
            runtime.selectExporter(GLTF);
            runtime.updateDestination("exports/model.gltf");
        } else if (current.endsWith(".gltf")) {
            runtime.selectExporter(OBJ);
            runtime.updateDestination("exports/model.obj");
        } else {
            runtime.selectExporter(GLTF);
            runtime.updateDestination("exports/model.glb");
        }
    }

    private void cycleDestinationName() {
        String current = runtime.settings().output().destination();
        int slash = current.lastIndexOf('/');
        String directory = slash >= 0 ? current.substring(0, slash + 1) : "exports/";
        String extension = extensionFor(current);
        String file = slash >= 0 ? current.substring(slash + 1) : current;
        String stem = file.substring(0, Math.max(0, file.length() - extension.length()));
        runtime.updateDestination(directory + (stem.equals("model") ? "structure" : "model") + extension);
    }

    private void cycleScale() {
        edit(settings -> {
            double previous = settings.transform().unitScale();
            double next = previous < 0.75 ? 1.0 : previous < 1.5 ? 2.0 : 0.5;
            ProjectSettings.TransformSettings value = settings.transform();
            return new ProjectSettings.TransformSettings(
                    value.originMode(), value.explicitOriginOffset(), value.rotationDegrees(), next, value.targetAxis());
        });
    }

    private void cycleAxis() {
        edit(settings -> {
            ProjectSettings.TransformSettings value = settings.transform();
            ProjectSettings.AxisConvention[] values = ProjectSettings.AxisConvention.values();
            ProjectSettings.AxisConvention next = values[(value.targetAxis().ordinal() + 1) % values.length];
            return new ProjectSettings.TransformSettings(
                    value.originMode(), value.explicitOriginOffset(), value.rotationDegrees(), value.unitScale(), next);
        });
    }

    private void cycleOrigin() {
        edit(settings -> {
            ProjectSettings.TransformSettings value = settings.transform();
            List<ProjectSettings.OriginMode> supported = List.of(
                    ProjectSettings.OriginMode.SELECTION_MINIMUM,
                    ProjectSettings.OriginMode.SELECTION_CENTRE,
                    ProjectSettings.OriginMode.BOTTOM_CENTRE,
                    ProjectSettings.OriginMode.EXPLICIT_OFFSET,
                    ProjectSettings.OriginMode.MARKER_DEFINED);
            int index = supported.indexOf(value.originMode());
            ProjectSettings.OriginMode next = supported.get((Math.max(index, 0) + 1) % supported.size());
            return new ProjectSettings.TransformSettings(
                    next, value.explicitOriginOffset(), value.rotationDegrees(), value.unitScale(), value.targetAxis());
        });
    }

    private void toggleMarkers() {
        runtime.settingsController().edit(draft -> {
            ProjectSettings.MarkerSettings value = draft.value().markers();
            return draft.withMarkers(new ProjectSettings.MarkerSettings(
                    !value.enabled(), value.consumeSemanticSources(), value.previewInterpretation()));
        });
    }

    private void toggleCollision() {
        runtime.settingsController().edit(draft -> {
            ProjectSettings.CollisionSettings value = draft.value().collision();
            return draft.withCollision(new ProjectSettings.CollisionSettings(!value.enabled(), value.defaultKind()));
        });
    }

    private void toggleLossPolicy() {
        runtime.settingsController().edit(draft -> {
            ProjectSettings.OutputSettings value = draft.value().output();
            ProjectSettings.LossPolicy next = value.lossPolicy() == ProjectSettings.LossPolicy.FAIL
                    ? ProjectSettings.LossPolicy.WARN_AND_CONTINUE
                    : ProjectSettings.LossPolicy.FAIL;
            return draft.withOutput(new ProjectSettings.OutputSettings(
                    value.exporterId(), value.destination(), next, value.exporterOptions()));
        });
    }

    private void toggleOutline() {
        runtime.settingsController().edit(draft -> {
            ProjectSettings.PreviewSettings value = draft.value().preview();
            return draft.withPreview(new ProjectSettings.PreviewSettings(
                    !value.selectionOutline(), value.diagnosticsOverlay(), value.consumedMarkers()));
        });
    }

    private void cycleMaximumBlocks() {
        runtime.settingsController().edit(draft -> {
            long previous = draft.value().selection().maximumBlockCount();
            long next = previous < 65_536L ? 65_536L : previous < 262_144L ? 262_144L : 16_384L;
            return draft.withSelection(new ProjectSettings.SelectionSettings(
                    next, draft.value().selection().preserveEmptyCells()));
        });
    }

    private void save() {
        try {
            runtime.saveSettings();
        } catch (IOException exception) {
            throw new IllegalStateException("設定保存に失敗しました", exception);
        }
    }

    private void startOrCancel() {
        if (runtime.busy()) {
            runtime.requestCancellation();
        } else {
            runtime.startExport(Minecraft.getInstance());
        }
    }

    private void edit(java.util.function.Function<ProjectSettings, ProjectSettings.TransformSettings> editor) {
        runtime.settingsController().edit(draft -> draft.withTransform(editor.apply(draft.value())));
    }

    private static String outputLabel(ProjectSettings settings) {
        String destination = settings.output().destination().toLowerCase(java.util.Locale.ROOT);
        if (destination.endsWith(".glb")) {
            return "GLB";
        }
        if (destination.endsWith(".gltf")) {
            return "glTF + BIN";
        }
        return "OBJ + MTL";
    }

    private static String extensionFor(String path) {
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".gltf")) {
            return ".gltf";
        }
        if (lower.endsWith(".obj")) {
            return ".obj";
        }
        return ".glb";
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }
}
