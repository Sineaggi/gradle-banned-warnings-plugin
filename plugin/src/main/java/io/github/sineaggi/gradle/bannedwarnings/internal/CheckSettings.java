package io.github.sineaggi.gradle.bannedwarnings.internal;

import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything a project needs to apply the rules; captured once the settings are evaluated. */
public record CheckSettings(List<RuleSpec> rules, String rootDir, String userHome, String baselineFile, boolean updateBaseline)
        implements Serializable {

    public Map<String, RuleSpec> rulesById() {
        Map<String, RuleSpec> byId = new LinkedHashMap<>();
        rules.forEach(r -> byId.put(r.id(), r));
        return byId;
    }

    public Path baselinePath() {
        return Paths.get(baselineFile);
    }

    /**
     * The path of a file relative to the build root, with {@code /} separators. Files outside the build but in
     * the user's home directory, such as init scripts, start with {@code ~/}.
     */
    public String relativize(String path) {
        if (path == null) {
            return "<unknown>";
        }
        Path root = Paths.get(rootDir).toAbsolutePath().normalize();
        Path file = Paths.get(path);
        String prefix = "";
        if (file.isAbsolute()) {
            file = file.normalize();
            Path home = userHome == null ? null : Paths.get(userHome).toAbsolutePath().normalize();
            if (file.startsWith(root)) {
                file = root.relativize(file);
            } else if (home != null && file.startsWith(home)) {
                file = home.relativize(file);
                prefix = "~/";
            }
        }
        return prefix + file.toString().replace('\\', '/');
    }

    /** A stable string of the rules, used as a task input. */
    public String fingerprint() {
        return rules.toString();
    }
}
