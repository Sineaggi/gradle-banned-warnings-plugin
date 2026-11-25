package io.github.sineaggi.gradle.bannedwarnings.internal;

import java.io.Serializable;
import java.util.regex.Pattern;

/** Immutable, serializable snapshot of a {@code BannedWarning}. */
public record RuleSpec(
        String id,
        String message,
        String kotlinDiagnostic,
        String javacLint,
        String buildScriptPattern,
        String gradleDeprecation,
        String reason,
        boolean failInCompiler
) implements Serializable {

    public RuleSpec {
        if (message != null) {
            Pattern.compile(message);
        }
        if (buildScriptPattern != null) {
            Pattern.compile(buildScriptPattern);
        }
        if (gradleDeprecation != null) {
            Pattern.compile(gradleDeprecation);
        }
        if (failInCompiler && kotlinDiagnostic == null) {
            throw new IllegalArgumentException("Banned warning '" + id + "' sets failInCompiler but no kotlinDiagnostic");
        }
    }

    public boolean hasCompilerCriteria() {
        return message != null || kotlinDiagnostic != null || javacLint != null;
    }

    /** Whether this rule is checked by reading compiler output (as opposed to failing inside the compiler). */
    public boolean checksCompilerOutput() {
        return hasCompilerCriteria() && !failInCompiler;
    }

    public boolean matches(CompilerWarning warning, CompilerKind kind) {
        if (!checksCompilerOutput()) {
            return false;
        }
        if (kind == CompilerKind.KOTLIN && javacLint != null && kotlinDiagnostic == null) {
            return false;
        }
        if (kind == CompilerKind.JAVA && kotlinDiagnostic != null && javacLint == null) {
            return false;
        }
        String expectedDiagnostic = kind == CompilerKind.KOTLIN ? kotlinDiagnostic : javacLint;
        if (expectedDiagnostic != null && !expectedDiagnostic.equals(warning.diagnostic())) {
            return false;
        }
        return message == null || Pattern.compile(message).matcher(warning.message()).find();
    }
}
