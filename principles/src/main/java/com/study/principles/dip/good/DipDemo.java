package com.study.principles.dip.good;

/**
 * Composition Root: the ONLY place that knows concrete classes and wires them together.
 * In a real framework this is a JUnit 5 extension / ParameterResolver or a DI container,
 * and the choice comes from config (e.g. -Dpreconditions=api).
 */
public class DipDemo {
    public static void main(String[] args) {
        String mode = System.getProperty("preconditions", "all");

        if (mode.equals("db") || mode.equals("all")) {
            System.out.println("GOOD: same test, DB preconditions");
            new OrderTest(new DbUserPrecondition("jdbc:h2:mem:sut")).userCanPlaceOrder();
        }
        if (mode.equals("api") || mode.equals("all")) {
            System.out.println("GOOD: same test, API preconditions");
            new OrderTest(new ApiUserPrecondition("http://localhost:7070")).userCanPlaceOrder();
        }
        if (mode.equals("memory") || mode.equals("all")) {
            System.out.println("GOOD: same test, in-memory fake");
            InMemoryUserPrecondition fake = new InMemoryUserPrecondition();
            new OrderTest(fake).userCanPlaceOrder();
            System.out.println("  fake recorded: " + fake.created());
        }
    }
}
