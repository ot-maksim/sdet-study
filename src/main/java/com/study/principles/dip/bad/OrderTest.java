package com.study.principles.dip.bad;

import com.study.principles.dip.User;

/*
 DIP VIOLATION: high-level code (a test) depends directly on a low-level detail.

 Problems:
 1. The test knows HOW a user is created (JDBC, URL, table) — not just THAT it needs one.
 2. Switching to "create via API" means editing every test that does this.
 3. Impossible to substitute a fast fake: `new` is hard-wired inside.
 4. The method name leaks the detail: insertUserRow — vocabulary of the DB, not of the test.

 Dependency arrow:  OrderTest ──► JdbcUserCreator ──► JDBC
 */
public class OrderTest {
    private final JdbcUserCreator creator = new JdbcUserCreator();   // hard-wired detail

    void userCanPlaceOrder() {
        User user = creator.insertUserRow("jdbc:h2:mem:sut", "bob");
        System.out.println("  placing order for " + user);
    }

    public static void main(String[] args) {
        System.out.println("BAD: test is welded to JDBC");
        new OrderTest().userCanPlaceOrder();
    }
}
