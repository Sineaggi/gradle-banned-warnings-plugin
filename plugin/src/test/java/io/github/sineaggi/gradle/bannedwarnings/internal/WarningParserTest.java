package io.github.sineaggi.gradle.bannedwarnings.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class WarningParserTest {

    @Test
    void parsesPlainKotlinWarnings() {
        List<CompilerWarning> warnings = WarningParser.parse("""
                > Task :compileKotlin
                w: file:///tmp/proto/src/main/kotlin/A.kt:2:13 [DEPRECATION] 'fun old(): Int' is deprecated. old.
                w: file:///tmp/proto/src/main/kotlin/B.kt:5:1: 'val x' is deprecated.
                w: Some warning without a location
                """);
        assertEquals(List.of(
                new CompilerWarning("/tmp/proto/src/main/kotlin/A.kt", 2, "DEPRECATION", "'fun old(): Int' is deprecated. old."),
                new CompilerWarning("/tmp/proto/src/main/kotlin/B.kt", 5, null, "'val x' is deprecated."),
                new CompilerWarning(null, 0, null, "Some warning without a location")
        ), warnings);
    }

    @Test
    void parsesKotlinProblemsApiBlocks() {
        List<CompilerWarning> warnings = WarningParser.parse("""
                Problem found: Kotlin compiler: DEPRECATION (id: kotlin:compiler:warning:DEPRECATION)
                  Kotlin compiler warning
                    [DEPRECATION] 'fun old(): Int' is deprecated. old.
                    Location: /tmp/proto/src/main/kotlin/A.kt line 2
                Problem found: Kotlin compiler: SOMETHING_ELSE (id: kotlin:compiler:error:SOMETHING_ELSE)
                  Kotlin compiler error
                    not a warning
                """);
        assertEquals(List.of(
                new CompilerWarning("/tmp/proto/src/main/kotlin/A.kt", 2, "DEPRECATION", "'fun old(): Int' is deprecated. old.")
        ), warnings);
    }

    @Test
    void parsesJavacWarnings() {
        List<CompilerWarning> warnings = WarningParser.parse("""
                /tmp/proto/src/main/java/B.java:2: warning: [deprecation] o() in B has been deprecated
                class C { void u(){ B.o(); } }
                                     ^
                warning: [options] source value 8 is obsolete
                1 warning
                """);
        assertEquals(List.of(
                new CompilerWarning("/tmp/proto/src/main/java/B.java", 2, "deprecation", "o() in B has been deprecated"),
                new CompilerWarning(null, 0, "options", "source value 8 is obsolete")
        ), warnings);
    }
}
