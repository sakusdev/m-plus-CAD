/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft.selection;

/**
 * One absolute Minecraft block position selected by the client.
 *
 * <p>The type deliberately contains only primitive coordinates so no live Minecraft object can
 * escape into selection state or a worker thread.</p>
 */
public record SelectionPoint(int x, int y, int z) {
}
