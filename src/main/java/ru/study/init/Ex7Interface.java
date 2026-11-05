package ru.study.init;

import static ru.study.init.Log.f;
import static ru.study.init.Log.header;
import static ru.study.init.Log.log;

/**
 * 7. Интерфейсы.
 * Реализация интерфейса не инициализирует его. Исключение: интерфейс с default-методами
 * инициализируется вместе с классом-реализацией.
 */
public class Ex7Interface {

    interface I {
        String X = f("I field initialized");
    }

    static class Impl implements I {
        static { log("Impl static block"); }
    }

    public static void run() {
        header("7. Интерфейс не инициализируется вместе с классом");
        new Impl();
        log("читаем I.X...");
        log("I.X = " + I.X);
    }
}
