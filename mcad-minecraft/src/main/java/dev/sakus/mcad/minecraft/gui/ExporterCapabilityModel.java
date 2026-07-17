/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.gui;

import dev.sakus.mcad.api.ExporterCapabilities;
import dev.sakus.mcad.api.ExporterFeature;
import dev.sakus.mcad.api.ExporterLimits;

import java.util.Collections;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Capability-derived enabled state for exporter-sensitive GUI controls.
 */
public record ExporterCapabilityModel(
        NavigableMap<Control, ControlAvailability> controls,
        ExporterLimits limits) {

    public enum Control {
        HIERARCHY(ExporterFeature.HIERARCHY, "階層"),
        MULTIPLE_MESHES(ExporterFeature.MULTIPLE_MESHES, "複数メッシュ"),
        MULTIPLE_MATERIALS(ExporterFeature.MULTIPLE_MATERIALS, "複数マテリアル"),
        VERTEX_COLOURS(ExporterFeature.VERTEX_COLOURS, "頂点カラー"),
        ALPHA_MODES(ExporterFeature.ALPHA_MODES, "アルファモード"),
        CUSTOM_PROPERTIES(ExporterFeature.CUSTOM_PROPERTIES, "カスタムプロパティ"),
        LIGHTS(ExporterFeature.LIGHTS, "ライト"),
        CAMERAS(ExporterFeature.CAMERAS, "カメラ"),
        CURVES(ExporterFeature.CURVES, "カーブ"),
        BONES(ExporterFeature.BONES, "ボーン"),
        ANIMATION(ExporterFeature.ANIMATION, "アニメーション"),
        COLLISION_METADATA(ExporterFeature.COLLISION_METADATA, "コリジョンメタデータ"),
        EMBEDDED_BINARY_ASSETS(ExporterFeature.EMBEDDED_BINARY_ASSETS, "バイナリアセット埋め込み"),
        EXTERNAL_FILE_SETS(ExporterFeature.EXTERNAL_FILE_SETS, "複数外部ファイル出力");

        private final ExporterFeature requiredFeature;
        private final String displayName;

        Control(ExporterFeature requiredFeature, String displayName) {
            this.requiredFeature = requiredFeature;
            this.displayName = displayName;
        }

        ExporterFeature requiredFeature() {
            return requiredFeature;
        }

        String displayName() {
            return displayName;
        }
    }

    public ExporterCapabilityModel {
        Objects.requireNonNull(controls, "controls");
        controls = Collections.unmodifiableNavigableMap(new TreeMap<>(controls));
        limits = Objects.requireNonNull(limits, "limits");
    }

    public static ExporterCapabilityModel from(ExporterCapabilities capabilities) {
        Objects.requireNonNull(capabilities, "capabilities");
        NavigableMap<Control, ControlAvailability> controls = new TreeMap<>();
        for (Control control : Control.values()) {
            boolean supported = capabilities.features().contains(control.requiredFeature());
            controls.put(control, supported
                    ? ControlAvailability.available()
                    : ControlAvailability.unavailable(
                            "選択中の出力形式は「" + control.displayName() + "」に対応していません。"));
        }
        return new ExporterCapabilityModel(controls, capabilities.limits());
    }

    public ControlAvailability availability(Control control) {
        Objects.requireNonNull(control, "control");
        return controls.get(control);
    }
}
