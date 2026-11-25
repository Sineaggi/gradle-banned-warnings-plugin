package io.github.sineaggi.gradle.bannedwarnings.internal;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the scripts a Kotlin DSL script applies with {@code apply(from = ...)} or {@code apply { from(...) }}.
 *
 * <p>Only paths Gradle would resolve without running the script are found: a string literal, optionally wrapped
 * in {@code file(...)}, {@code rootProject.file(...)}, {@code rootDir.resolve(...)},
 * {@code layout.projectDirectory.file(...)} or {@code layout.settingsDirectory.file(...)}, and using at most
 * {@code $rootDir} or {@code $projectDir}. Anything else, such as a URL or a computed path, is skipped.
 */
public final class ApplyFromFinder {

    private static final Pattern APPLY_FROM = Pattern.compile("\\bapply\\s*\\(\\s*from\\s*=\\s*");
    private static final Pattern APPLY_BLOCK_FROM = Pattern.compile("\\bapply\\s*\\{[^{}]*?\\bfrom\\s*\\(\\s*");
    private static final Pattern STRING = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern ROOT_BASED = Pattern.compile("\\brootDir\\b|\\brootProject\\b|\\bsettingsDirectory\\b|\\bisolated\\.rootProject\\b");

    private ApplyFromFinder() {
    }

    /**
     * @param text the script
     * @param baseDir the directory relative paths resolve against: the project directory for project scripts,
     *     the settings directory for settings scripts
     * @param rootDir the root directory of the build
     */
    public static List<File> find(String text, File baseDir, File rootDir) {
        String code = withoutComments(text);
        Set<File> files = new LinkedHashSet<>();
        for (Pattern start : List.of(APPLY_FROM, APPLY_BLOCK_FROM)) {
            Matcher m = start.matcher(code);
            while (m.find()) {
                String argument = argument(code, m.end());
                File file = resolve(argument, baseDir, rootDir);
                if (file != null) {
                    files.add(file);
                }
            }
        }
        return new ArrayList<>(files);
    }

    /** The text of a call argument starting at {@code from}, up to its closing parenthesis or a comma. */
    private static String argument(String code, int from) {
        int depth = 0;
        boolean inString = false;
        for (int i = from; i < code.length(); i++) {
            char c = code.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '(') {
                depth++;
            } else if (c == ')' || (c == ',' && depth == 0)) {
                if (depth == 0) {
                    return code.substring(from, i);
                }
                depth--;
            }
        }
        return code.substring(from);
    }

    static File resolve(String argument, File baseDir, File rootDir) {
        Matcher literal = STRING.matcher(argument);
        if (!literal.find()) {
            return null;
        }
        String path = literal.group(1)
                .replace("${rootDir}", rootDir.getPath())
                .replace("$rootDir", rootDir.getPath())
                .replace("${projectDir}", baseDir.getPath())
                .replace("$projectDir", baseDir.getPath());
        if (path.contains("$") || path.matches("^[a-zA-Z][a-zA-Z0-9+.-]+://.*")) {
            return null;
        }
        if (!path.endsWith(".kts")) {
            return null;
        }
        File file = new File(path);
        if (!file.isAbsolute()) {
            String before = argument.substring(0, literal.start());
            file = new File(ROOT_BASED.matcher(before).find() ? rootDir : baseDir, path);
        }
        return file.toPath().normalize().toFile();
    }

    private static String withoutComments(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        boolean[] inBlockComment = {false};
        for (String line : text.split("\\R", -1)) {
            sb.append(ScriptScanner.stripComments(line, inBlockComment)).append('\n');
        }
        return sb.toString();
    }
}
