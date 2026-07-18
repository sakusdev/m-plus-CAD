/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.minecraft;

import net.fabricmc.api.ClientModInitializer;

/** Physical-client entrypoint. Feature-specific registrations are added by their owning modules. */
public final class McadClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Shared client bootstrap intentionally remains minimal until feature PRs are integrated.
    }
}
