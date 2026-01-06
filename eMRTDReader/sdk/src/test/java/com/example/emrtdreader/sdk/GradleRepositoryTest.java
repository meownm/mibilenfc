package com.example.emrtdreader.sdk;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class GradleRepositoryTest {

    @Test
    public void tessTwoDependencyUsesMavenCentralCoordinates() throws IOException {
        Path repoRoot = findRepoRoot();
        Path sdkBuildFile = repoRoot.resolve("sdk").resolve("build.gradle");
        String buildGradle = Files.readString(sdkBuildFile, StandardCharsets.UTF_8);

        assertTrue(
            "Expected tess-two dependency to use Maven Central coordinates",
            buildGradle.contains("com.rmtheis:tess-two:9.1.0")
        );
        assertFalse(
            "Legacy Jitpack coordinate should be removed",
            buildGradle.contains("com.github.rmtheis:tess-two")
        );
    }

    @Test
    public void settingsGradleDoesNotReferenceJitpack() throws IOException {
        Path repoRoot = findRepoRoot();
        Path settingsFile = repoRoot.resolve("settings.gradle");
        String settingsGradle = Files.readString(settingsFile, StandardCharsets.UTF_8);

        assertFalse(
            "settings.gradle should not reference Jitpack",
            settingsGradle.contains("jitpack.io")
        );
    }

    private Path findRepoRoot() {
        Path current = Paths.get("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("settings.gradle"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate repo root containing settings.gradle");
    }
}
