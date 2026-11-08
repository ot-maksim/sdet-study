package com.study.principles.records.traps.bad;

/**
 * TRAP: the generated {@code toString} prints every component — including the password.
 *
 * <h2>Where it leaks in a test framework</h2>
 * <ul>
 *   <li>{@code log.info("Logging in as {}", credentials)};</li>
 *   <li>{@code @ParameterizedTest} display names: {@code [1] Credentials[login=admin, password=s3cret]}
 *       ends up in the console, the surefire XML and the HTML report;</li>
 *   <li>AssertJ failure messages and step attachments in reporting tools.</li>
 * </ul>
 *
 * @param login    login
 * @param password password — printed in clear text by the generated {@code toString}
 * @see com.study.principles.records.traps.good.Credentials the fixed version
 */
public record Credentials(String login, String password) {
}
