package com.study.principles.ocp;

public record Viewport(int width, int height, boolean mobile) {

    public static final Viewport DESKTOP = new Viewport(1920, 1080, false);
    public static final Viewport PHONE = new Viewport(390, 844, true);
}
