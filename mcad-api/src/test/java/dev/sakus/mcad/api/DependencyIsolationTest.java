/*
 * SPDX-License-Identifier: MPL-2.0
 */
package dev.sakus.mcad.api;


import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class DependencyIsolationTest {
    @Test
    void runtimeClasspathContainsNoMinecraftOrLoaderDependency() {
        String classpath = System.getProperty("java.class.path", "").toLowerCase(Locale.ROOT);
        assertFalse(classpath.contains("minecraft"));
        assertFalse(classpath.contains("fabric-loader"));
        assertFalse(classpath.contains("neoforge"));
    }
}
