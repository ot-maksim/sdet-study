package com.study.project.report;

import com.study.project.ui.users.FormError;
import com.study.project.ui.users.ShownUser;
import com.study.project.ui.users.UserForm;
import com.study.project.ui.users.UsersScreen;
import java.util.Map;
import java.util.Optional;

/**
 * Decorator over the screen interface: every user action is a step, for the web page today and for a native
 * screen tomorrow — it wraps the interface, not one implementation.
 *
 * <p>The cost a decorator always has: one method per method of the interface, and a new method on the interface
 * is a compile error here until it is decided how to report it. The trap:
 * {@link #open()} returns <b>this</b>, not what the delegate returns, or a fluent chain leaves the decorator after
 * the first call and the rest of the test is reported as nothing.
 */
public final class ReportingUsersScreen implements UsersScreen {

    private final UsersScreen delegate;

    public ReportingUsersScreen(UsersScreen delegate) {
        this.delegate = delegate;
    }

    @Override
    public UsersScreen open() {
        ReportSteps.step("screen: open users", Map.of(), step -> delegate.open());
        return this;
    }

    @Override
    public ShownUser create(UserForm user) {
        return ReportSteps.step("screen: create " + user.email(), Map.of("form", user), step -> delegate.create(user));
    }

    @Override
    public FormError createRejected(UserForm user) {
        return ReportSteps.step("screen: create (rejected) " + user.email(), Map.of("form", user),
                step -> delegate.createRejected(user));
    }

    @Override
    public Optional<ShownUser> find(String email) {
        return ReportSteps.step("screen: find " + email, Map.of(), step -> delegate.find(email));
    }

    @Override
    public void delete(String email) {
        ReportSteps.step("screen: delete " + email, Map.of(), step -> {
            delegate.delete(email);
            return null;
        });
    }

    @Override
    public Optional<String> notice() {
        return ReportSteps.step("screen: read the notice", Map.of(), step -> delegate.notice());
    }
}
