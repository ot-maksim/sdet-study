package com.study.init;

import static com.study.init.Log.header;
import static com.study.init.Log.log;

/**
 * 4. Lazy class initialization.
 * A compile-time constant and an array of the class type do not initialize it.
 * A non-constant static field does.
 */
public class Ex4LazyClassInit {

    static class K {
        static final int CONST = 42;          // compile-time constant
        static final Integer BOXED = 43;      // not a constant (boxing)
        static { log("K class initialized"); }
    }

    public static void run() {
        header("4. Lazy class initialization");
        log("reading K.CONST (constant) = " + K.CONST);
        log("creating array new K[1]");
        K[] arr = new K[1];
        log("reading K.BOXED...");
        log("K.BOXED = " + K.BOXED);
    }
}
