package com.study.init.patterns.builder;
/*
Билдер с изменяемым результатом
Объект — обычный класс с сеттерами (часто DTO для сериализации в JSON), билдер — просто удобный способ его заполнить.

Вариант без отдельного билдера — fluent-сеттеры, которые возвращают this. Формально это не билдер,
но решает ту же задачу для изменяемых объектов:
public CreateUserRequest withName(String name) { this.name = name; return this; }
 */

public class CreateUserRequestFluent {
    private String name;
    private String email;

    public CreateUserRequestFluent() {
    }            // нужен Jackson

    public String getName() {
        return name;
    }

    public CreateUserRequestFluent withName(String name) {
        this.name = name;
        return this;
    }

    public String getEmail() {
        return email;
    }

    public CreateUserRequestFluent withEmail(String email) {
        this.email = email;
        return this;
    }

    @Override
    public String toString() {
        return "CreateUserRequestFluent{" +
                "name='" + name + '\'' +
                ", email='" + email + '\'' +
                '}';
    }

    public static void main(String[] args) {
        CreateUserRequestFluent request = new CreateUserRequestFluent().withName("tom").withEmail("bad-email");
        System.out.println(request);
        request.withEmail(null);
        System.out.println(request);
    }
}
