package com.study.principles.demeter;

import com.study.principles.demeter.FakeApp.Layout;
import com.study.principles.demeter.bad.UserScenarios;

import java.util.function.Consumer;

/**
 * The same three user actions through train wrecks and through intention methods, on both layouts.
 * <pre>
 * mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.demeter.DemeterDemo
 * </pre>
 */
public class DemeterDemo {

    public static void main(String[] args) {
        for (Layout layout : Layout.values()) {
            System.out.println("=== layout " + layout + " ===");
            run("bad  logout", layout, app -> UserScenarios.logout(new com.study.principles.demeter.bad.AppPage(app)));
            run("bad  language", layout,
                    app -> UserScenarios.switchLanguage(new com.study.principles.demeter.bad.AppPage(app), "de"));
            run("bad  profile", layout, app -> UserScenarios.openProfile(new com.study.principles.demeter.bad.AppPage(app)));
            run("good logout", layout, app -> new com.study.principles.demeter.good.AppPage(app).logout());
            run("good language", layout, app -> new com.study.principles.demeter.good.AppPage(app).switchLanguage("de"));
            run("good profile", layout, app -> new com.study.principles.demeter.good.AppPage(app).openProfile());
            System.out.println();
        }
    }

    private static void run(String scenario, Layout layout, Consumer<FakeApp> action) {
        FakeApp app = new FakeApp(layout);
        try {
            action.accept(app);
            System.out.printf("%-14s -> ok: path=%s, language=%s, loggedIn=%s%n",
                    scenario, app.path(), app.language(), app.loggedIn());
        } catch (RuntimeException e) {
            System.out.printf("%-14s -> %s%n", scenario, e.getMessage());
        }
    }
}
