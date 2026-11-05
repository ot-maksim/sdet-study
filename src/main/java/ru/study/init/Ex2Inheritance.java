package ru.study.init;

import static ru.study.init.Log.f;
import static ru.study.init.Log.header;
import static ru.study.init.Log.log;

/**
 * 2. Наследование + виртуальный вызов из конструктора родителя.
 * Порядок: static родителя, static потомка, instance родителя + конструктор родителя,
 * instance потомка + конструктор потомка.
 * Ловушка: P() вызывает hook(), а это C.hook(), и поле name в этот момент ещё null.
 */
public class Ex2Inheritance {

    static class P {
        static { log("P static block"); }
        String pf = f("P instance field");
        { log("P instance block"); }

        P() {
            log("P constructor");
            hook(); // виртуальный вызов
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
        header("2. Наследование: new C()");
        new C();
    }
}
