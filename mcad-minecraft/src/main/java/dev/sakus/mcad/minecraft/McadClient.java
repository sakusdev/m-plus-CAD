/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft;

import com.mojang.blaze3d.platform.InputConstants;
import dev.sakus.mcad.minecraft.gui.McadSettingsScreen;
import dev.sakus.mcad.minecraft.runtime.McadRuntime;
import dev.sakus.mcad.minecraft.runtime.SelectionParticleOverlay;
import dev.sakus.mcad.minecraft.selection.SelectionInteractionController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** Physical-client entrypoint wiring export and Blender Live Link. */
public final class McadClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("m_plus_cad", "main"));

    private McadRuntime runtime;
    private SelectionParticleOverlay selectionOverlay;
    private KeyMapping setStart;
    private KeyMapping setEnd;
    private KeyMapping clearSelection;
    private KeyMapping openSettings;
    private KeyMapping export;
    private KeyMapping cancel;
    private KeyMapping toggleLiveLink;
    private KeyMapping syncLiveLink;

    @Override
    public void onInitializeClient() {
        Minecraft minecraft = Minecraft.getInstance();
        runtime = new McadRuntime(minecraft);
        selectionOverlay = new SelectionParticleOverlay();

        setStart = register("key.m_plus_cad.set_start", GLFW.GLFW_KEY_G);
        setEnd = register("key.m_plus_cad.set_end", GLFW.GLFW_KEY_H);
        clearSelection = register("key.m_plus_cad.clear_selection", GLFW.GLFW_KEY_J);
        openSettings = register("key.m_plus_cad.open_settings", GLFW.GLFW_KEY_O);
        export = register("key.m_plus_cad.export", GLFW.GLFW_KEY_P);
        cancel = register("key.m_plus_cad.cancel", GLFW.GLFW_KEY_K);
        toggleLiveLink = register("key.m_plus_cad.toggle_live_link", GLFW.GLFW_KEY_L);
        syncLiveLink = register("key.m_plus_cad.sync_live_link", GLFW.GLFW_KEY_I);

        ClientTickEvents.END_CLIENT_TICK.register(this::endClientTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> runtime.close());
    }

    private void endClientTick(Minecraft minecraft) {
        while (setStart.consumeClick()) {
            applySelection(minecraft, SelectionInteractionController.Action.SET_START);
        }
        while (setEnd.consumeClick()) {
            applySelection(minecraft, SelectionInteractionController.Action.SET_END);
        }
        while (clearSelection.consumeClick()) {
            runtime.selectionInteraction().apply(
                    SelectionInteractionController.Action.CLEAR_ALL, java.util.Optional.empty());
            notify(minecraft, "m+CAD: 選択を解除しました");
        }
        while (openSettings.consumeClick()) {
            minecraft.setScreenAndShow(new McadSettingsScreen(runtime, minecraft.gui.screen()));
        }
        while (export.consumeClick()) {
            runtime.startExport(minecraft);
        }
        while (cancel.consumeClick()) {
            if (!runtime.requestCancellation()) {
                notify(minecraft, "m+CAD: キャンセル可能な処理はありません");
            }
        }
        while (toggleLiveLink.consumeClick()) {
            runtime.toggleLiveLink(minecraft);
        }
        while (syncLiveLink.consumeClick()) {
            runtime.requestLiveLinkSync(minecraft);
        }

        runtime.tick(minecraft);
        selectionOverlay.tick(
                minecraft,
                runtime.selectionOverlay(),
                runtime.settings().preview().selectionOutline());
    }

    private void applySelection(Minecraft minecraft, SelectionInteractionController.Action action) {
        var target = runtime.targetedBlock(minecraft);
        if (target.isEmpty()) {
            notify(minecraft, "m+CAD: ブロックに照準を合わせてください");
            return;
        }
        var result = runtime.selectionInteraction().apply(action, target);
        String label = action == SelectionInteractionController.Action.SET_START ? "始点" : "終点";
        notify(minecraft, "m+CAD: " + label + "を " + target.orElseThrow() + " に設定しました");
        String validation = result.snapshot().validation().message();
        if (!validation.isBlank()) {
            notify(minecraft, "m+CAD: " + validation);
        }
    }

    private static KeyMapping register(String translationKey, int keyCode) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                translationKey,
                InputConstants.Type.KEYSYM,
                keyCode,
                CATEGORY));
    }

    private static void notify(Minecraft minecraft, String message) {
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal(message));
        }
    }
}
