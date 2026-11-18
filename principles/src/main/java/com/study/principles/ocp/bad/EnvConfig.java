package com.study.principles.ocp.bad;

/**
 * Environments as code: the values differ, the behaviour does not — still every new environment is an edit
 * in several switches. And the safety check is written as "not prod": a new {@code preprod}, as precious
 * as prod, is open to destructive tests the moment its URLs are added.
 */
public final class EnvConfig {

    private EnvConfig() {
    }

    public static String baseUrl(String env) {
        return switch (env) {
            case "qa" -> "https://qa.shop.test";
            case "staging" -> "https://staging.shop.test";
            case "preprod" -> "https://preprod.shop.test";   // added
            case "prod" -> "https://shop.example";
            default -> throw new IllegalArgumentException("unknown env: " + env);
        };
    }

    public static String dbUrl(String env) {
        return switch (env) {
            case "qa" -> "jdbc:postgresql://qa-db:5432/shop";
            case "staging" -> "jdbc:postgresql://staging-db:5432/shop";
            case "preprod" -> "jdbc:postgresql://preprod-db:5432/shop";   // added
            case "prod" -> "jdbc:postgresql://prod-db:5432/shop";
            default -> throw new IllegalArgumentException("unknown env: " + env);
        };
    }

    public static boolean destructiveAllowed(String env) {
        return !env.equals("prod");
    }
}
