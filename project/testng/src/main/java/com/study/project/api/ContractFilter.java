package com.study.project.api;

import com.atlassian.oai.validator.restassured.RestAssuredResponse;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import java.net.URI;
import java.util.List;
import java.util.function.Consumer;

/**
 * Checks every response of the run against the {@link Contract} and <b>reports</b> what breaks it; it never fails
 * the call (ADR 0015). A functional test is judged by its own assertions: a drift in a field it does not look at
 * must not hide whether the feature works. The verdict on the contract belongs to the contract tests
 * ({@code UsersContractTest}); here a violation is a hint in the report of the test that met it.
 */
public final class ContractFilter implements Filter {

    private final Contract contract;
    private final Consumer<ContractViolation> observer;

    /** @param observer gets each violation (the root hands the report's); called on the calling thread */
    public ContractFilter(Contract contract, Consumer<ContractViolation> observer) {
        this.contract = contract;
        this.observer = observer;
    }

    @Override
    public Response filter(FilterableRequestSpecification request, FilterableResponseSpecification responseSpec, FilterContext context) {
        Response response = context.next(request, responseSpec);
        String path = URI.create(request.getURI()).getRawPath();
        List<String> problems = contract.violations(request.getMethod(), path, RestAssuredResponse.of(response));
        if (!problems.isEmpty()) {
            observer.accept(new ContractViolation(request.getMethod() + " " + path + " -> " + response.statusCode(), problems));
        }
        return response;
    }
}
