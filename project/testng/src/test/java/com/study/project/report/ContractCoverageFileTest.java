package com.study.project.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.study.project.api.Contract;
import com.study.project.api.ContractCoverage;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.annotations.Test;

/** The coverage file is written at the end of the run, and never by a context that made no calls. */
public class ContractCoverageFileTest {

    private final Contract contract = Contract.fromClasspath("contract/openapi.yaml");

    @Test
    public void aRunWithoutApiCallsLeavesTheLastReportAlone() throws Exception {
        Path file = Files.createTempDirectory("coverage").resolve("contract-coverage.md");
        Files.writeString(file, "the run's report");

        new ContractCoverageFile(new ContractCoverage(contract), file).close();

        assertThat(file).hasContent("the run's report");
    }

    @Test
    public void theContractListsEveryDeclaredResponse() {
        assertThat(contract.responses()).hasSize(11)
                .startsWith("GET /health 200", "GET /api/users 200", "POST /api/users 201", "POST /api/users 400", "POST /api/users 409")
                .endsWith("DELETE /api/users/{id} 204", "DELETE /api/users/{id} 400", "DELETE /api/users/{id} 404");
    }
}
