package com.study.sut.users;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Storage of users. Contract (checked for every implementation by {@code UserRepositoryContract}):
 * ids are assigned by the repository and never reused; emails are unique ({@code insert} of a taken
 * email throws {@link UserRejectedException} with code {@code email_taken}); reads return what was
 * stored, not a shared instance; {@code findAll} is ordered by id.
 */
public interface UserRepository {

    User insert(ValidUser user, Instant createdAt);

    Optional<User> findById(long id);

    Optional<User> findByEmail(String email);

    List<User> findAll();

    /** @return {@code true} if a user was deleted */
    boolean delete(long id);
}
