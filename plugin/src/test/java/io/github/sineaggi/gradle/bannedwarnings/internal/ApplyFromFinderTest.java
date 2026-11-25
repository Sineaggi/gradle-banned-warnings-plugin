package io.github.sineaggi.gradle.bannedwarnings.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Test;

class ApplyFromFinderTest {

    private static final File ROOT = new File("/build");
    private static final File PROJECT = new File("/build/sub");

    @Test
    void findsAppliedKotlinScripts() {
        String script = """
                apply(from = "gradle/a.gradle.kts")
                apply(from = file("b.gradle.kts"))
                apply(from = isolated.rootProject.projectDirectory.file("gradle/c.gradle.kts"))
                apply(from = "$rootDir/gradle/d.gradle.kts")
                apply(from = "${projectDir}/e.gradle.kts")
                apply(from = rootDir.resolve("gradle/f.gradle.kts"))
                apply(from = layout.settingsDirectory.file("gradle/g.gradle.kts"))
                apply {
                    from("h.gradle.kts")
                }
                apply(from = "/abs/i.gradle.kts")
                """;
        assertEquals(List.of(
                new File("/build/sub/gradle/a.gradle.kts"),
                new File("/build/sub/b.gradle.kts"),
                new File("/build/gradle/c.gradle.kts"),
                new File("/build/gradle/d.gradle.kts"),
                new File("/build/sub/e.gradle.kts"),
                new File("/build/gradle/f.gradle.kts"),
                new File("/build/gradle/g.gradle.kts"),
                new File("/abs/i.gradle.kts"),
                new File("/build/sub/h.gradle.kts")
        ), ApplyFromFinder.find(script, PROJECT, ROOT));
    }

    @Test
    void skipsWhatCannotBeResolvedStatically() {
        String script = """
                // apply(from = "commented.gradle.kts")
                apply(from = "https://example.com/remote.gradle.kts")
                apply(from = "$someVar/x.gradle.kts")
                apply(from = "groovy.gradle")
                apply(from = computePath())
                apply(plugin = "java")
                """;
        assertEquals(List.of(), ApplyFromFinder.find(script, PROJECT, ROOT));
    }
}
