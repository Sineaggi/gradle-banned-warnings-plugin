package io.github.sineaggi.gradle.bannedwarnings.internal.deprecations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import io.github.sineaggi.gradle.bannedwarnings.internal.Baseline;
import io.github.sineaggi.gradle.bannedwarnings.internal.CheckSettings;
import io.github.sineaggi.gradle.bannedwarnings.internal.Occurrence;
import io.github.sineaggi.gradle.bannedwarnings.internal.Reports;
import io.github.sineaggi.gradle.bannedwarnings.internal.RuleSpec;
import org.gradle.api.GradleException;

/** Compares recorded Gradle deprecations with the rules and the baseline. */
public final class DeprecationCheck {

    private DeprecationCheck() {
    }

    public static List<Occurrence> occurrences(List<DeprecationEvent> events, List<RuleSpec> rules) {
        List<Occurrence> occurrences = new ArrayList<>();
        for (DeprecationEvent event : events) {
            for (RuleSpec rule : rules) {
                if (rule.gradleDeprecation() == null) {
                    continue;
                }
                Pattern pattern = Pattern.compile(rule.gradleDeprecation());
                if (pattern.matcher(event.name()).find() || pattern.matcher(event.label()).find()) {
                    // One baseline entry per deprecation and source; occurrences differ by calling frame.
                    // The (negative, so not printed) line number tells occurrences apart by calling frame.
                    occurrences.add(new Occurrence(rule.id(), event.source() + ": " + event.name(), -1 - (event.frame().hashCode() & 0x7fffffff),
                            event.label() + (event.frame().isEmpty() ? "" : " (at " + event.frame() + ")")));
                }
            }
        }
        return occurrences;
    }

    /**
     * Checks or, in update mode, records the deprecations.
     *
     * @param replaceAll in update mode, whether to drop every Gradle deprecation entry first (at the end of
     *     configuration) or only add what was seen (at the end of execution)
     */
    public static void check(String phase, List<DeprecationEvent> events, CheckSettings settings, boolean replaceAll) {
        List<RuleSpec> rules = settings.rules().stream().filter(r -> r.gradleDeprecation() != null).toList();
        if (rules.isEmpty()) {
            return;
        }
        List<Occurrence> occurrences = occurrences(events, rules);
        if (settings.updateBaseline()) {
            List<String> ruleIds = rules.stream().map(RuleSpec::id).toList();
            Map<Baseline.Key, Integer> counts = Baseline.count(occurrences);
            Baseline.update(settings.baselinePath(),
                    key -> replaceAll ? ruleIds.contains(key.ruleId()) : counts.containsKey(key),
                    replaceAll ? counts : mergeWithExisting(settings, counts));
            return;
        }
        if (occurrences.isEmpty()) {
            return;
        }
        Map<Baseline.Key, Integer> baseline = Baseline.read(settings.baselinePath());
        List<Occurrence> violations = Baseline.violations(occurrences, baseline);
        if (!violations.isEmpty()) {
            throw new GradleException(Reports.failure("Gradle deprecations reported during " + phase, violations, baseline,
                    settings.rulesById(), settings.relativize(settings.baselineFile())).replace(
                    "Banned warnings found in Gradle deprecations reported", "Banned Gradle deprecations reported"));
        }
    }

    /** Execution can repeat what configuration already recorded; keep the larger count. */
    private static Map<Baseline.Key, Integer> mergeWithExisting(CheckSettings settings, Map<Baseline.Key, Integer> counts) {
        Map<Baseline.Key, Integer> existing = Baseline.read(settings.baselinePath());
        Map<Baseline.Key, Integer> merged = new java.util.TreeMap<>(counts);
        merged.replaceAll((k, v) -> Math.max(v, existing.getOrDefault(k, 0)));
        return merged;
    }
}
