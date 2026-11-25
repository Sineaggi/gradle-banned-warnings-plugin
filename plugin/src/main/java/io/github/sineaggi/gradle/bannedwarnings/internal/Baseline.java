package io.github.sineaggi.gradle.bannedwarnings.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * The baseline file: how many lines of each file may break each rule.
 *
 * <pre>
 * # comment
 * &lt;rule id&gt;	&lt;path relative to the build root&gt;	&lt;allowed line count&gt;
 * </pre>
 */
public final class Baseline {

    public record Key(String ruleId, String path) implements Comparable<Key> {
        @Override
        public int compareTo(Key o) {
            int c = path.compareTo(o.path);
            return c != 0 ? c : ruleId.compareTo(o.ruleId);
        }
    }

    static final String HEADER = """
            # Banned warnings baseline: existing occurrences that are allowed, as <rule>\t<file>\t<line count>.
            # Fix them and remove the lines. Regenerate with: ./gradlew build -PbannedWarnings.updateBaseline
            """;

    private static final Map<Path, Object> LOCKS = new ConcurrentHashMap<>();

    private Baseline() {
    }

    public static SortedMap<Key, Integer> parse(String text) {
        SortedMap<Key, Integer> entries = new TreeMap<>();
        if (text == null) {
            return entries;
        }
        int lineNumber = 0;
        for (String line : text.split("\\R")) {
            lineNumber++;
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] parts = trimmed.split("\t");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Malformed banned-warnings baseline line " + lineNumber + ": " + line);
            }
            entries.merge(new Key(parts[0], parts[1]), Integer.parseInt(parts[2].strip()), Integer::sum);
        }
        return entries;
    }

    public static String render(Map<Key, Integer> entries) {
        StringBuilder sb = new StringBuilder(HEADER);
        new TreeMap<>(entries).forEach((key, count) -> {
            if (count > 0) {
                sb.append(key.ruleId()).append('\t').append(key.path()).append('\t').append(count).append('\n');
            }
        });
        return sb.toString();
    }

    public static SortedMap<Key, Integer> read(Path file) {
        synchronized (lock(file)) {
            return readUnlocked(file);
        }
    }

    /**
     * Replaces the entries a reporter owns with what it found. Safe to call from tasks running in parallel
     * in the same build.
     */
    public static void update(Path file, Predicate<Key> owned, Map<Key, Integer> found) {
        synchronized (lock(file)) {
            SortedMap<Key, Integer> entries = readUnlocked(file);
            entries.keySet().removeIf(owned);
            found.forEach((key, count) -> {
                if (count > 0) {
                    entries.put(key, count);
                }
            });
            try {
                if (entries.isEmpty()) {
                    Files.deleteIfExists(file);
                } else {
                    Files.createDirectories(file.toAbsolutePath().getParent());
                    Files.writeString(file, render(entries), StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /** Counts distinct offending lines per baseline key. */
    public static SortedMap<Key, Integer> count(Collection<Occurrence> occurrences) {
        Map<Key, java.util.Set<Integer>> lines = new TreeMap<>();
        for (Occurrence o : occurrences) {
            lines.computeIfAbsent(o.key(), k -> new java.util.TreeSet<>()).add(o.line());
        }
        SortedMap<Key, Integer> counts = new TreeMap<>();
        lines.forEach((k, v) -> counts.put(k, v.size()));
        return counts;
    }

    /** Returns the occurrences in files that have more offending lines than the baseline allows. */
    public static List<Occurrence> violations(Collection<Occurrence> occurrences, Map<Key, Integer> baseline) {
        SortedMap<Key, Integer> counts = count(occurrences);
        List<Occurrence> violations = new ArrayList<>();
        for (Occurrence o : occurrences) {
            if (counts.get(o.key()) > baseline.getOrDefault(o.key(), 0)) {
                violations.add(o);
            }
        }
        violations.sort((a, b) -> {
            int c = a.key().compareTo(b.key());
            return c != 0 ? c : Integer.compare(a.line(), b.line());
        });
        return Collections.unmodifiableList(violations);
    }

    private static SortedMap<Key, Integer> readUnlocked(Path file) {
        try {
            return Files.exists(file) ? parse(Files.readString(file, StandardCharsets.UTF_8)) : new TreeMap<>();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Object lock(Path file) {
        return LOCKS.computeIfAbsent(file.toAbsolutePath().normalize(), p -> new Object());
    }
}
