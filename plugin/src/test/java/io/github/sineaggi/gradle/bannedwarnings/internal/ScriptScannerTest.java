package io.github.sineaggi.gradle.bannedwarnings.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import io.github.sineaggi.gradle.bannedwarnings.BannedWarningsExtension;
import org.junit.jupiter.api.Test;

class ScriptScannerTest {

    private static final RuleSpec DELEGATES = TestRules.delegates();

    @Test
    void findsDeprecatedDelegates() {
        String script = """
                val foo by tasks.registering {
                }
                val bar by tasks.registering(Copy::class)
                val myConf by configurations.creating
                val main by sourceSets.getting
                val jar by tasks.existing
                val x by extra("a")
                val y: String by project
                val ok = tasks.register("ok")
                val lazyValue by lazy { 1 }
                // val commented by tasks.registering
                /* val blockCommented by tasks.registering
                   val stillCommented by creating */
                val url = "https://example.com" // by getting
                """;
        List<Integer> lines = ScriptScanner.scan(script, "build.gradle.kts", List.of(DELEGATES)).stream()
                .map(Occurrence::line).toList();
        assertEquals(List.of(1, 3, 4, 5, 6, 7, 8), lines);
    }

    @Test
    void reportsRuleAndPath() {
        List<Occurrence> found = ScriptScanner.scan("val foo by tasks.registering", "sub/build.gradle.kts", List.of(DELEGATES));
        assertEquals(List.of(new Occurrence(BannedWarningsExtension.KOTLIN_DSL_DELEGATED_PROPERTIES, "sub/build.gradle.kts", 1,
                "val foo by tasks.registering")), found);
    }
}
