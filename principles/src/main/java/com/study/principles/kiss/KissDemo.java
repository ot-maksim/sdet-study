package com.study.principles.kiss;

import com.study.principles.kiss.flags.FlagAudit;
import com.study.principles.kiss.flags.FlagAudit.Row;
import com.study.principles.kiss.yagni.bad.TreeReporter;
import com.study.principles.kiss.yagni.good.StepTree;
import com.study.principles.kiss.yagni.good.Steps;

/**
 * Prints the audit of the flag step (all 32 combinations) and the step tree through the guessed and the
 * extracted interface.
 * Run: {@code mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.kiss.KissDemo}
 */
public final class KissDemo {

    public static void main(String[] args) {
        System.out.println("== placeOrder(customer, guest, promo, pay, savedCard, verify): every combination ==");
        for (Row row : FlagAudit.auditAll()) {
            System.out.printf("%-9s %s  %s%n", row.verdict(), row.flags().callSite(), row.why());
        }
        System.out.println();
        System.out.println("== The step tree that really came ==");
        TreeReporter guessed = new TreeReporter();
        new com.study.principles.kiss.yagni.bad.CheckoutSteps(guessed).buy();
        System.out.println("through report(String): " + guessed.roots());
        StepTree extracted = new StepTree();
        new com.study.principles.kiss.yagni.good.CheckoutSteps(new Steps(extracted)).buy();
        System.out.println("through StepListener:    " + extracted.roots());
    }
}
