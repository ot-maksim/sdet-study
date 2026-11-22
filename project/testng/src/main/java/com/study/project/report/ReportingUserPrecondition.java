package com.study.project.report;

import com.study.project.api.users.NewUser;
import com.study.project.api.users.User;
import com.study.project.domain.UserPrecondition;
import java.util.Map;

/**
 * Decorator: the precondition as a step, named by its channel. {@link UserPrecondition} is an interface the root
 * already chooses an implementation of — the same seam (DIP) that lets the channel change lets reporting wrap it,
 * and the domain never learns about Allure.
 */
public final class ReportingUserPrecondition implements UserPrecondition {

    private final UserPrecondition delegate;
    private final String channel;

    public ReportingUserPrecondition(UserPrecondition delegate, String channel) {
        this.delegate = delegate;
        this.channel = channel;
    }

    @Override
    public User create(NewUser user) {
        return ReportSteps.step("precondition: a user through " + channel, Map.of("email", user.email()),
                step -> delegate.create(user));
    }
}
