package com.study.init.patterns.builder.inheritance;

public class Admin extends User {
    private final int level;

    private Admin(Builder b) { super(b); this.level = b.level; }

    public int getLevel() { return level; }

    public static Builder adminBuilder() { return new Builder(); }

    public static class Builder extends User.Builder<Builder> {
        private int level;

        public Builder level(int level) { this.level = level; return this; }
        @Override public Admin build() { return new Admin(this); }
    }
}
