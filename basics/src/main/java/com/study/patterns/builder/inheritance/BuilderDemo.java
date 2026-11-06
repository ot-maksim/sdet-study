package com.study.patterns.builder.inheritance;

public class BuilderDemo {
    public static void main(String[] args) {
        User user = User.builder().name("bob").surName("old").build();
        System.out.println("user: " + user);

        // .name() returned AdminBuilder (T = AdminBuilder), so .level() is available
        Admin admin = Admin.adminBuilder().name("tom").surName("very old").level(5).build();
        System.out.println("admin: " + admin);

//        User.builder().
//        Admin.adminBuilder().
    }
}
