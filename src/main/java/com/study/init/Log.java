package com.study.init;

/** Мини-утилита для вывода шагов инициализации. */
public final class Log {
    private Log() {}

    public static void log(String s) {
        System.out.println("  " + s);
    }

    /** Печатает сообщение и возвращает его же: удобно для инициализаторов полей. */
    public static String f(String s) {
        log(s);
        return s;
    }

    public static void header(String s) {
        System.out.println("\n=== " + s + " ===");
    }
}
