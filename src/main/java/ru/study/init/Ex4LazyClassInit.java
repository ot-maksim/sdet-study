package ru.study.init;

import static ru.study.init.Log.header;
import static ru.study.init.Log.log;

/**
 * 4. Ленивая инициализация класса.
 * Константа времени компиляции и массив типа класс не инициализируют.
 * Не-константное static-поле инициализирует.
 */
public class Ex4LazyClassInit {

    static class K {
        static final int CONST = 42;          // константа времени компиляции
        static final Integer BOXED = 43;      // не константа (боксинг)
        static { log("K class initialized"); }
    }

    public static void run() {
        header("4. Ленивая инициализация класса");
        log("читаем K.CONST (константа) = " + K.CONST);
        log("создаём массив new K[1]");
        K[] arr = new K[1];
        log("читаем K.BOXED...");
        log("K.BOXED = " + K.BOXED);
    }
}
