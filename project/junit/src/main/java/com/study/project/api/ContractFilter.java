package com.study.project.api;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.restassured.RestAssuredResponse;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Checks every <b>response</b> against the SUT's OpenAPI file: status declared for the operation, body schema
 * (required fields, types, formats, enums of error codes), required headers. A drift between the SUT and its
 * contract fails the test that saw it, with the report and the exchange — not a consumer in production.
 *
 * <p>Requests are not checked, on purpose: negative tests send what the contract forbids (age 17, a missing field)
 * to see the SUT reject it. The generated client already keeps the requests' shape right.
 */
public final class ContractFilter implements Filter {

    private final OpenApiInteractionValidator validator;

    private ContractFilter(OpenApiInteractionValidator validator) {
        this.validator = validator;
    }

    /** The file copied next to the classes by the build ({@code contract/openapi.yaml}): the one the client was generated from. */
    public static ContractFilter fromClasspath(String resource) {
        try (InputStream in = ContractFilter.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("No " + resource + " on the class path (the module's pom.xml copies it from sut/)");
            }
            return fromSpecification(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static ContractFilter fromSpecification(String openApiYaml) {
        return new ContractFilter(OpenApiInteractionValidator.createForInlineApiSpecification(openApiYaml).build());
    }

    @Override
    public Response filter(FilterableRequestSpecification request, FilterableResponseSpecification responseSpec, FilterContext context) {
        Response response = context.next(request, responseSpec);
        ValidationReport report = validator.validateResponse(URI.create(request.getURI()).getPath(),
                Request.Method.valueOf(request.getMethod()), RestAssuredResponse.of(response));
        if (report.hasErrors()) {
            throw new Violation(report.getMessages().stream()
                    .filter(message -> message.getLevel() == ValidationReport.Level.ERROR)
                    .map(message -> "  - " + message.getMessage())
                    .collect(Collectors.joining("\n")));
        }
        return response;
    }

    /** Thrown inside the filter chain; {@link ApiTransport} turns it into an {@link ApiException} with the exchange. */
    static final class Violation extends RuntimeException {

        Violation(String message) {
            super(message);
        }
    }
}
