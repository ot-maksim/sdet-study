package com.study.sut.users;

/**
 * What the service tells the outside world after a change. A port: the use cases do not know whether anybody
 * listens, the adapter (Kafka, nothing) is chosen in {@code SutMain}.
 *
 * <p>Called <b>after</b> the change is stored, without a transaction spanning both: if publishing fails, the user
 * exists and the event is lost (the dual-write problem; a real service would use an outbox). Implementations must
 * not throw back into the use case — the request already succeeded.
 */
public interface UserEvents {

    void created(User user);

    /** Publishing switched off: the default, so the SUT runs without a broker as in stage 2. */
    static UserEvents none() {
        return user -> {
        };
    }
}
