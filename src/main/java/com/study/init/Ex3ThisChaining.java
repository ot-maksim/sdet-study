package com.study.init;

import static com.study.init.Log.header;
import static com.study.init.Log.log;

/**
 * 3. this(...) chaining.
 * Field initializers and instance blocks run once: in the constructor of the chain
 * that calls super(). A constructor that calls this(...) does not run them.
 */
public class Ex3ThisChaining {

    static class T {
        { log("T instance block"); }

        T() {
            this("x");
            log("T() body");
        }

        T(String s) {
            log("T(String) body");
        }
    }

    public static void run() {
        header("3. this(...) chaining: new T()");
        new T();
    }
}
