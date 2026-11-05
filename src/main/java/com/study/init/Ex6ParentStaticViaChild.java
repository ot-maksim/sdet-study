package com.study.init;

import static com.study.init.Log.header;
import static com.study.init.Log.log;

/**
 * 6. Parent static field accessed via the child.
 * C2.X compiles to P2.X, so only P2 is initialized.
 */
public class Ex6ParentStaticViaChild {

    static class P2 {
        static int X = 1;
        static { log("P2 static block"); }
    }

    static class C2 extends P2 {
        static { log("C2 static block"); }
    }

    public static void run() {
        header("6. Parent static via child: C2.X");
        log("C2.X = " + C2.X);
        log("now new C2()");
        new C2();
    }
}
