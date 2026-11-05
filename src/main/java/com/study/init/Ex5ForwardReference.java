package com.study.init;

import static com.study.init.Log.header;
import static com.study.init.Log.log;

/**
 * 5. A static field is read before its declaration.
 * Fields are initialized in textual order. When a is computed, b is still 0.
 * A direct "a = b + 1" is rejected by the compiler, but going through a method works.
 */
public class Ex5ForwardReference {

    static class F {
        static int a = get();
        static int b = 5;

        static int get() { return b; }
    }

    public static void run() {
        header("5. Static field read before its declaration");
        log("F.a = " + F.a + ", F.b = " + F.b);
    }
}
