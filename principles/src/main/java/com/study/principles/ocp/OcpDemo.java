package com.study.principles.ocp;

import com.study.principles.ocp.bad.DriverFactory;
import com.study.principles.ocp.bad.EnvConfig;
import com.study.principles.ocp.bad.TimeoutSettings;
import com.study.principles.ocp.good.BrowserProfiles;
import com.study.principles.ocp.good.Environment;

import java.util.List;
import java.util.function.Supplier;

/**
 * Every browser name through the bad switches and the good registry, then every environment name.
 * The demo runs from main, so the test-only "remote" plugin is not on the class path: discover() == built-ins.
 *
 * mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.ocp.OcpDemo
 */
public class OcpDemo {

    public static void main(String[] args) {
        BrowserProfiles profiles = BrowserProfiles.discover();
        for (String name : List.of("chrome", "mobile-web", "remote", "edge")) {
            System.out.println("=== -Dbrowser=" + name + " ===");
            show("bad  launch  ", () -> DriverFactory.create(name).toString());
            show("bad  timeouts", () -> TimeoutSettings.forBrowser(name).toString());
            show("good launch  ", () -> profiles.get(name).launch().toString());
            show("good timeouts", () -> profiles.get(name).timeouts().toString());
        }
        for (String env : List.of("qa", "prod", "preprod")) {
            System.out.println("=== -Denv=" + env + " ===");
            show("bad  destructive allowed", () -> String.valueOf(EnvConfig.destructiveAllowed(env)));
            show("good destructive allowed", () -> String.valueOf(Environment.load(env).destructiveAllowed()));
        }
    }

    private static void show(String label, Supplier<String> value) {
        try {
            System.out.println("  " + label + " -> " + value.get());
        } catch (RuntimeException e) {
            System.out.println("  " + label + " -> FAILS: " + e.getMessage());
        }
    }
}
