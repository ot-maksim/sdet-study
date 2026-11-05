package com.study.init;

import static com.study.init.Log.header;
import static com.study.init.Log.log;

/**
 * 8. An exception in a static initializer.
 * The first access throws ExceptionInInitializerError (with the original cause),
 * every following access throws NoClassDefFoundError.
 */
public class Ex8StaticInitFailure {

    static class Bad {
        static int v = 1 / Integer.parseInt("0");
    }

    public static void run() {
        header("8. Exception in a static initializer");
        try {
            log("v = " + Bad.v);
        } catch (Throwable t) {
            log("1st access: " + t);
        }
        try {
            log("v = " + Bad.v);
        } catch (Throwable t) {
            log("2nd access: " + t);
        }
    }
}
