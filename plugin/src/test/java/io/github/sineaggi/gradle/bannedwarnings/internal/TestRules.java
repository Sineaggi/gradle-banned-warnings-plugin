package io.github.sineaggi.gradle.bannedwarnings.internal;

import io.github.sineaggi.gradle.bannedwarnings.BannedWarningsExtension;

final class TestRules {
    private TestRules() {
    }

    /** Same values as {@link BannedWarningsExtension#kotlinDslDelegatedProperties()}. */
    static RuleSpec delegates() {
        return new RuleSpec(
                BannedWarningsExtension.KOTLIN_DSL_DELEGATED_PROPERTIES,
                "(?:DelegateProvider|DomainObjectDelegate|PropertyDelegate)\\b.*' is deprecated",
                "DEPRECATION",
                null,
                "\\bby\\s+(?:[\\w.]+\\.)?(?:registering|creating|getting|existing)\\b"
                        + "|\\bby\\s+(?:[\\w.]+\\.)?extra\\b"
                        + "|:\\s*[\\w.<>?, ]+?\\s+by\\s+(?:project|settings)\\b",
                null,
                null,
                false);
    }
}
