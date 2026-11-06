package com.study.init;

import static com.study.init.Log.f;
import static com.study.init.Log.header;
import static com.study.init.Log.log;

/**
 * 2. Inheritance + a virtual call from the parent constructor.
 * Order: parent static, child static, parent instance + parent constructor,
 * child instance + child constructor.
 * Pitfall: P() calls hook(), which is C.hook(), and the name field is still null at that moment.
 */
public class Ex2Inheritance {

    static class P {
        static { log("P static block"); }
        String pf = f("P instance field");
        { log("P instance block"); }

        P() {
            log("P constructor");
            hook(); // virtual call
        }

        void hook() { log("P.hook"); }
    }

    static class C extends P {
        static { log("C static block"); }
        String name = f("C instance field name=Bob");
        { log("C instance block"); }

        C() {
            super();
            log("C constructor, name=" + name);
        }

        @Override
        void hook() { log("C.hook, name=" + name); } // name == null
    }

    public static void run() {
        header("2. Inheritance: new C()");
        new C();
    }
}
