package com.study.init;

import static com.study.init.Log.f;
import static com.study.init.Log.header;
import static com.study.init.Log.log;

/**
 * 7. Interfaces.
 * Implementing an interface does not initialize it. Exception: an interface with default methods
 * is initialized together with the implementing class.
 */
public class Ex7Interface {

    interface I {
        String X = f("I field initialized");
    }

    static class Impl implements I {
        static { log("Impl static block"); }
    }

    public static void run() {
        header("7. Interface is not initialized together with the class");
        new Impl();
        log("reading I.X...");
        log("I.X = " + I.X);
    }
}
