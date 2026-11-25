package io.github.sineaggi.gradle.bannedwarnings.internal;

/**
 * One line of source that broke a rule.
 *
 * @param ruleId the rule
 * @param path path relative to the root of the build, with {@code /} separators, or {@code <unknown>}
 * @param line 1-based line, or 0 when unknown
 * @param message what was found
 */
public record Occurrence(String ruleId, String path, int line, String message) {

    public Baseline.Key key() {
        return new Baseline.Key(ruleId, path);
    }
}
