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

    public UserService(UserRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public User create(NewUser request) {
        ValidUser user = UserRules.validate(request);
        // Postgres keeps microseconds: truncating here makes the response equal to what a later read returns
        return repository.insert(user, clock.instant().truncatedTo(ChronoUnit.MICROS));
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
