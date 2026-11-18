package com.study.principles.ocp.good;

import com.study.principles.ocp.Browser;
import com.study.principles.ocp.Timeouts;
import com.study.principles.ocp.Viewport;

/**
 * Strategy: everything that varies per browser, in one type. A new browser is one new class; the compiler
 * makes it answer every question — there is no {@code default} branch to fall into.
 * <p>
 * Implementations need a public no-arg constructor to be found by {@link java.util.ServiceLoader}.
 */
public interface BrowserProfile {

    String name();

    Timeouts timeouts();

    Viewport viewport();

    Browser launch();
}
