# Gradle Banned Warnings plugin

A settings plugin that fails the build when a warning you have banned shows up, so a warning stays gone once it
has been fixed. Existing occurrences can be recorded in a baseline: they keep building, but any new one fails.

It checks three places:

| Where | How |
|---|---|
| Kotlin compile tasks (`compileKotlin`, precompiled script plugins in `build-logic`/`buildSrc`, ...) | Reads the compiler's warnings from the task output, matched by Kotlin diagnostic name and/or message |
| Java compile tasks | Reads javac's warnings, matched by `-Xlint` category and/or message |
| `*.gradle.kts` build, settings and init scripts, and scripts they apply with `apply(from = ...)` | Scans the script source, because Gradle compiles scripts itself and plugins can't see that compiler's output |
| Gradle's own deprecation warnings, from scripts, plugins and task actions | Listens to Gradle's internal build operation events (see [Gradle deprecations](#gradle-deprecations)) |

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

## Gradle deprecations

```kotlin
bannedWarnings {
    gradleDeprecations()
}
```

bans every deprecation Gradle reports, whoever triggers it: a build script, a plugin you apply, or a task action.
That covers deprecations that `--warning-mode=fail` would catch, but with a baseline, so you can adopt it before
the last deprecation is gone. Each one is attributed to a source:

- `plugin <id>`: inside the application of a (non-Gradle) plugin, including precompiled script plugins
- `script <path>`: in a build, settings, init or applied script
- `class <name>`: in a task action, named by the first class outside Gradle that made the call

Configuration-time deprecations fail the build before any task runs. Execution-time ones fail it when the build
finishes. `gradleDeprecations { gradleDeprecation = "regex" }` narrows the rule to matching deprecations.

This relies on Gradle internals: the build operation events Gradle keeps stable for Develocity
(`org.gradle.operations.*`), plus Gradle's build operation listener manager. Plugins can't reach that manager
from a build service. On a configuration cache hit, where the settings plugin doesn't run, the plugin reuses the
manager the daemon saw when it last configured the build. In a fresh daemon, it finds the manager by reflection
through Gradle's static deprecation logger, which Gradle hands the current build's event emitter. If a Gradle
version changes those private fields, the plugin prints a warning that task-action deprecations were not
checked, rather than failing. Configuration-time deprecations are unaffected, since a cache hit doesn't run
configuration.

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
# <rule>	<file relative to the build root, or deprecation source>	<count>
kotlin-dsl-delegated-properties	build.gradle.kts	3
gradle-deprecations	plugin com.example.legacy: Invocation of Task.project at execution time	1
```

For Gradle deprecations, the update records what the build you run reports, so run it with the tasks your CI
runs.

## Limitations

- Compiler warnings are only printed when a file is compiled. Up-to-date and build cache hits are not
  re-checked, but a task never caches output from a run that failed, and editing a file recompiles it.
- With `--quiet`, Gradle hides compiler warnings, so compile tasks are not checked (the plugin says so).
- The build script scan matches source text, so a rule there needs its own `buildScriptPattern`. String literals
  are scanned; comments are not.
- `apply(from = ...)` is followed only when the path is a string literal, optionally inside `file(...)`,
  `rootProject.file(...)`, `rootDir.resolve(...)` or `layout.projectDirectory/settingsDirectory.file(...)`, using at
  most `$rootDir` or `$projectDir`. Computed paths, URLs and Groovy `.gradle` scripts are skipped.
- Init scripts outside the build are recorded in the baseline as `~/...` paths, so a baseline entry for one only
  fits machines with the same init script.
