package com.study.init;

import static com.study.init.Log.header;
import static com.study.init.Log.log;

/**
 * 3. Цепочка this(...).
 * Инициализаторы полей и блоки выполняются один раз: в том конструкторе цепочки,
 * который вызывает super(). Конструктор с this(...) их не запускает.
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
        header("3. this(...) цепочка: new T()");
        new T();
    }
}
