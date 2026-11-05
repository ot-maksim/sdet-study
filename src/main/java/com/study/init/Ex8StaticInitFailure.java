package com.study.init;

import static com.study.init.Log.header;
import static com.study.init.Log.log;

/**
 * 8. Исключение в static-инициализаторе.
 * Первый доступ даёт ExceptionInInitializerError (с исходной причиной),
 * все следующие дают NoClassDefFoundError.
 */
public class Ex8StaticInitFailure {

    static class Bad {
        static int v = 1 / Integer.parseInt("0");
    }

    public static void run() {
        header("8. Исключение в static-инициализаторе");
        try {
            log("v = " + Bad.v);
        } catch (Throwable t) {
            log("1-й доступ: " + t);
        }
        try {
            log("v = " + Bad.v);
        } catch (Throwable t) {
            log("2-й доступ: " + t);
        }
    }
}
