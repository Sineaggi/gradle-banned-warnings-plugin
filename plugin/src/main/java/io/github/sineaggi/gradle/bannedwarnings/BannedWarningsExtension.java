package io.github.sineaggi.gradle.bannedwarnings;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

/** The {@code bannedWarnings} settings extension. */
public abstract class BannedWarningsExtension {

    /** Rule id of {@link #kotlinDslDelegatedProperties()}. */
    public static final String KOTLIN_DSL_DELEGATED_PROPERTIES = "kotlin-dsl-delegated-properties";

    /** The banned warnings. */
    public abstract NamedDomainObjectContainer<BannedWarning> getRules();

    /**
     * Baseline file of accepted existing occurrences. Occurrences listed in it are allowed, anything beyond
     * them fails the build. Defaults to {@code banned-warnings-baseline.txt} next to the settings script;
     * a missing file means an empty baseline.
     */
    public abstract RegularFileProperty getBaseline();

    /**
     * Rewrite the baseline from what this build sees instead of failing. Defaults to {@code true} when the
     * Gradle property {@code bannedWarnings.updateBaseline} is set. Compile tasks are rerun from scratch in
     * this mode so that every source file is compiled.
     */
    public abstract Property<Boolean> getUpdateBaseline();

    /** Adds a banned warning. */
    public void ban(String id, Action<? super BannedWarning> action) {
        getRules().register(id, action);
    }

    /**
     * Bans the Kotlin DSL property delegates deprecated in Gradle 9.6, such as
     * {@code val foo by tasks.registering}, {@code by creating}, {@code by getting}, {@code by existing},
     * {@code by extra} and {@code val x: String by project}.
     *
     * <p>Covers {@code *.gradle.kts} build and settings scripts by source scan, and precompiled script
     * plugins or other Kotlin sources by compiler output.
     */
    public void kotlinDslDelegatedProperties() {
        kotlinDslDelegatedProperties(rule -> { });
    }

    /** Like {@link #kotlinDslDelegatedProperties()}, with further configuration of the rule. */
    public void kotlinDslDelegatedProperties(Action<? super BannedWarning> action) {
        ban(KOTLIN_DSL_DELEGATED_PROPERTIES, rule -> {
            rule.getKotlinDiagnostic().set("DEPRECATION");
            rule.getMessage().set("(?:DelegateProvider|DomainObjectDelegate|PropertyDelegate)\\b.*' is deprecated");
            rule.getBuildScriptPattern().set(
                    "\\bby\\s+(?:[\\w.]+\\.)?(?:registering|creating|getting|existing)\\b"
                            + "|\\bby\\s+(?:[\\w.]+\\.)?extra\\b"
                            + "|:\\s*[\\w.<>?, ]+?\\s+by\\s+(?:project|settings)\\b");
            rule.getReason().set("Kotlin DSL property delegates are deprecated in Gradle 9.6 and removed in Gradle 10."
                    + " Use 'val foo = tasks.register(\"foo\") { }', named(), getByName(), create() or extra.set() instead."
                    + " See https://docs.gradle.org/current/userguide/upgrading_version_9.html#kotlin_dsl_delegated_properties");
            action.execute(rule);
        });
    }
}
