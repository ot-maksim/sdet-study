package com.study.init.patterns.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.util.Arrays.asList;

/*
Билдер Блоха с неизменяемым результатом
Это тот, что был выше. Чуть доработаю: обязательные поля, валидация и защитная копия коллекции.
Что делает объект неизменяемым: final-поля, приватный конструктор, нет сеттеров, List.copyOf вместо ссылки на изменяемый список.
Без последнего пункта объект неизменяемый только формально: снаружи можно было бы поменять список.

toBuilder() очень полезен в тестовых данных: берёшь «валидного пользователя по умолчанию» и меняешь одно поле для негативного кейса.
 */

public final class User {
    private final String name;          // обязательное
    private final String email;         // обязательное
    private final int age;              // необязательное
    private final List<String> roles;   // необязательное

    private User(Builder b) {
        this.name = b.name;
        this.email = b.email;
        this.age = b.age;
        this.roles = List.copyOf(b.roles);   // неизменяемая копия
    }

    public static Builder builder(String name, String email) {
        return new Builder(name, email);
    }

    public Builder toBuilder() {             // копия с изменениями
        return new Builder(name, email).age(age).roles(roles);
    }

    // только геттеры, сеттеров нет
    public String getName() {
        return name;
    }

    public List<String> getRoles() {
        return roles;
    }

    @Override
    public String toString() {
        return "User{" +
                "name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", age=" + age +
                ", roles=" + roles +
                '}';
    }

    public static final class Builder {
        private final String name;
        private final String email;
        private int age;
        private List<String> roles = new ArrayList<>();

        private Builder(String name, String email) {
            this.name = Objects.requireNonNull(name);
            this.email = Objects.requireNonNull(email);
        }

        public Builder age(int age) {
            this.age = age;
            return this;
        }

        public Builder roles(List<String> roles) {
            this.roles = new ArrayList<>(roles);
            return this;
        }

        public Builder role(String role) {
            this.roles.add(role);
            return this;
        }

        public User build() {
            if (age < 0) throw new IllegalStateException("age must be >= 0");
            return new User(this);
        }
    }

    public static void main(String[] args) {
        User admin = User.builder("tom", "tom@test.com").age(30).roles(asList("A", "B")).role("ADMIN").build();
        User guest = admin.toBuilder().age(31).build();

        System.out.println(admin);
        System.out.println(guest);
    }
}
