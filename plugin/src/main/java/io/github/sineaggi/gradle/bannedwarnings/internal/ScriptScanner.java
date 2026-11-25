package io.github.sineaggi.gradle.bannedwarnings.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans Kotlin DSL scripts for banned patterns.
 *
 * <p>Gradle compiles build scripts itself and only prints the compiler's warnings, so a plugin cannot
 * observe them; matching the source text is the reliable way to keep a script warning from coming back.
 * Comments are skipped; string literals are not.
 */
public final class ScriptScanner {

    private ScriptScanner() {
    }

    public static List<Occurrence> scan(String text, String relativePath, List<RuleSpec> rules) {
        List<Occurrence> found = new ArrayList<>();
        List<RuleSpec> scriptRules = rules.stream().filter(r -> r.buildScriptPattern() != null).toList();
        if (scriptRules.isEmpty()) {
            return found;
        }
        List<Pattern> patterns = scriptRules.stream().map(r -> Pattern.compile(r.buildScriptPattern())).toList();
        String[] lines = text.split("\\R", -1);
        boolean[] inBlockComment = {false};
        for (int i = 0; i < lines.length; i++) {
            String code = stripComments(lines[i], inBlockComment);
            if (code.isBlank()) {
                continue;
            }
            for (int r = 0; r < scriptRules.size(); r++) {
                Matcher m = patterns.get(r).matcher(code);
                if (m.find()) {
                    found.add(new Occurrence(scriptRules.get(r).id(), relativePath, i + 1, lines[i].strip()));
                }
            }
        }
        return found;
    }

    /** Removes {@code //} and {@code /* *}{@code /} comments, tracking block comments across lines. */
    static String stripComments(String line, boolean[] inBlockComment) {
        StringBuilder code = new StringBuilder(line.length());
        boolean inString = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            char next = i + 1 < line.length() ? line.charAt(i + 1) : '\0';
            if (inBlockComment[0]) {
                if (c == '*' && next == '/') {
                    inBlockComment[0] = false;
                    i++;
                }
                continue;
            }
            if (inString) {
                if (c == '\\') {
                    code.append(c);
                    if (i + 1 < line.length()) {
                        code.append(next);
                        i++;
                    }
                    continue;
                }
                if (c == '"') {
                    inString = false;
                }
                code.append(c);
                continue;
            }
            if (c == '"') {
                inString = true;
                code.append(c);
            } else if (c == '/' && next == '/') {
                break;
            } else if (c == '/' && next == '*') {
                inBlockComment[0] = true;
                i++;
            } else {
                code.append(c);
            }
        }
        return code.toString();
    }
}
