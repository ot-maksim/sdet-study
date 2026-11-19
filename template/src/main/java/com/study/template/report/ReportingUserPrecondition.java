package com.study.template.report;

import com.study.framework.api.users.NewUser;
import com.study.framework.api.users.User;
import com.study.framework.domain.UserPrecondition;
import java.util.Map;

/**
 * Decorator: the precondition as a step, named by its channel. Possible without touching stage 2 because
 * {@link UserPrecondition} is an interface the root already chooses an implementation of — the same seam
 * (DIP) that lets the channel change lets reporting wrap it. Compare {@link ReportingAspect}: no seam, no decorator.
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
