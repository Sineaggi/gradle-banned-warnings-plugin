package io.github.sineaggi.gradle.bannedwarnings.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RuleSpecTest {

    @Test
    void delegatesPresetMatchesGradleDeprecationWarnings() {
        RuleSpec rule = TestRules.delegates();
        // Messages printed by Gradle 9.7 when compiling scripts that use the deprecated delegates.
        String[] messages = {
                "'fun RegisteringDomainObjectDelegateProviderWithAction<out TaskContainer, Task>.provideDelegate(receiver: Any?, property: KProperty<*>): ExistingDomainObjectDelegate<TaskProvider<Task>>' is deprecated. Use 'val task = tasks.register(name) { }' instead.",
                "'val <T : Any, C : NamedDomainObjectContainer<T>> C.registering: RegisteringDomainObjectDelegateProvider<out C>' is deprecated. Use 'val element = register(name)' instead.",
                "'fun <T> ExtraPropertiesExtension.invoke(initialValue: T): InitialValueExtraPropertyDelegateProvider<T>' is deprecated. Use 'extra.set(name, value)' instead.",
                "'fun Project.provideDelegate(any: Any?, property: KProperty<*>): PropertyDelegate' is deprecated.",
        };
        for (String message : messages) {
            assertTrue(rule.matches(new CompilerWarning("/a.kt", 1, "DEPRECATION", message), CompilerKind.KOTLIN), message);
        }
        assertFalse(rule.matches(new CompilerWarning("/a.kt", 1, "DEPRECATION", "'fun old(): Int' is deprecated."), CompilerKind.KOTLIN));
        assertFalse(rule.matches(new CompilerWarning("/a.kt", 1, null, messages[0]), CompilerKind.KOTLIN));
        assertFalse(rule.matches(new CompilerWarning("/A.java", 1, "deprecation", messages[0]), CompilerKind.JAVA));
    }

    @Test
    void javacLintRule() {
        RuleSpec rule = new RuleSpec("removal", null, null, "removal", null, null, null, false);
        assertTrue(rule.matches(new CompilerWarning("/A.java", 1, "removal", "x has been deprecated and marked for removal"), CompilerKind.JAVA));
        assertFalse(rule.matches(new CompilerWarning("/A.java", 1, "deprecation", "x has been deprecated"), CompilerKind.JAVA));
        assertFalse(rule.matches(new CompilerWarning("/A.kt", 1, "removal", "x"), CompilerKind.KOTLIN));
    }
}
