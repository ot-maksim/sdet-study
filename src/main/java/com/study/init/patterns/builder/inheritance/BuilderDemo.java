package com.study.init.patterns.builder.inheritance;

public class BuilderDemo {
    public static void main(String[] args) {
        User user = User.builder().name("bob").build();
        System.out.println("user: " + user.getName());

        // .name() вернул Admin.Builder, поэтому .level() доступен
        Admin admin = Admin.adminBuilder().name("tom").level(5).build();
        System.out.println("admin: " + admin.getName() + ", level " + admin.getLevel());
    }
}
