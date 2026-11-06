package com.study.principles.inheritance.session;

import com.study.principles.inheritance.session.composition.ManagedSession;
import com.study.principles.inheritance.session.composition.WebDriver;

import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Runs the same three "real life" scenarios against every implementation:
 *  A. the test closes the session, then @AfterEach closes it again
 *  B. start() fails halfway (page creation) — do we leak a browser?
 *  C. start() is called twice (base-class @BeforeEach + an extension)
 *  D. a helper keeps using the page after close
 *
 * mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.inheritance.session.SessionDemo
 */
public class SessionDemo {

    public static void main(String[] args) {
        System.out.println("=== BAD: shared protected fields, every subclass re-implements the rules ===");
        run("bad.WebSession", farm -> {
            var s = new com.study.principles.inheritance.session.bad.WebSession(farm);
            return new Subject(s::start, s::page, s::close);
        });
        run("bad.MobileSession", farm -> {
            var s = new com.study.principles.inheritance.session.bad.MobileSession(farm);
            return new Subject(s::start, s::page, s::close);
        });

        System.out.println("\n=== GOOD: abstract class owns state + invariants (Template Method) ===");
        run("good.WebSession", farm -> {
            var s = new com.study.principles.inheritance.session.good.WebSession(farm);
            return new Subject(s::start, s::page, s::close);
        });
        run("good.MobileSession", farm -> {
            var s = new com.study.principles.inheritance.session.good.MobileSession(farm);
            return new Subject(s::start, s::page, s::close);
        });

        System.out.println("\n=== COMPOSITION: final ManagedSession + injected SessionDriver ===");
        run("ManagedSession(WebDriver)", farm -> {
            var s = new ManagedSession(new WebDriver(farm));
            return new Subject(s::start, s::page, s::close);
        });
    }

    private static void run(String name, Function<BrowserFarm, Subject> create) {
        System.out.println("-- " + name);

        attempt("A. double close      ", () -> {
            Subject s = create.apply(new BrowserFarm());
            s.start.run();
            s.close.run();
            s.close.run();
            return "ok";
        });

        attempt("B. start fails midway", () -> {
            // Typical fixture: "Session open() { var s = new X(); s.start(); return s; }"
            // If start() throws, the caller never gets the reference — nobody can close it.
            BrowserFarm farm = new BrowserFarm().failOnNewPage();
            try {
                Subject s = create.apply(farm);
                s.start.run();
                return "unexpected: start passed";
            } catch (RuntimeException e) {
                long open = farm.openBrowsers();
                return "start threw, open browsers left: " + open + (open > 0 ? "  <-- LEAK" : "");
            }
        });

        attempt("C. start called twice", () -> {
            BrowserFarm farm = new BrowserFarm();
            Subject s = create.apply(farm);
            s.start.run();
            s.start.run();                         // e.g. both a base-class @BeforeEach and an extension start it
            s.close.run();
            long open = farm.openBrowsers();
            return "after close, open browsers left: " + open + (open > 0 ? "  <-- LEAK" : "");
        });

        attempt("D. use after close   ", () -> {
            Subject s = create.apply(new BrowserFarm());
            s.start.run();
            s.close.run();
            FakePage page = s.page.get();
            page.navigate("https://example.com/cart");
            return "silently 'worked' on a dead browser (alive=" + page.isAlive() + ")  <-- fails later, far from the cause";
        });
    }

    private static void attempt(String scenario, Callable<String> body) {
        try {
            System.out.println("   " + scenario + ": " + body.call());
        } catch (Exception e) {
            System.out.println("   " + scenario + ": " + e.getClass().getSimpleName() + " — " + e.getMessage());
        }
    }

    /** Uniform view over unrelated hierarchies — demo plumbing only. */
    private record Subject(Runnable start, Supplier<FakePage> page, Runnable close) {
    }
}
