package com.study.principles.dip.good;

import com.study.principles.dip.User;

import java.util.ArrayList;
import java.util.List;

/** Detail #3: a fake for unit-testing the framework itself — possible only thanks to DIP. */
public class InMemoryUserPrecondition implements UserPrecondition {
    private final List<User> created = new ArrayList<>();

    @Override
    public User existingUser(String name) {
        User user = new User(created.size() + 1, name);
        created.add(user);
        System.out.println("  [memory] created " + user);
        return user;
    }

    public List<User> created() {
        return List.copyOf(created);
    }
}
