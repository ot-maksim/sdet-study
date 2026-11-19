-- Applied on SUT start-up; idempotent. A real service would use migrations (Flyway/Liquibase).
CREATE TABLE IF NOT EXISTS users (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(254) NOT NULL UNIQUE,
    age        INT          NOT NULL CHECK (age >= 18),
    country    CHAR(2)      NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);
