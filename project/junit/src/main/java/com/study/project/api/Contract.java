package com.study.project.api;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.Response;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The SUT's OpenAPI file as a check of a <b>response</b>: status declared for the operation, body schema (required
 * fields, types, formats, enums of error codes), required headers. It says what breaks the contract and decides
 * nothing: the contract tests assert on it ({@link ApiResponseAssert#conformsTo}), {@link ContractFilter} only
 * reports it for every other call (ADR 0015).
 *
 * <p>Requests are not checked, on purpose: negative tests send what the contract forbids (age 17, a missing field)
 * to see the SUT reject it. The generated client already keeps the requests' shape right.
 */
public final class Contract {

    private final OpenApiInteractionValidator validator;

    private Contract(OpenApiInteractionValidator validator) {
        this.validator = validator;
    }

    /** The file copied next to the classes by the build ({@code contract/openapi.yaml}): the one the client was generated from. */
    public static Contract fromClasspath(String resource) {
        try (InputStream in = Contract.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("No " + resource + " on the class path (the module's pom.xml copies it from sut/)");
            }
            return fromSpecification(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Contract fromSpecification(String openApiYaml) {
        return new Contract(OpenApiInteractionValidator.createForInlineApiSpecification(openApiYaml).build());
    }

    /** What in this response breaks the contract, one line per problem; empty if it is as the contract says. */
    public List<String> violations(ApiResponse response) {
        SimpleResponse.Builder received = SimpleResponse.Builder.status(response.status());
        if (response.body() != null && !response.body().isEmpty()) {
            received.withBody(response.body());
        }
        response.allHeaders().stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey, LinkedHashMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())))
                .forEach((name, values) -> received.withHeader(name, values));
        return violations(response.method(), response.path(), received.build());
    }

    /** {@code path} may carry a query: the operation is found by the path alone. */
    List<String> violations(String method, String path, Response response) {
        ValidationReport report = validator.validateResponse(URI.create(path).getRawPath(), Request.Method.valueOf(method), response);
        return report.getMessages().stream()
                .filter(message -> message.getLevel() == ValidationReport.Level.ERROR)
                .map(ValidationReport.Message::getMessage)
                .toList();
    }
}
