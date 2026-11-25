package io.github.sineaggi.gradle.bannedwarnings.internal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/** Builds failure messages. */
public final class Reports {

    private Reports() {
    }

    public static String failure(String where, List<Occurrence> violations, Map<Baseline.Key, Integer> baseline,
                                 Map<String, RuleSpec> rules, String baselinePath) {
        SortedMap<Baseline.Key, Integer> counts = Baseline.count(violations);
        StringBuilder sb = new StringBuilder("Banned warnings found in ").append(where).append(":\n");
        Map<String, Boolean> reasonsShown = new LinkedHashMap<>();
        Baseline.Key current = null;
        for (Occurrence o : violations) {
            if (!o.key().equals(current)) {
                current = o.key();
                int allowed = baseline.getOrDefault(current, 0);
                if (allowed > 0) {
                    sb.append("  ").append(current.path()).append(": ").append(counts.get(current))
                            .append(" lines break [").append(current.ruleId()).append("], the baseline allows ")
                            .append(allowed).append('\n');
                }
            }
            sb.append("  [").append(o.ruleId()).append("] ").append(o.path());
            if (o.line() > 0) {
                sb.append(':').append(o.line());
            }
            sb.append(": ").append(o.message()).append('\n');
            reasonsShown.putIfAbsent(o.ruleId(), true);
        }
        for (String ruleId : reasonsShown.keySet()) {
            RuleSpec rule = rules.get(ruleId);
            if (rule != null && rule.reason() != null) {
                sb.append("\n[").append(ruleId).append("] ").append(rule.reason()).append('\n');
            }
        }
        sb.append("\nTo accept the current occurrences instead, add them to the baseline (")
                .append(baselinePath).append(") by running the build with -PbannedWarnings.updateBaseline");
        return sb.toString();
    }
}
