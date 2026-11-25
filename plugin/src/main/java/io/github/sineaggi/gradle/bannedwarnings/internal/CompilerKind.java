package io.github.sineaggi.gradle.bannedwarnings.internal;

import org.gradle.api.Task;
import org.gradle.api.tasks.compile.JavaCompile;

/** The compile tasks whose output is checked. */
public enum CompilerKind {
    JAVA,
    KOTLIN;

    private static final String ABSTRACT_KOTLIN_COMPILE = "org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompile";

    /**
     * Detects the compiler of a task. Kotlin tasks are detected by class name, so this plugin does not
     * need the Kotlin Gradle plugin on its classpath.
     */
    public static CompilerKind of(Task task) {
        if (task instanceof JavaCompile) {
            return JAVA;
        }
        for (Class<?> c = task.getClass(); c != null; c = c.getSuperclass()) {
            if (c.getName().equals(ABSTRACT_KOTLIN_COMPILE)) {
                return KOTLIN;
            }
        }
        return null;
    }
}
