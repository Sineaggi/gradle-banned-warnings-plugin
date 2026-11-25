package io.github.sineaggi.gradle.bannedwarnings.internal;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;

/** Task action that checks the warnings a compile task printed against the rules and the baseline. */
public final class FinishCapture implements Action<Task> {

    private final Provider<CaptureService> service;
    private final CheckSettings settings;
    private final CompilerKind kind;
    private final FileCollection sources;

    public FinishCapture(Provider<CaptureService> service, CheckSettings settings, CompilerKind kind, FileCollection sources) {
        this.service = service;
        this.settings = settings;
        this.kind = kind;
        this.sources = sources;
    }

    @Override
    public void execute(Task task) {
        String output = service.get().finish(task);
        if (output == null) {
            task.getLogger().quiet("banned-warnings: {} was not checked, because compiler warnings are hidden at this log level", task.getPath());
            return;
        }
        List<Occurrence> occurrences = new ArrayList<>();
        for (CompilerWarning warning : WarningParser.parse(output)) {
            for (RuleSpec rule : settings.rules()) {
                if (rule.matches(warning, kind)) {
                    occurrences.add(new Occurrence(rule.id(), settings.relativize(warning.path()), warning.line(), warning.message()));
                }
            }
        }
        if (settings.updateBaseline()) {
            Set<String> owned = new HashSet<>();
            for (File file : sources.getFiles()) {
                owned.add(settings.relativize(file.getAbsolutePath()));
            }
            Map<Baseline.Key, Integer> counts = Baseline.count(occurrences);
            counts.keySet().forEach(k -> owned.add(k.path()));
            Baseline.update(settings.baselinePath(), key -> owned.contains(key.path()), counts);
            if (!counts.isEmpty()) {
                task.getLogger().lifecycle("banned-warnings: recorded {} file(s) from {} in the baseline", counts.size(), task.getPath());
            }
            return;
        }
        if (occurrences.isEmpty()) {
            return;
        }
        Map<Baseline.Key, Integer> baseline = Baseline.read(settings.baselinePath());
        List<Occurrence> violations = Baseline.violations(occurrences, baseline);
        if (!violations.isEmpty()) {
            throw new GradleException(Reports.failure(task.getPath(), violations, baseline, settings.rulesById(),
                    settings.relativize(settings.baselineFile())));
        }
    }
}
