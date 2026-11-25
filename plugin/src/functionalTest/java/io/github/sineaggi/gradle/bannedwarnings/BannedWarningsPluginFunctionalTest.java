package io.github.sineaggi.gradle.bannedwarnings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Every build runs with the configuration cache and isolated projects enabled. */
class BannedWarningsPluginFunctionalTest {

    private static final String KOTLIN_VERSION = System.getProperty("bannedWarnings.test.kotlinVersion", "2.4.21");
    private static final String MIRROR = System.getProperty("bannedWarnings.test.mavenMirror");
    private static final String PLUGIN_REPO = System.getProperty("bannedWarnings.test.pluginRepo");
    private static final String PLUGIN_VERSION = System.getProperty("bannedWarnings.test.pluginVersion");

    @TempDir
    Path projectDir;

    private static String repositories() {
        return (MIRROR != null ? "maven(\"" + MIRROR + "\")\n" : "") + "mavenCentral()\n";
    }

    private static String pluginRepositories() {
        return "maven(\"" + PLUGIN_REPO.replace("\\", "/") + "\")\n" + repositories();
    }

    private void settings(String bannedWarnings, String extra) throws IOException {
        write("settings.gradle.kts", """
                pluginManagement {
                    repositories {
                        %s
                        gradlePluginPortal()
                    }
                }
                plugins {
                    id("io.github.sineaggi.banned-warnings") version "%s"
                }
                dependencyResolutionManagement {
                    repositories {
                        %s
                    }
                }
                rootProject.name = "sample"
                bannedWarnings {
                %s
                }
                %s
                """.formatted(pluginRepositories(), PLUGIN_VERSION, repositories(), bannedWarnings, extra));
    }

    private void kotlinProject(String dir) throws IOException {
        write(dir + "build.gradle.kts", """
                plugins {
                    kotlin("jvm") version "%s"
                }
                """.formatted(KOTLIN_VERSION));
    }

    private void write(String path, String content) throws IOException {
        Path file = projectDir.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private GradleRunner runner(String... args) {
        List<String> arguments = new ArrayList<>(List.of(args));
        arguments.add("--configuration-cache");
        arguments.add("-Dorg.gradle.unsafe.isolated-projects=true");
        arguments.add("--stacktrace");
        // The plugin itself must not use anything Gradle has deprecated.
        if (arguments.stream().noneMatch(a -> a.startsWith("--warning-mode"))) {
            arguments.add("--warning-mode=fail");
        }
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .forwardOutput()
                .withArguments(arguments);
    }

    @Test
    void failsOnDeprecatedDelegateInBuildScript() throws IOException {
        settings("kotlinDslDelegatedProperties()", "");
        write("build.gradle.kts", """
                val ok = tasks.register("ok")
                val foo by tasks.registering {
                    doLast { println("foo") }
                }
                """);

        BuildResult result = runner("help").buildAndFail();

        assertTrue(result.getOutput().contains("Banned warnings found in build script of root project 'sample'"), result.getOutput());
        assertTrue(result.getOutput().contains("[kotlin-dsl-delegated-properties] build.gradle.kts:2: val foo by tasks.registering {"));
        assertTrue(result.getOutput().contains("Use 'val foo = tasks.register(\"foo\") { }'"));
    }

    @Test
    void failsOnDeprecatedDelegateInSubprojectAndSettings() throws IOException {
        settings("kotlinDslDelegatedProperties()", "include(\"sub\")\nval answer: String by settings\n");
        write("gradle.properties", "answer=42\n");
        write("sub/build.gradle.kts", "val myConf by configurations.creating\n");

        BuildResult result = runner("help").buildAndFail();
        assertTrue(result.getOutput().contains("Banned warnings found in settings script"), result.getOutput());

        settings("kotlinDslDelegatedProperties()", "include(\"sub\")\n");
        result = runner("help").buildAndFail();
        assertTrue(result.getOutput().contains("[kotlin-dsl-delegated-properties] sub/build.gradle.kts:1: val myConf by configurations.creating"), result.getOutput());
    }

    @Test
    void cleanBuildPassesAndReusesConfigurationCache() throws IOException {
        settings("kotlinDslDelegatedProperties()", "include(\"sub\")\n");
        write("build.gradle.kts", "val foo = tasks.register(\"foo\")\n");
        write("sub/build.gradle.kts", "val bar = tasks.register(\"bar\")\n");

        BuildResult first = runner("foo", ":sub:bar").build();
        BuildResult second = runner("foo", ":sub:bar").build();

        assertTrue(first.getOutput().contains("Configuration cache entry stored."), first.getOutput());
        assertTrue(second.getOutput().contains("Configuration cache entry reused."), second.getOutput());
    }

    @Test
    void baselineAllowsExistingButNotNewOccurrences() throws IOException {
        settings("kotlinDslDelegatedProperties()", "");
        write("build.gradle.kts", "val foo by tasks.registering\n");

        // The baselined delegate still makes Gradle print its own deprecation warning.
        runner("help", "-PbannedWarnings.updateBaseline", "--warning-mode=all").build();
        String baseline = Files.readString(projectDir.resolve("banned-warnings-baseline.txt"));
        assertTrue(baseline.contains("kotlin-dsl-delegated-properties\tbuild.gradle.kts\t1"), baseline);

        runner("help", "--warning-mode=all").build();

        write("build.gradle.kts", "val foo by tasks.registering\nval bar by tasks.registering\n");
        BuildResult result = runner("help").buildAndFail();
        assertTrue(result.getOutput().contains("2 lines break [kotlin-dsl-delegated-properties], the baseline allows 1"), result.getOutput());

        // Fixing everything and updating removes the baseline file.
        write("build.gradle.kts", "val foo = tasks.register(\"foo\")\n");
        runner("help", "-PbannedWarnings.updateBaseline").build();
        assertFalse(Files.exists(projectDir.resolve("banned-warnings-baseline.txt")));
    }

    @Test
    void failsOnBannedKotlinCompilerWarning() throws IOException {
        settings("""
                ban("old-api") {
                    kotlinDiagnostic = "DEPRECATION"
                    message = "'fun old\\\\(\\\\)"
                    reason = "Call new() instead."
                }
                """, "");
        kotlinProject("");
        write("src/main/kotlin/Old.kt", """
                @Deprecated("use new") fun old() = 1
                fun new() = 2
                """);
        write("src/main/kotlin/Use.kt", "fun use() = new()\n");

        BuildResult ok = runner("compileKotlin").build();
        assertEquals(TaskOutcome.SUCCESS, ok.task(":compileKotlin").getOutcome());

        write("src/main/kotlin/Use.kt", "fun use() = new()\nfun use2() = old()\n");
        BuildResult result = runner("compileKotlin").buildAndFail();
        assertTrue(result.getOutput().contains("Banned warnings found in :compileKotlin"), result.getOutput());
        assertTrue(result.getOutput().contains("[old-api] src/main/kotlin/Use.kt:2: 'fun old(): Int' is deprecated. use new."), result.getOutput());
        assertTrue(result.getOutput().contains("Call new() instead."));
    }

    @Test
    void kotlinBaselineAllowsExistingButNotNewOccurrences() throws IOException {
        settings("""
                ban("old-api") {
                    kotlinDiagnostic = "DEPRECATION"
                }
                """, "include(\"lib\")\n");
        kotlinProject("lib/");
        write("lib/src/main/kotlin/Old.kt", "@Deprecated(\"x\") fun old() = 1\n");
        write("lib/src/main/kotlin/A.kt", "fun a() = old()\n");
        write("lib/src/main/kotlin/B.kt", "fun b() = 1\n");

        runner(":lib:compileKotlin", "-PbannedWarnings.updateBaseline").build();
        String baseline = Files.readString(projectDir.resolve("banned-warnings-baseline.txt"));
        assertTrue(baseline.contains("old-api\tlib/src/main/kotlin/A.kt\t1"), baseline);

        BuildResult ok = runner(":lib:compileKotlin").build();
        assertNotNull(ok.task(":lib:compileKotlin"));

        write("lib/src/main/kotlin/B.kt", "fun b() = old()\n");
        BuildResult result = runner(":lib:compileKotlin").buildAndFail();
        assertTrue(result.getOutput().contains("[old-api] lib/src/main/kotlin/B.kt:1:"), result.getOutput());
        assertFalse(result.getOutput().contains("[old-api] lib/src/main/kotlin/A.kt"), result.getOutput());
    }

    @Test
    void failsInCompilerWithWarningLevel() throws IOException {
        settings("""
                ban("deprecation") {
                    kotlinDiagnostic = "DEPRECATION"
                    failInCompiler = true
                }
                """, "");
        kotlinProject("");
        write("src/main/kotlin/A.kt", "@Deprecated(\"x\") fun old() = 1\nfun a() = old()\n");

        BuildResult result = runner("compileKotlin").buildAndFail();
        assertTrue(result.getOutput().contains("e: file://"), result.getOutput());
        assertTrue(result.getOutput().contains("is deprecated"), result.getOutput());
    }

    @Test
    void failsOnBannedJavacLint() throws IOException {
        settings("""
                ban("java-deprecation") {
                    javacLint = "deprecation"
                }
                """, "");
        write("build.gradle.kts", "plugins { java }\n");
        write("src/main/java/Old.java", "public class Old { @Deprecated public static void old() {} }\n");
        write("src/main/java/Use.java", "class Use { void u() { Old.old(); } }\n");

        BuildResult result = runner("compileJava").buildAndFail();
        assertTrue(result.getOutput().contains("[java-deprecation] src/main/java/Use.java:1: old() in Old has been deprecated"), result.getOutput());
    }

    @Test
    void catchesDelegatesInPrecompiledScriptPlugins() throws IOException {
        settings("kotlinDslDelegatedProperties()", "includeBuild(\"build-logic\")\n");
        write("build-logic/settings.gradle.kts", """
                pluginManagement {
                    repositories {
                        %s
                        gradlePluginPortal()
                    }
                }
                plugins {
                    id("io.github.sineaggi.banned-warnings") version "%s"
                }
                dependencyResolutionManagement {
                    repositories {
                        %s
                        gradlePluginPortal()
                    }
                }
                bannedWarnings {
                    kotlinDslDelegatedProperties()
                }
                """.formatted(pluginRepositories(), PLUGIN_VERSION, repositories()));
        write("build-logic/build.gradle.kts", "plugins { `kotlin-dsl` }\n");
        write("build-logic/src/main/kotlin/my-convention.gradle.kts", "val foo by tasks.registering\n");
        write("build.gradle.kts", "plugins { id(\"my-convention\") }\n");

        BuildResult result = runner("help").buildAndFail();
        assertTrue(result.getOutput().contains("Execution failed for task ':build-logic:compileKotlin'"), result.getOutput());
        // Paths are relative to the root of the build that applies the plugin, here build-logic.
        assertTrue(result.getOutput().contains("[kotlin-dsl-delegated-properties] src/main/kotlin/my-convention.gradle.kts:1: 'val <T : Any, C : NamedDomainObjectContainer<T>> C.registering"), result.getOutput());
    }

    @Test
    void scansScriptsAppliedWithApplyFrom() throws IOException {
        settings("kotlinDslDelegatedProperties()", "include(\"sub\")\n");
        write("sub/build.gradle.kts", "apply(from = \"$rootDir/gradle/shared.gradle.kts\")\n");
        write("gradle/shared.gradle.kts", "apply(from = isolated.rootProject.projectDirectory.file(\"gradle/nested.gradle.kts\"))\n");
        write("gradle/nested.gradle.kts", "\nval foo by tasks.registering\n");

        BuildResult result = runner("help").buildAndFail();

        assertTrue(result.getOutput().contains("Banned warnings found in build script of project ':sub'"), result.getOutput());
        assertTrue(result.getOutput().contains("[kotlin-dsl-delegated-properties] gradle/nested.gradle.kts:2: val foo by tasks.registering"), result.getOutput());

        // Baselined like any other script (Gradle itself still warns about the delegate once it runs).
        runner("help", "-PbannedWarnings.updateBaseline", "--warning-mode=all").build();
        String baseline = Files.readString(projectDir.resolve("banned-warnings-baseline.txt"));
        assertTrue(baseline.contains("kotlin-dsl-delegated-properties\tgradle/nested.gradle.kts\t1"), baseline);
    }

    @Test
    void scansInitScripts() throws IOException {
        settings("kotlinDslDelegatedProperties()", "");
        // Never called, so only the plugin's scan sees it, not Gradle's own deprecation warning.
        write("init.gradle.kts", """
                fun unused(project: Project) {
                    val foo by project.tasks.registering
                }
                """);

        BuildResult result = runner("help", "--init-script", "init.gradle.kts").buildAndFail();

        assertTrue(result.getOutput().contains("Banned warnings found in init script init.gradle.kts"), result.getOutput());
        assertTrue(result.getOutput().contains("[kotlin-dsl-delegated-properties] init.gradle.kts:2: val foo by project.tasks.registering"), result.getOutput());
    }

    /** An included build with a precompiled script plugin and a Java-style plugin that use deprecated Gradle APIs. */
    private void deprecatedPlugins() throws IOException {
        write("plugins/settings.gradle.kts", """
                pluginManagement {
                    repositories {
                        %s
                        gradlePluginPortal()
                    }
                }
                dependencyResolutionManagement {
                    repositories {
                        %s
                        gradlePluginPortal()
                    }
                }
                """.formatted(repositories(), repositories()));
        write("plugins/build.gradle.kts", """
                plugins { `kotlin-dsl` }
                gradlePlugin {
                    plugins {
                        register("late") {
                            id = "my.late-deprecation"
                            implementationClass = "LateDeprecationPlugin"
                        }
                    }
                }
                """);
        // Deprecated at configuration time, inside the plugin.
        write("plugins/src/main/kotlin/my-convention.gradle.kts", "val foo by tasks.registering\n");
        // Deprecated at execution time, inside a task action.
        write("plugins/src/main/kotlin/LateDeprecationPlugin.kt", """
                import org.gradle.api.Plugin
                import org.gradle.api.Project

                class LateDeprecationPlugin : Plugin<Project> {
                    override fun apply(project: Project) {
                        project.tasks.register("late") {
                            // Stands in for a plugin calling a deprecated Gradle API from a task action.
                            doLast {
                                org.gradle.internal.deprecation.DeprecationLogger.deprecate("Calling the late API")
                                    .willBeRemovedInGradle11().undocumented().nagUser()
                            }
                        }
                    }
                }
                """);
    }

    @Test
    void failsOnGradleDeprecationFromBuildScript() throws IOException {
        settings("gradleDeprecations()", "");
        write("build.gradle.kts", "val foo by tasks.registering\n");

        BuildResult result = runner("help", "--warning-mode=all").buildAndFail();

        assertTrue(result.getOutput().contains("Banned Gradle deprecations reported during configuration"), result.getOutput());
        assertTrue(result.getOutput().contains("[gradle-deprecations] script build.gradle.kts: The 'val name by registering' property delegate syntax"), result.getOutput());
        // A configuration cache entry can't hide it: the next build fails the same way.
        BuildResult again = runner("help", "--warning-mode=all").buildAndFail();
        assertTrue(again.getOutput().contains("[gradle-deprecations] script build.gradle.kts: The 'val name by registering' property delegate syntax"), again.getOutput());
    }

    @Test
    void failsOnGradleDeprecationFromPlugin() throws IOException {
        settings("gradleDeprecations()", "includeBuild(\"plugins\")\n");
        deprecatedPlugins();
        write("build.gradle.kts", "plugins { id(\"my-convention\") }\n");

        BuildResult result = runner("help", "--warning-mode=all").buildAndFail();

        assertTrue(result.getOutput().contains("[gradle-deprecations] plugin my-convention: The 'val name by registering' property delegate syntax"), result.getOutput());
    }

    @Test
    void failsOnGradleDeprecationInTaskActionEvenOnConfigurationCacheHit() throws IOException {
        settings("gradleDeprecations()", "includeBuild(\"plugins\")\n");
        deprecatedPlugins();
        write("build.gradle.kts", "plugins { id(\"my.late-deprecation\") }\n");

        BuildResult first = runner("late", "--warning-mode=all").buildAndFail();
        assertTrue(first.getOutput().contains("Banned Gradle deprecations reported during task execution"), first.getOutput());
        assertTrue(first.getOutput().contains("[gradle-deprecations] class LateDeprecationPlugin: Calling the late API"), first.getOutput());

        BuildResult second = runner("late", "--warning-mode=all").buildAndFail();
        assertTrue(second.getOutput().contains("Configuration cache entry reused."), second.getOutput());
        assertTrue(second.getOutput().contains("[gradle-deprecations] class LateDeprecationPlugin: Calling the late API"), second.getOutput());
    }

    @Test
    void failsOnGradleDeprecationInTaskActionOnConfigurationCacheHitInFreshDaemon() throws IOException {
        settings("gradleDeprecations()", "includeBuild(\"plugins\")\n");
        deprecatedPlugins();
        write("build.gradle.kts", "plugins { id(\"my.late-deprecation\") }\n");
        runner("late", "--warning-mode=all").buildAndFail();

        // Daemon JVM arguments no running daemon has, so Gradle starts a new daemon, which has never configured this build.
        write("gradle.properties", "org.gradle.jvmargs=-Xmx512m -Dbanned-warnings.test.daemon=" + UUID.randomUUID() + "\n");
        BuildResult result = runner("late", "--warning-mode=all").buildAndFail();
        assertTrue(result.getOutput().contains("Configuration cache entry reused."), result.getOutput());
        assertTrue(result.getOutput().contains("[gradle-deprecations] class LateDeprecationPlugin: Calling the late API"), result.getOutput());
        assertFalse(result.getOutput().contains("were not checked"), result.getOutput());
    }

    @Test
    void gradleDeprecationBaseline() throws IOException {
        settings("gradleDeprecations()", "includeBuild(\"plugins\")\n");
        deprecatedPlugins();
        write("build.gradle.kts", "plugins { id(\"my.late-deprecation\") }\nval foo by tasks.registering\n");

        runner("late", "-PbannedWarnings.updateBaseline", "--warning-mode=all").build();
        String baseline = Files.readString(projectDir.resolve("banned-warnings-baseline.txt"));
        assertTrue(baseline.contains("gradle-deprecations\tscript build.gradle.kts: The 'val name by registering' property delegate syntax\t1"), baseline);
        assertTrue(baseline.contains("gradle-deprecations\tclass LateDeprecationPlugin: Calling the late API\t1"), baseline);

        runner("late", "--warning-mode=all").build();
        BuildResult reused = runner("late", "--warning-mode=all").build();
        assertTrue(reused.getOutput().contains("Configuration cache entry reused."), reused.getOutput());

        // A new deprecation in the same script fails.
        write("build.gradle.kts", "plugins { id(\"my.late-deprecation\") }\nval foo by tasks.registering\nval bar by tasks.creating\n");
        BuildResult result = runner("late", "--warning-mode=all").buildAndFail();
        assertTrue(result.getOutput().contains("[gradle-deprecations] script build.gradle.kts: The 'val name by creating' property delegate syntax"), result.getOutput());
    }
}
