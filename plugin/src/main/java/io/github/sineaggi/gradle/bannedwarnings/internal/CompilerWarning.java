package io.github.sineaggi.gradle.bannedwarnings.internal;

import java.io.Serializable;

/**
 * One warning parsed from compiler output.
 *
 * @param path absolute path of the source file, or {@code null} when the warning has no location
 * @param line 1-based line, or 0 when unknown
 * @param diagnostic Kotlin diagnostic name or javac lint category, or {@code null}
 * @param message the warning text, without the diagnostic prefix
 */
public record CompilerWarning(String path, int line, String diagnostic, String message) implements Serializable {
}
