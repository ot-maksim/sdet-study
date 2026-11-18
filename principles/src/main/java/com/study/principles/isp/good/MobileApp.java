package com.study.principles.isp.good;

/** Aggregate for the native side; same rule as {@link Browser}. */
public interface MobileApp extends Interaction, Screenshots, Gestures, AppLifecycle {
}
