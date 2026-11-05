package com.study.init;

import static com.study.init.Log.f;
import static com.study.init.Log.header;
import static com.study.init.Log.log;

/**
 * 1. A single class.
 * First all static parts (in textual order), then the instance part (in textual order).
 * Static parts run once, the instance part runs on every new.
 */
public class Ex1SingleClass {

    static class A {
        static String sf1 = f("A static field 1");
        String if1 = f("A instance field 1");
        static { log("A static block"); }
        { log("A instance block"); }
        static String sf2 = f("A static field 2");
        String if2 = f("A instance field 2");

        A() { log("A constructor"); }
    }

    public static void run() {
        header("1. Single class: first new A()");
        new A();
        header("1. Second new A(): static parts do not run again");
        new A();
    }
}
