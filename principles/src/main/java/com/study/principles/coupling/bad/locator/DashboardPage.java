package com.study.principles.coupling.bad.locator;

public class DashboardPage {

    public String greeting() {
        return DriverManager.get().text("greeting");
    }
}
