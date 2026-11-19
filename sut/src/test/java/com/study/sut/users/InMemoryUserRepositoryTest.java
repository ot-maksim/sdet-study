package com.study.sut.users;

class InMemoryUserRepositoryTest implements UserRepositoryContract {

    private final UserRepository repository = new InMemoryUserRepository();

    @Override
    public UserRepository repository() {
        return repository;
    }
}
