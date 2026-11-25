package io.github.sineaggi.gradle.bannedwarnings;

import org.gradle.api.Named;
import org.gradle.api.provider.Property;

/**
 * A single banned warning.
 *
 * <p>A compiler warning matches this rule when every compiler criterion that is set matches it:
 * {@link #getMessage()}, {@link #getKotlinDiagnostic()} and {@link #getJavacLint()}. A rule with none of
 * them set never matches a compiler warning.
 *
 * <p>{@link #getBuildScriptPattern()} is matched separately against the lines of Kotlin DSL build and
 * settings scripts, because Gradle compiles those scripts itself and gives plugins no way to see the
 * compiler's output.
 */
public interface BannedWarning extends Named {

    /** Regular expression searched for in the warning message (without the {@code [DIAGNOSTIC]} prefix). */
    Property<String> getMessage();

    /**
     * Kotlin diagnostic name, such as {@code DEPRECATION}. The plugin adds
     * {@code -Xrender-internal-diagnostic-names} to Kotlin compile tasks so the name can be matched.
     */
    Property<String> getKotlinDiagnostic();

    /**
     * javac lint category, such as {@code deprecation} or {@code removal}. The plugin adds
     * {@code -Xlint:<category>} to Java compile tasks so javac reports the warning in full.
     */
    Property<String> getJavacLint();

    /** Regular expression searched for in each non-comment line of {@code *.gradle.kts} scripts. */
    Property<String> getBuildScriptPattern();

    /** Explanation shown when the rule is broken, for example how to fix the code. */
    Property<String> getReason();

    /**
     * Turn the Kotlin diagnostic into a compiler error with {@code -Xwarning-level=NAME:error} (Kotlin 2.2+),
     * instead of checking the compiler output. Requires {@link #getKotlinDiagnostic()}, and ignores both
     * {@link #getMessage()} and the baseline, so use it only for diagnostics with no occurrences left.
     * Defaults to {@code false}.
     */
    Property<Boolean> getFailInCompiler();
}
