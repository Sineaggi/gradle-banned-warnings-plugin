package io.github.sineaggi.gradle.bannedwarnings.internal;

import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses warnings out of the console output of Kotlin and Java compile tasks.
 *
 * <p>Understands the Kotlin Gradle plugin's plain {@code w: file:///A.kt:2:13 msg} lines, its
 * {@code Problem found: ...} blocks (printed with {@code --warning-mode all}), and javac's
 * {@code /A.java:2: warning: [lint] msg} lines.
 */
public final class WarningParser {

    private static final Pattern KOTLIN_LOCATED = Pattern.compile("^w: (?<path>.+?):(?<line>\\d+):(?<col>\\d+):? (?<msg>.*)$");
    private static final Pattern KOTLIN_UNLOCATED = Pattern.compile("^w: (?<msg>.*)$");
    private static final Pattern KOTLIN_DIAGNOSTIC = Pattern.compile("^\\[(?<diag>[A-Z][A-Z0-9_]*)] (?<msg>.*)$");
    private static final Pattern PROBLEM_HEADER = Pattern.compile("^Problem found: .*\\(id: kotlin:compiler:warning:(?<diag>[A-Za-z0-9_]+)\\)\\s*$");
    private static final Pattern PROBLEM_LOCATION = Pattern.compile("^\\s+Location: (?<path>.+?) line (?<line>\\d+)\\s*$");
    private static final Pattern JAVAC = Pattern.compile("^(?<path>.+?\\.java):(?<line>\\d+): warning: (?:\\[(?<lint>[\\w-]+)] )?(?<msg>.*)$");
    private static final Pattern JAVAC_UNLOCATED = Pattern.compile("^warning: (?:\\[(?<lint>[\\w-]+)] )?(?<msg>.*)$");

    private WarningParser() {
    }

    public static List<CompilerWarning> parse(String output) {
        Set<CompilerWarning> warnings = new LinkedHashSet<>();
        String[] lines = output.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher m;
            if ((m = PROBLEM_HEADER.matcher(line)).matches()) {
                i = parseProblem(lines, i, m.group("diag"), warnings);
            } else if ((m = KOTLIN_LOCATED.matcher(line)).matches()) {
                warnings.add(kotlin(toPath(m.group("path")), Integer.parseInt(m.group("line")), m.group("msg")));
            } else if ((m = KOTLIN_UNLOCATED.matcher(line)).matches()) {
                warnings.add(kotlin(null, 0, m.group("msg")));
            } else if ((m = JAVAC.matcher(line)).matches()) {
                warnings.add(new CompilerWarning(toPath(m.group("path")), Integer.parseInt(m.group("line")), m.group("lint"), m.group("msg")));
            } else if ((m = JAVAC_UNLOCATED.matcher(line)).matches()) {
                warnings.add(new CompilerWarning(null, 0, m.group("lint"), m.group("msg")));
            }
        }
        return new ArrayList<>(warnings);
    }

    /** Parses the indented body of a {@code Problem found:} block; returns the index of its last line. */
    private static int parseProblem(String[] lines, int header, String diagnostic, Set<CompilerWarning> out) {
        String message = null;
        String path = null;
        int lineNumber = 0;
        int i = header + 1;
        // The first indented line is the problem's label ("Kotlin compiler warning"), then the details.
        boolean labelSeen = false;
        for (; i < lines.length && lines[i].startsWith("  "); i++) {
            Matcher location = PROBLEM_LOCATION.matcher(lines[i]);
            if (location.matches()) {
                path = toPath(location.group("path"));
                lineNumber = Integer.parseInt(location.group("line"));
            } else if (!labelSeen) {
                labelSeen = true;
            } else if (message == null) {
                message = lines[i].trim();
            }
        }
        if (message != null) {
            CompilerWarning parsed = kotlin(path, lineNumber, message);
            out.add(new CompilerWarning(parsed.path(), parsed.line(), diagnostic, parsed.message()));
        }
        return i - 1;
    }

    private static CompilerWarning kotlin(String path, int line, String message) {
        Matcher m = KOTLIN_DIAGNOSTIC.matcher(message);
        if (m.matches()) {
            return new CompilerWarning(path, line, m.group("diag"), m.group("msg"));
        }
        return new CompilerWarning(path, line, null, message);
    }

    private static String toPath(String raw) {
        if (raw.startsWith("file:")) {
            try {
                return Paths.get(URI.create(raw)).toString();
            } catch (RuntimeException e) {
                return raw.replaceFirst("^file:(//)?", "");
            }
        }
        return raw;
    }
}
