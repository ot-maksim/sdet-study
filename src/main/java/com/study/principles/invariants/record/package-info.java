/**
 * Using {@code record} to protect invariants — and where it stops protecting them.
 *
 * <h2>What a record gives for free</h2>
 * <ul>
 *   <li>{@code private final} fields, no setters → state cannot change after creation;</li>
 *   <li>{@code final} class → no mutable subclass can be substituted;</li>
 *   <li>no extra instance fields → no hidden state outside the components;</li>
 *   <li>generated {@code equals}/{@code hashCode}/{@code toString} over all components.</li>
 * </ul>
 *
 * <h2>What you still have to do</h2>
 * <ul>
 *   <li><b>Validate</b> in the compact constructor — immutability keeps a valid object valid, but
 *       does not stop you from creating an invalid one. See
 *       {@link com.study.principles.invariants.record.DateRange}.</li>
 *   <li><b>Copy mutable components</b> — {@code final} freezes the <i>reference</i>, not the object it
 *       points to. A record is fully immutable only if all its components are immutable. See
 *       {@link com.study.principles.invariants.record.ShallowResult} (trap) vs
 *       {@link com.study.principles.invariants.record.SafeResult} (fix).</li>
 * </ul>
 *
 * <h2>Where records fit in a test framework</h2>
 * Good fit: test data and keys, configuration, value objects, DB rows, API DTOs (Jackson 2.12+).
 * Poor fit: objects with a lifecycle and behaviour (browser session, DB connection, page objects),
 * JPA entities, anything that needs class inheritance.
 */
package com.study.principles.invariants.record;
