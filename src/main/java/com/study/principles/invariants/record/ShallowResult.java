package com.study.principles.invariants.record;

import java.util.List;

/**
 * TRAP: a record that only <i>looks</i> immutable.
 *
 * <h2>Problem: shallow immutability</h2>
 * The field {@code failedTests} is {@code final}, but {@code final} freezes the <b>reference</b>,
 * not the {@link List} it points to. There are two ways to change the "immutable" record:
 * <ol>
 *   <li><b>From outside:</b> the caller keeps a reference to the list it passed into the constructor
 *       and modifies it later — the record changes too.</li>
 *   <li><b>Through the accessor:</b> {@code failedTests()} returns the same mutable list, so
 *       {@code result.failedTests().clear()} empties the record.</li>
 * </ol>
 * Consequences: {@code hashCode()} changes over time (see the {@code hashkey} example), snapshots
 * taken "at the end of the suite" silently change afterwards.
 *
 * @param failedTests names of failed tests — stored by reference, NOT copied
 * @see SafeResult the fixed version
 */
public record ShallowResult(List<String> failedTests) {
}
