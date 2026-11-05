package com.study.init.patterns.builder;
/*
Билдер с изменяемым результатом
Объект — обычный класс с сеттерами (часто DTO для сериализации в JSON), билдер — просто удобный способ его заполнить.

Здесь билдер не дает гарантий, только читаемость. Это ровно то, что генерирует Lombok при @Data + @Builder.
Для тестов API это нормально: в негативных кейсах иногда как раз нужно собрать «сломанный» запрос.
 */

public class CreateUserRequest {
    private String name;
    private String email;

    public CreateUserRequest() {}            // нужен Jackson
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final CreateUserRequest request = new CreateUserRequest();

        public Builder name(String name) { request.setName(name); return this; }
        public Builder email(String email) { request.setEmail(email); return this; }
        public CreateUserRequest build() { return request; }
    }

    @Override
    public String toString() {
        return "CreateUserRequest{" +
                "name='" + name + '\'' +
                ", email='" + email + '\'' +
                '}';
    }

    public static void main(String[] args) {
        CreateUserRequest request = CreateUserRequest.builder().name("tom").email("bad-email").build();
        System.out.println(request);
        request.setEmail(null);
        System.out.println(request);
    }
}
