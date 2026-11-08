package com.study.principles.encapsulation;

import com.study.principles.encapsulation.good.TestConfig;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.constraints.StringLength;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "However the base URL and the path are spelled, the result is the same well-formed URL" is a
 * for-all statement — exactly what property-based testing checks. The bad design has no single
 * place where such a property could even be stated: every client builds URLs its own way.
 */
class EndpointPropertyTest {

    @Property
    void endpointIsIndependentOfSlashSpelling(
            @ForAll @Size(min = 1, max = 4) List<@AlphaChars @StringLength(min = 1, max = 8) String> segments,
            @ForAll boolean withContextPath,
            @ForAll @IntRange(min = 0, max = 2) int trailingSlashesInBase,
            @ForAll @IntRange(min = 0, max = 2) int leadingSlashesInPath) {

        String root = withContextPath ? "http://localhost:7070/sut" : "http://localhost:7070";
        String path = String.join("/", segments);
        TestConfig config = TestConfig.from(Map.of("baseUrl", root + "/".repeat(trailingSlashesInBase)));

        String url = config.endpoint("/".repeat(leadingSlashesInPath) + path).toString();

        assertThat(url).isEqualTo(root + "/" + path);
    }
}
