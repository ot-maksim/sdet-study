package com.study.principles.dry.good;

import java.util.LinkedHashMap;
import java.util.Map;

/** The request body as a value: immutable, so no test can change it for another. */
public record NewUser(String name, String email, Integer age, String country) {

    public Map<String, Object> json() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("name", name);
        json.put("email", email);
        json.put("age", age);
        json.put("country", country);
        return json;
    }
}
