package com.study.principles.isp.good;

/** Role with one method — so a fake is a lambda: {@code name -> { taken.add(name); return name; }}. */
@FunctionalInterface
public interface Screenshots {

    String screenshot(String name);
}
