package com.study.project.ui.users;

/** What a person types into the create form; strings, because a form can hold "old" in the age field. */
public record UserForm(String name, String email, String age, String country) {

    public UserForm withAge(String age) {
        return new UserForm(name, email, age, country);
    }

    public UserForm withEmail(String email) {
        return new UserForm(name, email, age, country);
    }
}
