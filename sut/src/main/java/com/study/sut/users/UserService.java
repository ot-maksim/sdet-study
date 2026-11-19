package com.study.sut.users;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The use cases behind both the REST API and the HTML page. */
public class UserService {

    private final UserRepository repository;
    private final Clock clock;
    private final UserEvents events;

    public UserService(UserRepository repository, Clock clock) {
        this(repository, clock, UserEvents.none());
    }

    public UserService(UserRepository repository, Clock clock, UserEvents events) {
        this.repository = repository;
        this.clock = clock;
        this.events = events;
    }

    public User create(NewUser request) {
        ValidUser valid = UserRules.validate(request);
        // Postgres keeps microseconds: truncating here makes the response equal to what a later read returns
        User user = repository.insert(valid, clock.instant().truncatedTo(ChronoUnit.MICROS));
        events.created(user);   // after the insert: no event about a user that was not stored
        return user;
    }

    public User get(long id) {
        return repository.findById(id).orElseThrow(() -> UserRejectedException.notFound(id));
    }

    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email.strip().toLowerCase(Locale.ROOT));
    }

    public List<User> all() {
        return repository.findAll();
    }

    public void delete(long id) {
        if (!repository.delete(id)) {
            throw UserRejectedException.notFound(id);
        }
    }
}
