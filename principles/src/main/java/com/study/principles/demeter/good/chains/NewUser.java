package com.study.principles.demeter.good.chains;

import java.util.Objects;

/**
 * NOT a violation: {@code NewUser.builder().name(..).email(..).admin().build()} is a long chain, but every
 * call returns the same object ({@link Builder}) — you talk to one friend repeatedly, you do not walk
 * into strangers. Same for {@code StringBuilder}, {@code Stream}, AssertJ's {@code assertThat(..)} chains
 * and RestAssured's {@code given().when().then()}: DSLs designed to be chained.
 */
public record NewUser(String name, String email, boolean admin) {

    public NewUser {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(email, "email");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name = "Test User";
        private String email = "user@test.io";
        private boolean admin;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder admin() {
            this.admin = true;
            return this;
        }

        public NewUser build() {
            return new NewUser(name, email, admin);
        }
    }
}
