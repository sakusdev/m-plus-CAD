/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.livelink;

/** Stable constants for the localhost Minecraft-to-Blender live-link protocol. */
public final class LiveLinkProtocol {
    public static final String NAME = "mcad-live-link";
    public static final int VERSION = 1;
    public static final int DEFAULT_PORT = 8765;
    public static final int MAX_HANDSHAKE_BYTES = 16 * 1024;
    public static final int MAX_INBOUND_FRAME_BYTES = 1024 * 1024;

    private LiveLinkProtocol() {
    }
}
