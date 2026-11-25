package io.github.sineaggi.gradle.bannedwarnings.internal;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.gradle.api.GradleException;
import org.gradle.api.IsolatedAction;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.compile.JavaCompile;

/** Applies the rules to one project: scans its build script and wires its compile tasks. */
public final class ConfigureProject implements IsolatedAction<Project> {

    private final CheckSettings settings;

    public ConfigureProject(CheckSettings settings) {
        this.settings = settings;
    }

    @Override
    public void execute(Project project) {
        File buildFile = project.getBuildFile();
        if (buildFile.getName().endsWith(".kts")) {
            checkScript(project.getProviders(), settings, buildFile, "build script of " + project.getDisplayName());
        }
        boolean hasCompilerRules = settings.rules().stream().anyMatch(RuleSpec::hasCompilerCriteria);
        if (!hasCompilerRules) {
            return;
        }
        Provider<CaptureService> service = project.getGradle().getSharedServices()
                .registerIfAbsent(CaptureService.NAME, CaptureService.class);
        Provider<String> baselineForProject = baselineForProject(project);
        project.getTasks().configureEach(task -> {
            CompilerKind kind = CompilerKind.of(task);
            if (kind != null) {
                configureCompileTask(task, kind, service, baselineForProject);
            }
        });
    }

    /** Fails if a Kotlin DSL script has more banned lines than the baseline allows (or records them). */
    public static void checkScript(ProviderFactory providers, CheckSettings settings, File script, String description) {
        if (settings.rules().stream().noneMatch(r -> r.buildScriptPattern() != null)) {
            return;
        }
        String text = providers.fileContents(() -> script).getAsText().getOrNull();
        if (text == null) {
            return;
        }
        String relativePath = settings.relativize(script.getAbsolutePath());
        List<Occurrence> occurrences = ScriptScanner.scan(text, relativePath, settings.rules());
        if (settings.updateBaseline()) {
            Baseline.update(settings.baselinePath(), key -> key.path().equals(relativePath), Baseline.count(occurrences));
            return;
        }
        if (occurrences.isEmpty()) {
            return;
        }
        File baselineFile = new File(settings.baselineFile());
        Map<Baseline.Key, Integer> baseline = Baseline.parse(providers.fileContents(() -> baselineFile).getAsText().getOrNull());
        List<Occurrence> violations = Baseline.violations(occurrences, baseline);
        if (!violations.isEmpty()) {
            throw new GradleException(Reports.failure(description, violations, baseline, settings.rulesById(),
                    settings.relativize(settings.baselineFile())));
        }
    }

    private void configureCompileTask(Task task, CompilerKind kind, Provider<CaptureService> service, Provider<String> baseline) {
        List<RuleSpec> rules = settings.rules().stream().filter(RuleSpec::hasCompilerCriteria).toList();
        if (kind == CompilerKind.JAVA) {
            JavaCompile javaCompile = (JavaCompile) task;
            rules.stream().map(RuleSpec::javacLint).filter(l -> l != null).distinct()
                    .forEach(lint -> javaCompile.getOptions().getCompilerArgs().add("-Xlint:" + lint));
            if (settings.updateBaseline()) {
                javaCompile.getOptions().setIncremental(false);
            }
        } else {
            List<String> args = new ArrayList<>();
            if (rules.stream().anyMatch(r -> r.kotlinDiagnostic() != null)) {
                args.add("-Xrender-internal-diagnostic-names");
            }
            rules.stream().filter(RuleSpec::failInCompiler).map(RuleSpec::kotlinDiagnostic).distinct()
                    .forEach(d -> args.add("-Xwarning-level=" + d + ":error"));
            if (!args.isEmpty()) {
                kotlinFreeCompilerArgs(task).addAll(args);
            }
            if (settings.updateBaseline()) {
                invoke(task, "setIncremental", new Class<?>[]{boolean.class}, false);
            }
        }
        if (rules.stream().noneMatch(RuleSpec::checksCompilerOutput)) {
            return;
        }
        task.getInputs().property("bannedWarnings.rules", settings.fingerprint());
        if (settings.updateBaseline()) {
            task.getOutputs().upToDateWhen(t -> false);
            task.getOutputs().doNotCacheIf("the banned-warnings baseline is being updated", t -> true);
        } else {
            task.getInputs().property("bannedWarnings.baseline", baseline);
        }
        task.usesService(service);
        task.doFirst(new StartCapture(service));
        task.doLast(new FinishCapture(service, settings, kind, task.getInputs().getSourceFiles()));
    }

    /** The baseline lines for files in this project, so baseline edits elsewhere don't invalidate its tasks. */
    private Provider<String> baselineForProject(Project project) {
        String prefix = settings.relativize(project.getProjectDir().getAbsolutePath());
        String pathPrefix = prefix.isEmpty() || prefix.equals(".") ? "" : prefix + "/";
        File baselineFile = new File(settings.baselineFile());
        return project.getProviders().fileContents(() -> baselineFile).getAsText()
                .map(text -> {
                    List<String> lines = new ArrayList<>();
                    Baseline.parse(text).forEach((key, count) -> {
                        if (key.path().startsWith(pathPrefix)) {
                            lines.add(key.ruleId() + "\t" + key.path() + "\t" + count);
                        }
                    });
                    return String.join("\n", lines);
                })
                .orElse("");
    }

    @SuppressWarnings("unchecked")
    private static ListProperty<String> kotlinFreeCompilerArgs(Task task) {
        Object options = invoke(task, "getCompilerOptions", new Class<?>[0]);
        return (ListProperty<String>) invoke(options, "getFreeCompilerArgs", new Class<?>[0]);
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) {
        Method method = findPublicMethod(target.getClass(), name, types);
        if (method == null) {
            throw new GradleException("banned-warnings: unsupported Kotlin Gradle plugin version, " + target.getClass().getName() + " has no " + name + "()");
        }
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new GradleException("banned-warnings: could not call " + name + " on " + target.getClass().getName(), e);
        }
    }

    /** Finds a method declared by a public class or interface, so it can be called without setAccessible. */
    private static Method findPublicMethod(Class<?> type, String name, Class<?>[] types) {
        List<Class<?>> queue = new ArrayList<>(Collections.singletonList(type));
        for (int i = 0; i < queue.size(); i++) {
            Class<?> c = queue.get(i);
            if (java.lang.reflect.Modifier.isPublic(c.getModifiers())) {
                try {
                    return c.getMethod(name, types);
                } catch (NoSuchMethodException ignored) {
                    // keep looking
                }
            }
            if (c.getSuperclass() != null) {
                queue.add(c.getSuperclass());
            }
            queue.addAll(List.of(c.getInterfaces()));
        }
        return null;
    }
}
