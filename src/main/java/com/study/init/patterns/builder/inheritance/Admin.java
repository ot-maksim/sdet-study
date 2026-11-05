package com.study.init.patterns.builder.inheritance;

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

    public static AdminBuilder builder() {
        return new AdminBuilder();
    }

    public static class AdminBuilder extends UserBuilder<AdminBuilder> {
        private int level;

        public AdminBuilder level(int level) {
            this.level = level;
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
