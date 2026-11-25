package io.github.sineaggi.gradle.bannedwarnings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import io.github.sineaggi.gradle.bannedwarnings.internal.CheckSettings;
import io.github.sineaggi.gradle.bannedwarnings.internal.ConfigureProject;
import io.github.sineaggi.gradle.bannedwarnings.internal.RuleSpec;
import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;
import org.gradle.api.provider.ProviderFactory;

/**
 * Settings plugin {@code io.github.sineaggi.banned-warnings}: fails the build when a banned warning appears
 * in a Kotlin or Java compile task, or a banned pattern appears in a Kotlin DSL script, beyond what the
 * baseline allows.
 */
public abstract class BannedWarningsPlugin implements Plugin<Settings> {

    public static final String UPDATE_BASELINE_PROPERTY = "bannedWarnings.updateBaseline";

    @Inject
    protected abstract ProviderFactory getProviders();

    @Override
    public void apply(Settings settings) {
        BannedWarningsExtension extension = settings.getExtensions().create("bannedWarnings", BannedWarningsExtension.class);
        extension.getBaseline().convention(settings.getLayout().getSettingsDirectory().file("banned-warnings-baseline.txt"));
        extension.getUpdateBaseline().convention(getProviders().gradleProperty(UPDATE_BASELINE_PROPERTY)
                .map(value -> !value.equalsIgnoreCase("false"))
                .orElse(false));
        settings.getGradle().settingsEvaluated(evaluated -> configure(evaluated, extension));
    }

    private void configure(Settings settings, BannedWarningsExtension extension) {
        List<RuleSpec> rules = new ArrayList<>();
        extension.getRules().forEach(rule -> rules.add(new RuleSpec(
                rule.getName(),
                rule.getMessage().getOrNull(),
                rule.getKotlinDiagnostic().getOrNull(),
                rule.getJavacLint().getOrNull(),
                rule.getBuildScriptPattern().getOrNull(),
                rule.getReason().getOrNull(),
                rule.getFailInCompiler().getOrElse(false))));
        if (rules.isEmpty()) {
            return;
        }
        CheckSettings check = new CheckSettings(
                List.copyOf(rules),
                settings.getRootDir().getAbsolutePath(),
                getProviders().systemProperty("user.home").getOrNull(),
                extension.getBaseline().get().getAsFile().getAbsolutePath(),
                extension.getUpdateBaseline().get());

        for (File initScript : settings.getGradle().getStartParameter().getAllInitScripts()) {
            if (initScript.getName().endsWith(".kts")) {
                ConfigureProject.checkScript(getProviders(), check, initScript, initScript.getAbsoluteFile().getParentFile(),
                        "init script " + check.relativize(initScript.getAbsolutePath()));
            }
        }
        File settingsScript = settings.getBuildscript().getSourceFile();
        if (settingsScript != null && settingsScript.getName().endsWith(".kts")) {
            ConfigureProject.checkScript(getProviders(), check, settingsScript, settings.getSettingsDir(), "settings script");
        }
        settings.getGradle().getLifecycle().beforeProject(new ConfigureProject(check));
    }
}
