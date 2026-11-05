package com.study.init;

import static com.study.init.Log.header;
import static com.study.init.Log.log;

/**
 * 5. Static-поле читается до объявления.
 * Поля заполняются по порядку текста. Когда вычисляется a, поле b ещё равно 0.
 * Прямое "a = b + 1" компилятор запретит, а через метод проходит.
 */
public class Ex5ForwardReference {

    static class F {
        static int a = get();
        static int b = 5;

        static int get() { return b; }
    }

    public static void run() {
        header("5. Static-поле читается до объявления");
        log("F.a = " + F.a + ", F.b = " + F.b);
    }
}
