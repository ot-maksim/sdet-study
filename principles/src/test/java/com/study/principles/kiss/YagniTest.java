package com.study.principles.kiss;

import com.study.principles.kiss.yagni.StepNode;
import com.study.principles.kiss.yagni.bad.CheckoutSteps;
import com.study.principles.kiss.yagni.bad.ConsoleReporter;
import com.study.principles.kiss.yagni.bad.ReporterConfig;
import com.study.principles.kiss.yagni.bad.ReporterFactory;
import com.study.principles.kiss.yagni.bad.TreeReporter;
import com.study.principles.kiss.yagni.good.ConsoleSteps;
import com.study.principles.kiss.yagni.good.StepTree;
import com.study.principles.kiss.yagni.good.Steps;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YagniTest {

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);

    private String printed() {
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Nested
    class Speculative {

        @Test
        void fourTypesAndAConfigToPrintALine() {
            new CheckoutSteps(new ReporterFactory(out).create(ReporterConfig.DEFAULT)).buy();

            assertThat(new ReporterFactory(out).create(ReporterConfig.DEFAULT)).isInstanceOf(ConsoleReporter.class);
            assertThat(printed()).isEqualTo("""
                    [STEP] buy
                    [STEP] add to cart
                    [STEP] pay
                    [STEP] confirm 3DS
                    """);
        }

        @Test
        void thePlannedReportersAreStillPlaceholders() {
            ReporterFactory factory = new ReporterFactory(out);

            assertThatThrownBy(() -> factory.create(new ReporterConfig("file", List.of())))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void theReporterThatReallyCameDoesNotFitTheGuessedInterface() {
            TreeReporter tree = new TreeReporter();

            new CheckoutSteps(tree).buy();

            assertThat(tree.roots()).extracting(StepNode::name)
                    .as("every step is a root: nesting cannot pass through report(String)")
                    .containsExactly("buy", "add to cart", "pay", "confirm 3DS");
            assertThat(tree.roots()).allMatch(node -> node.depth() == 1);
        }
    }

    @Nested
    class Simple {

        @Test
        void version1IsOneClassWithoutAnyAbstraction() {
            new com.study.principles.kiss.yagni.good.v1.CheckoutSteps(out).buy();

            assertThat(printed()).isEqualTo("""
                    [STEP] buy
                      [STEP] add to cart
                      [STEP] pay
                        [STEP] confirm 3DS
                    """);
        }

        @Test
        void version2PrintsTheSameThroughTheExtractedListener() {
            new com.study.principles.kiss.yagni.good.CheckoutSteps(new Steps(new ConsoleSteps(out))).buy();

            assertThat(printed()).isEqualTo("""
                    [STEP] buy
                      [STEP] add to cart
                      [STEP] pay
                        [STEP] confirm 3DS
                    """);
        }

        @Test
        void theSecondRealListenerGetsTheTree() {
            StepTree tree = new StepTree();

            new com.study.principles.kiss.yagni.good.CheckoutSteps(new Steps(tree)).buy();

            assertThat(tree.roots()).hasSize(1);
            StepNode buy = tree.roots().get(0);
            assertThat(buy.depth()).isEqualTo(3);
            assertThat(buy).hasToString("buy > [add to cart, pay > [confirm 3DS]]");
        }

        @Test
        void aFailingStepIsStillClosed() {
            StepTree tree = new StepTree();
            Steps steps = new Steps(tree);

            assertThatThrownBy(() -> steps.step("pay", () -> {
                throw new AssertionError("declined");
            })).hasMessage("declined");
            steps.step("next");

            assertThat(tree.roots()).extracting(StepNode::name).containsExactly("pay", "next");
        }
    }
}
