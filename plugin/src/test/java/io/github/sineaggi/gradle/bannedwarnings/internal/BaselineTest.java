package io.github.sineaggi.gradle.bannedwarnings.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BaselineTest {

    @Test
    void countsDistinctLinesAndAllowsUpToBaseline() {
        List<Occurrence> found = List.of(
                new Occurrence("r", "a.kt", 1, "x"),
                new Occurrence("r", "a.kt", 1, "y"), // same line, several compiler warnings
                new Occurrence("r", "a.kt", 2, "x"));
        assertEquals(Map.of(new Baseline.Key("r", "a.kt"), 2), Baseline.count(found));
        assertTrue(Baseline.violations(found, Map.of(new Baseline.Key("r", "a.kt"), 2)).isEmpty());
        assertEquals(3, Baseline.violations(found, Map.of(new Baseline.Key("r", "a.kt"), 1)).size());
        assertEquals(3, Baseline.violations(found, Map.of()).size());
    }

    @Test
    void updateReplacesOnlyOwnedEntries(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("baseline.txt");
        Baseline.update(file, k -> true, Map.of(new Baseline.Key("r", "a.kt"), 2, new Baseline.Key("r", "b.kt"), 1));
        Baseline.update(file, k -> k.path().equals("a.kt"), Map.of());
        assertEquals(Map.of(new Baseline.Key("r", "b.kt"), 1), Baseline.read(file));
        assertTrue(Files.readString(file).startsWith("#"));
        Baseline.update(file, k -> true, Map.of());
        assertFalse(Files.exists(file));
    }

    @Test
    void roundTrips() {
        Map<Baseline.Key, Integer> entries = Map.of(new Baseline.Key("r", "a b/c.kt"), 3);
        assertEquals(entries, Baseline.parse(Baseline.render(entries)));
    }
}
