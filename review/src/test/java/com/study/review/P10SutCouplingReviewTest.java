package com.study.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.study.review.bad.steps.Expected;
import com.study.sut.users.UserRules;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * #10 — the framework imports the SUT: its DTOs, its JSON mapper, its validation rules as the oracle. The tests
 * cannot disagree with the service — a bug in the rules is in the expectation too, a renamed field is renamed on
 * both sides. Checked as an architecture rule over the sources (ArchUnit does this on bytecode; stage 4).
 */
class P10SutCouplingReviewTest {

    private static final Path MAIN = Path.of("src/main/java/com/study/review");

    @Nested
    class Bad {

        @Test
        void theFrameworkCompilesAgainstTheSut() {
            assertThat(filesImporting("com.study.sut", MAIN.resolve("bad")))
                    .containsExactlyInAnyOrder("steps/Expected.java", "steps/UserSteps.java");
        }

        @Test
        void theOracleIsTheSutsOwnCode() {
            Map<String, Object> request = Map.of("name", " Ada ", "email", "ADA@EXAMPLE.COM", "age", 36, "country", "gb");

            // Whatever UserRules does — right or wrong — is "expected": the same call the service makes
            assertThat(Expected.user(request))
                    .isEqualTo(UserRules.validate(new com.study.sut.users.NewUser(" Ada ", "ADA@EXAMPLE.COM", 36, "gb")));
        }
    }

    @Nested
    class Good {

        @Test
        void theFrameworkKnowsTheSutOnlyThroughItsInterfaces() {
            assertThat(filesImporting("com.study.sut", MAIN.resolve("good"))).isEmpty();
        }
    }

    private static List<String> filesImporting(String packagePrefix, Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(f -> f.toString().endsWith(".java"))
                    .filter(f -> read(f).contains("import " + packagePrefix + "."))
                    .map(f -> root.relativize(f).toString().replace('\\', '/'))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
