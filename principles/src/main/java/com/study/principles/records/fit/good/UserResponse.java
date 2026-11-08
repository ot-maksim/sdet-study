package com.study.principles.records.fit.good;

/**
 * GOOD: the same DTO as a record.
 *
 * <h2>Why a record fits here</h2>
 * A response DTO <i>is</i> its data: there is nothing to hide and no behaviour to protect. The record
 * states exactly that, and the compiler derives everything else from the component list:
 * <ul>
 *   <li>{@code equals}/{@code hashCode}/{@code toString} cover <b>all</b> components — adding {@code role}
 *       to the header updates all three at once, so {@code isEqualTo} cannot silently ignore it;</li>
 *   <li>AssertJ's failure message prints every component, so a diff is visible without a debugger;</li>
 *   <li>no setters → an expected value cannot be changed after it was built or put into a {@code Set}.</li>
 * </ul>
 * Jackson 2.12+ (de)serializes records through the canonical constructor; RestAssured's
 * {@code .as(UserResponse.class)} works the same way.
 *
 * @param id    user id
 * @param login login
 * @param email email
 * @param role  role, e.g. {@code ADMIN}
 * @see com.study.principles.records.fit.bad.UserResponse the hand-written version
 */
public record UserResponse(long id, String login, String email, String role) {
}
