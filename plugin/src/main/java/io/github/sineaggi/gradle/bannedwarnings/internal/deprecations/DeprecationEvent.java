package io.github.sineaggi.gradle.bannedwarnings.internal.deprecations;

import java.io.Serializable;

/**
 * One Gradle deprecation reported while the build ran.
 *
 * @param name the deprecation's problem name, stable across occurrences, such as
 *     {@code The 'val name by registering' property delegate syntax}
 * @param label the full message Gradle printed
 * @param source where it came from: {@code plugin <id>}, {@code script <path>}, {@code class <name>} or
 *     {@code unknown}
 * @param frame the first stack frame outside Gradle, or {@code ""}; distinguishes occurrences
 */
public record DeprecationEvent(String name, String label, String source, String frame) implements Serializable {
}
