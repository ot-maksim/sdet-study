package com.study.init;

/** Tiny utility for printing initialization steps. */
public final class Log {
    private Log() {}

    public static void log(String s) {
        System.out.println("  " + s);
    }

    /** Prints the message and returns it: handy for field initializers. */
    public static String f(String s) {
        log(s);
        return s;
    }

    public static void header(String s) {
        System.out.println("\n=== " + s + " ===");
    }
}
