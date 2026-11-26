package com.study.project.tests;

import static com.study.project.api.ApiResponseAssert.assertThat;

import com.study.project.api.Contract;
import com.study.project.api.SutHealth;
import com.study.project.api.users.User;
import com.study.project.api.users.UserApi;
import com.study.project.assembly.Groups;
import com.study.project.domain.UserSteps;
import org.testng.annotations.Test;

/**
 * One test per response the contract describes (operation × status × shape of the body), checked against
 * {@code openapi.yaml} and nothing else (ADR 0015). What the response means (which error code, the stored values) is
 * the functional tests'; a contract test fails only when the SUT and its contract disagree.
 *
 * <p>The status is asserted first: it picks the response of the operation the body is checked against, and shows
 * that the test reached the branch it names. One representative per shape: every validation code has the same
 * {@code Error} body, so {@code age 17} stands for all of them. Malformed JSON and an id that is not a number are
 * separate: the SUT answers them on another path (parsing), where a drift of the body is most likely.
 */
@Test(groups = Groups.CONTRACT)
public class UsersContractTest {

    @Test
    public void healthIsUp(SutHealth health, Contract contract) {
        assertThat(health.check()).hasStatus(200).conformsTo(contract);
    }

    /** {@code de}: the response must carry the normalized country the schema's pattern asks for; {@code Location} is required. */
    @Test
    public void createdUser(UserSteps users, Contract contract) {
        assertThat(users.tryRegisterThroughApi(users.aNewUser().withCountry("de"))).hasStatus(201).conformsTo(contract);
    }

    @Test
    public void invalidUser(UserSteps users, Contract contract) {
        assertThat(users.tryRegisterThroughApi(users.aNewUser().withAge(17))).hasStatus(400).conformsTo(contract);
    }

    @Test
    public void malformedJson(UserApi api, Contract contract) {
        assertThat(api.tryCreate("{\"name\":")).hasStatus(400).conformsTo(contract);
    }

    @Test
    public void takenEmail(UserSteps users, Contract contract) {
        User existing = users.existing();

        assertThat(users.tryRegisterThroughApi(users.aNewUser().withEmail(existing.email()))).hasStatus(409).conformsTo(contract);
    }

    @Test
    public void allUsers(UserSteps users, UserApi api, Contract contract) {
        users.existing();
        users.existing();

        assertThat(api.tryAll()).hasStatus(200).conformsTo(contract);
    }

    @Test
    public void userFoundByEmail(UserSteps users, UserApi api, Contract contract) {
        User existing = users.existing();

        assertThat(api.tryFindByEmail(existing.email())).hasStatus(200).conformsTo(contract);
    }

    /** An empty array, not a 404 or {@code null}. */
    @Test
    public void noUserFoundByEmail(UserSteps users, UserApi api, Contract contract) {
        assertThat(api.tryFindByEmail(users.aNewUser().email())).hasStatus(200).conformsTo(contract);
    }

    @Test
    public void oneUser(UserSteps users, UserApi api, Contract contract) {
        User existing = users.existing();

        assertThat(api.tryGet(existing.id())).hasStatus(200).conformsTo(contract);
    }

    @Test
    public void invalidIdOnGet(UserApi api, Contract contract) {
        assertThat(api.tryGet("abc")).hasStatus(400).conformsTo(contract);
    }

    @Test
    public void unknownUserOnGet(UserSteps users, UserApi api, Contract contract) {
        assertThat(api.tryGet(aDeletedUserId(users, api))).hasStatus(404).conformsTo(contract);
    }

    @Test
    public void deletedUser(UserSteps users, UserApi api, Contract contract) {
        User existing = users.existing();

        assertThat(api.tryDelete(existing.id())).hasStatus(204).conformsTo(contract);
    }

    @Test
    public void invalidIdOnDelete(UserApi api, Contract contract) {
        assertThat(api.tryDelete("abc")).hasStatus(400).conformsTo(contract);
    }

    @Test
    public void unknownUserOnDelete(UserSteps users, UserApi api, Contract contract) {
        assertThat(api.tryDelete(aDeletedUserId(users, api))).hasStatus(404).conformsTo(contract);
    }

    /** An id the SUT gave out and no longer has: the cleanup of the precondition finds it gone, which is fine. */
    private static long aDeletedUserId(UserSteps users, UserApi api) {
        long id = users.existing().id();
        api.delete(id);
        return id;
    }
}
