package com.study.principles.lsp.fakes;

import java.util.Optional;

/**
 * Contract — every implementation, the JDBC one and every fake, must honour it:
 * <ol>
 *   <li>{@code insert} assigns a new id and returns the stored user; a second user with the same e-mail
 *       throws {@link DuplicateEmailException} (unique constraint).</li>
 *   <li>Returned objects are <b>detached copies</b>: changing one changes nothing stored until {@code update}.</li>
 *   <li>{@code update} stores the user's current state; {@code findByEmail} returns it.</li>
 * </ol>
 */
public interface UserRepository {

    User insert(User newUser);

    void update(User user);

    Optional<User> findByEmail(String email);
}
