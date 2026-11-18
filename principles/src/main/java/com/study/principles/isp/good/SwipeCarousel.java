package com.study.principles.isp.good;

/**
 * Needs gestures and reading: here as <b>two role parameters</b> (the caller passes the same device twice).
 * Flexible — the reader could be a different object — but the signature no longer says "the same screen".
 * Compare the intersection type in {@link CookieLogin}.
 */
public final class SwipeCarousel {

    private final Gestures gestures;
    private final Interaction screen;

    public SwipeCarousel(Gestures gestures, Interaction screen) {
        this.gestures = gestures;
        this.screen = screen;
    }

    public String next() {
        gestures.swipe("left");
        return screen.text("slide");
    }
}
