package com.study.principles.dip.good;

import com.study.principles.dip.User;

/*
 DIP applied. The test depends only on UserPrecondition; it has no idea about JDBC or HTTP.

 Dependency arrows:
                 OrderTest ──► UserPrecondition  (interface, owned by the test layer)
                                  ▲      ▲      ▲
                   DbUserPrecondition  Api...  InMemory...

 Note: DI (constructor) is the TECHNIQUE, DIP is the PRINCIPLE.
 Injecting a concrete JdbcUserCreator via the constructor would be DI without DIP.
 */
public class OrderTest {
    private final UserPrecondition users;

    public OrderTest(UserPrecondition users) {
        this.users = users;
    }

    void userCanPlaceOrder() {
        User user = users.existingUser("bob");
        System.out.println("  placing order for " + user);
    }
}
