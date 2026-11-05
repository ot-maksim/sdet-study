package com.study.init.patterns.builder;
/*
Builder with a mutable result
The object is a plain class with setters (often a DTO for JSON serialization); the builder is just a convenient way to fill it.

Here the builder gives no guarantees, only readability. This is exactly what Lombok generates for @Data + @Builder.
For API tests this is fine: negative cases sometimes need exactly a "broken" request.
 */

public class CreateUserRequest {
    private String name;
    private String email;

    public CreateUserRequest() {}            // required by Jackson
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
