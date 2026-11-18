package com.study.principles.kiss.flags;

import com.study.principles.kiss.flags.bad.OrderSteps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Runs the flag step with every combination of its five flags, against a correct shop and against shops with
 * a regression, and sorts each combination into one of four verdicts. This is what reviewing a flag step
 * really takes — and nobody does it, because 32 is too many to do by hand.
 */
public final class FlagAudit {

    public enum Verdict {
        /** Works and every flag set changes something. */
        OK,
        /** Throws on a correct shop: a combination the step accepts but cannot do. */
        BROKEN,
        /** {@code verify} is set and the step passes although something requested is broken. */
        LIES,
        /** A flag that is set changes nothing: the caller believes it asked for something. */
        DEAD_FLAG
    }

    public record Flags(boolean guest, boolean promo, boolean pay, boolean savedCard, boolean verify) {

        static final List<String> NAMES = List.of("guest", "promo", "pay", "savedCard", "verify");

        public static List<Flags> all() {
            List<Flags> all = new ArrayList<>();
            for (int bits = 0; bits < 32; bits++) {
                all.add(new Flags((bits & 16) != 0, (bits & 8) != 0, (bits & 4) != 0, (bits & 2) != 0,
                        (bits & 1) != 0));
            }
            return all;
        }

        boolean get(String name) {
            return switch (name) {
                case "guest" -> guest;
                case "promo" -> promo;
                case "pay" -> pay;
                case "savedCard" -> savedCard;
                case "verify" -> verify;
                default -> throw new IllegalArgumentException(name);
            };
        }

        Flags without(String name) {
            return new Flags(guest && !name.equals("guest"), promo && !name.equals("promo"),
                    pay && !name.equals("pay"), savedCard && !name.equals("savedCard"),
                    verify && !name.equals("verify"));
        }

        /** As the call site reads: {@code placeOrder("alice", true, false, true, false, true)}. */
        public String callSite() {
            return "placeOrder(\"alice\", %s, %s, %s, %s, %s)".formatted(guest, promo, pay, savedCard, verify);
        }
    }

    public record Row(Flags flags, Verdict verdict, String why) {
    }

    /** What one run produced: the order (without id) or the kind of failure. */
    private record Outcome(Order order, String failure) {
        boolean passed() {
            return failure == null;
        }
    }

    private FlagAudit() {
    }

    public static List<Row> auditAll() {
        return Flags.all().stream().map(FlagAudit::audit).toList();
    }

    public static Row audit(Flags flags) {
        Outcome correct = run(flags, FakeShop.Build.CORRECT);
        if (!correct.passed()) {
            return new Row(flags, Verdict.BROKEN, "fails on a correct shop: " + correct.failure());
        }
        if (flags.verify()) {
            if (flags.promo() && run(flags, FakeShop.Build.PROMO_BROKEN).passed()) {
                return new Row(flags, Verdict.LIES, "verify passes when the promo is broken");
            }
            if (flags.pay() && run(flags, FakeShop.Build.PAYMENT_BROKEN).passed()) {
                return new Row(flags, Verdict.LIES, "verify passes when the payment is broken");
            }
        }
        List<String> dead = Flags.NAMES.stream()
                .filter(flags::get)
                .filter(name -> sameEverywhere(flags, flags.without(name)))
                .toList();
        if (!dead.isEmpty()) {
            return new Row(flags, Verdict.DEAD_FLAG, String.join(", ", dead) + " changes nothing");
        }
        return new Row(flags, Verdict.OK, "");
    }

    private static boolean sameEverywhere(Flags a, Flags b) {
        return Arrays.stream(FakeShop.Build.values()).allMatch(build -> run(a, build).equals(run(b, build)));
    }

    private static Outcome run(Flags f, FakeShop.Build build) {
        FakeShop shop = new FakeShop(build);
        String alice = shop.registerCustomer("alice");
        try {
            Order order = new OrderSteps(shop).placeOrder(alice, f.guest(), f.promo(), f.pay(), f.savedCard(),
                    f.verify());
            return new Outcome(order.withoutId(), null);
        } catch (AssertionError e) {
            return new Outcome(null, "verify failed");
        } catch (RuntimeException e) {
            return new Outcome(null, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
