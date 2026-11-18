package com.study.principles.srp.good;

/** Reason to change: test data conventions (which cards the payment sandbox accepts). */
public final class Cards {

    private Cards() {
    }

    public static String validVisa() {
        return "4111111111111111";
    }
}
