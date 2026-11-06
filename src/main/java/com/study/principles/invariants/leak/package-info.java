/**
 * Invariant violation #1: a mutable collection leaks through a getter.
 * <p>
 * The field is {@code private} and there is no setter, so the class <i>looks</i> protected.
 * But the getter returns a reference to the internal list, and outside code changes the
 * state bypassing the object's methods.
 * <ul>
 *   <li>{@link com.study.principles.invariants.leak.bad.TestSuiteResult} — the leak.</li>
 *   <li>{@link com.study.principles.invariants.leak.good.TestSuiteResult} — defensive copy +
 *       derived value instead of a stored counter.</li>
 *   <li>{@link com.study.principles.invariants.leak.LeakDemo} — runnable demonstration.</li>
 * </ul>
 */
package com.study.principles.invariants.leak;
