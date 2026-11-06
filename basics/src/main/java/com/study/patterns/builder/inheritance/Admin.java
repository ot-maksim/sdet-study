package com.study.patterns.builder.inheritance;

import static java.lang.String.format;

public class Admin extends User {
    private final int level;

    private Admin(AdminBuilder b) {
        super(b);
        this.level = b.level;
    }

    public int getLevel() {
        return level;
    }

    // Not builder(): a static Admin.builder() would hide User.builder(),
    // which is allowed only if AdminBuilder is a subtype of UserBuilder.
    // Now they are siblings (both extend Builder), hence a different name.
    public static AdminBuilder adminBuilder() {
        return new AdminBuilder();
    }

    /** Concrete builder for Admin: T = AdminBuilder. */
    public static class AdminBuilder extends User.Builder<AdminBuilder> {
        private int level;

        public AdminBuilder level(int level) {
            this.level = level;
            return this;
        }

        @Override
        protected AdminBuilder self() {
            return this;
        }

        @Override
        public Admin build() {
            return new Admin(this);
        }
    }

    @Override
    public String toString() {
        return format("Admin {name: %s, surName: %s, level: %d}", super.getName(), super.getSurName(), level);
    }
}
