package io.github.sineaggi.gradle.bannedwarnings.internal.deprecations;

import io.github.sineaggi.gradle.bannedwarnings.internal.CheckSettings;
import org.gradle.api.flow.BuildWorkResult;
import org.gradle.api.flow.FlowAction;
import org.gradle.api.flow.FlowParameters;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

/** Runs when the build finishes: checks the deprecations reported while tasks ran, and stops recording. */
public abstract class CheckDeprecationsAction implements FlowAction<CheckDeprecationsAction.Params> {

    public interface Params extends FlowParameters {
        /** Loaded with the configuration cache entry, which starts recording on a cache hit. */
        @Input
        Property<StartListeningOnLoad> getListener();

        @Input
        Property<CheckSettings> getSettings();

        /** Wired to the build result so that the action runs at the end of the build. */
        @Input
        Property<BuildWorkResult> getResult();
    }

    @Override
    public void execute(Params parameters) {
        String rootDir = parameters.getListener().get().rootDir();
        boolean listening = Deprecations.isListening(rootDir);
        try {
            if (parameters.getResult().get().getFailure().isPresent()) {
                return;
            }
            if (!listening) {
                Logging.getLogger(CheckDeprecationsAction.class).warn(
                        "banned-warnings: Gradle deprecations from task actions were not checked in this build: on this configuration"
                                + " cache hit, the plugin couldn't find Gradle's build operation listener manager. This Gradle version"
                                + " may have changed the internals the plugin relies on.");
                return;
            }
            DeprecationCheck.check("task execution", Deprecations.drain(rootDir), parameters.getSettings().get(), false);
        } finally {
            Deprecations.stopListening(rootDir);
        }
    }
}
