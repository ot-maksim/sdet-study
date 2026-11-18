package com.study.principles.isp.good;

public interface AppLifecycle {

    void installApp(String path);

    void launchApp(String bundleId);
}
