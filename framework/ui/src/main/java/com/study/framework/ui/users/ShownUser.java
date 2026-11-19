package com.study.framework.ui.users;

/** A user as the list shows it (table row on desktop, card on a phone — the same data). */
public record ShownUser(long id, String name, String email, int age, String country) {
}
