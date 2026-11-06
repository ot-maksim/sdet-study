package com.study.principles.dip;

/** Stable value object: depending on it directly is fine (no abstraction needed). */
public record User(long id, String name) {
}
