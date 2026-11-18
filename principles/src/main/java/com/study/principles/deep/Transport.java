package com.study.principles.deep;

/** The wire. In a real framework: RestAssured / java.net.http; here: {@link FakeUsersBackend}. */
@FunctionalInterface
public interface Transport {

    HttpResponse send(HttpRequest request);
}
