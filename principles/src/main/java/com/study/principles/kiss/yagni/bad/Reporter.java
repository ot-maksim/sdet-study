package com.study.principles.kiss.yagni.bad;

/**
 * "We will surely need other reporters" — so an interface came first, with one implementation. Its shape was
 * guessed from the only use there was: print a line.
 */
public interface Reporter {

    void report(String message);

    /** "For reporters that buffer." None does. */
    default void flush() {
    }
}
