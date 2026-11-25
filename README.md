# Gradle Banned Warnings plugin

A settings plugin that fails the build when a warning you have banned shows up, so a warning stays gone once it
has been fixed. Existing occurrences can be recorded in a baseline: they keep building, but any new one fails.

It checks three places:

| Where | How |
|---|---|
| Kotlin compile tasks (`compileKotlin`, precompiled script plugins in `build-logic`/`buildSrc`, ...) | Reads the compiler's warnings from the task output, matched by Kotlin diagnostic name and/or message |
| Java compile tasks | Reads javac's warnings, matched by `-Xlint` category and/or message |
| `*.gradle.kts` build and settings scripts | Scans the script source, because Gradle compiles scripts itself and plugins can't see that compiler's output |

Works with the configuration cache and isolated projects. The functional tests run Gradle 9.9.2 with both turned
on and `--warning-mode=fail`, and the repository itself builds with Gradle 9.9.2 with both turned on.

## Usage

`settings.gradle.kts`:

```kotlin
plugins {
    id("io.github.sineaggi.banned-warnings") version "<version>"
}

bannedWarnings {
    // Built-in rule: the Kotlin DSL property delegates deprecated in Gradle 9.6, such as
    // `val foo by tasks.registering`, `by creating`, `by getting`, `by existing`, `by extra` and `val x: String by project`.
    kotlinDslDelegatedProperties()

    // Your own rules.
    ban("old-http-client") {
        kotlinDiagnostic = "DEPRECATION"
        message = "'class LegacyHttpClient'"
        reason = "Use HttpClient2 instead."
    }
    ban("java-removal") {
        javacLint = "removal"
    }
}
```

A rule can set:

| Property | Meaning |
|---|---|
| `message` | Regex searched for in the compiler warning's text |
| `kotlinDiagnostic` | Kotlin diagnostic name, such as `DEPRECATION`. The plugin adds `-Xrender-internal-diagnostic-names` so it can see names |
| `javacLint` | javac lint category, such as `deprecation`. The plugin adds `-Xlint:<category>` |
| `buildScriptPattern` | Regex searched for in each non-comment line of `*.gradle.kts` build and settings scripts |
| `reason` | Shown when the rule is broken |
| `failInCompiler` | With `kotlinDiagnostic`, turn the diagnostic into a compiler error with `-Xwarning-level=NAME:error` (Kotlin 2.2+) instead of reading the output. Ignores `message` and the baseline |

A compiler warning breaks a rule when every compiler criterion the rule sets matches it.

An included build, such as `build-logic`, has its own settings: apply the plugin there too to check its
precompiled script plugins.

## Baseline

```shell
./gradlew build -PbannedWarnings.updateBaseline
```

writes `banned-warnings-baseline.txt` next to the settings script (change it with `bannedWarnings.baseline`).
In this mode nothing fails, and compile tasks run from scratch so every source file is seen. Commit the file. From
then on a file may contain as many offending lines per rule as the baseline lists; more fails the build. Fixing
the last occurrence and updating again removes the file.

```
# <rule>	<file relative to the build root>	<line count>
kotlin-dsl-delegated-properties	build.gradle.kts	3
```

## Limitations

- Compiler warnings are only printed when a file is compiled. Up-to-date and build cache hits are not
  re-checked, but a task never caches output from a run that failed, and editing a file recompiles it.
- With `--quiet`, Gradle hides compiler warnings, so compile tasks are not checked (the plugin says so).
- The build script scan matches source text, so a rule there needs its own `buildScriptPattern`. String literals
  are scanned; comments are not.
- Scripts applied with `apply(from = ...)` are not scanned.
