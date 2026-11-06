/**
 * Invariant violation #2: a mutable object is changed while another object relies on it.
 * <p>
 * {@link java.util.HashMap} has an invariant: an entry is stored in the bucket that matches its
 * key's {@code hashCode()}. If the key is mutated after insertion and the mutated field takes part in
 * {@code hashCode()}, the entry stays in the "old" bucket while lookups go to the "new" one — the
 * entry becomes unreachable. The map did nothing wrong: its invariant was broken by changing an
 * object it depends on.
 * <ul>
 *   <li>{@link com.study.principles.invariants.hashkey.bad.TestUser} — mutable key.</li>
 *   <li>{@link com.study.principles.invariants.hashkey.good.TestUser} — immutable {@code record} key.</li>
 *   <li>{@link com.study.principles.invariants.hashkey.HashKeyDemo} — runnable demonstration.</li>
 * </ul>
 */
package com.study.principles.invariants.hashkey;
