/**
 * Class invariants: what they are, how they get broken and how an object protects them.
 *
 * <h2>Definition</h2>
 * An <b>invariant</b> (Latin <i>in-</i> "not" + <i>varians</i> "changing") is a condition that is
 * true for an object during its whole lifetime: right after construction and after every public
 * operation. It is a <i>rule</i> describing a valid state; a state that violates the rule is an
 * invalid (corrupted) state.
 * <p>
 * Examples: {@code from <= to} for a date range, {@code passed + failed == total} for a test
 * suite result, {@code status == FINISHED <=> finishedAt != null} for a test run.
 *
 * <h2>Part of the class contract (Design by Contract, B. Meyer)</h2>
 * <ul>
 *   <li><b>Precondition</b> — what must be true <i>before</i> a method is called
 *       (e.g. argument is not null).</li>
 *   <li><b>Postcondition</b> — what the method guarantees <i>after</i> it returns
 *       (e.g. {@code create()} returns a user with an id).</li>
 *   <li><b>Invariant</b> — what is true for the object <i>always</i>, between any calls.</li>
 * </ul>
 * An invariant may be temporarily broken <i>inside</i> a method, but must hold again at the
 * boundaries: after the constructor and after each public method, i.e. whenever outside code
 * can observe the object.
 *
 * <h2>Who is responsible</h2>
 * The object itself is the guardian of its invariants. It must:
 * <ol>
 *   <li>refuse to be created in an invalid state (validate in the constructor, fail fast);</li>
 *   <li>not let outside code change its state bypassing its methods
 *       (no leaked references to mutable internals, no "setter for every field");</li>
 *   <li>not break the rules in its own methods.</li>
 * </ol>
 * Note: {@code private} hides the <i>field</i>, it does not make the <i>object the field points
 * to</i> immutable. Encapsulation is more than "private fields + getters".
 *
 * <h2>Examples in this package</h2>
 * <ul>
 *   <li>{@link com.study.principles.invariants.leak} — a mutable collection leaks through a getter.</li>
 *   <li>{@link com.study.principles.invariants.hashkey} — a mutable object used as a {@code HashMap} key.</li>
 *   <li>{@link com.study.principles.invariants.record} — {@code record}: compact constructor and the
 *       "shallow immutability" trap.</li>
 *   <li>{@link com.study.principles.invariants.testrun} — setters for every field vs domain operations;
 *       why a well encapsulated object is easy to test.</li>
 * </ul>
 * Each sub-package has a {@code *Demo} class with {@code main()} and matching unit tests
 * under {@code src/test/java}.
 */
package com.study.principles.invariants;
