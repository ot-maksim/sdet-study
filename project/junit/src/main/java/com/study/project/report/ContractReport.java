package com.study.project.report;

import com.study.project.api.ContractViolation;
import io.qameta.allure.Allure;
import io.qameta.allure.AllureLifecycle;
import io.qameta.allure.model.Status;
import io.qameta.allure.model.StatusDetails;
import io.qameta.allure.model.StepResult;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A contract violation met by a functional test, as a <b>broken step</b> of that test: visible in the report, while
 * the test keeps the verdict of its own assertions (ADR 0015). The root hands it to the api layer's
 * {@code ContractFilter}, which does not know Allure.
 *
 * <p>Outside a running test (a stand probe) Allure has no test to add the step to and only logs it.
 */
public final class ContractReport implements Consumer<ContractViolation> {

    @Override
    public void accept(ContractViolation violation) {
        AllureLifecycle lifecycle = Allure.getLifecycle();
        String uuid = UUID.randomUUID().toString();
        lifecycle.startStep(uuid, new StepResult()
                .setName("contract violation: " + violation.call())
                .setStatus(Status.BROKEN)
                .setStatusDetails(new StatusDetails().setMessage(violation.toString())));
        lifecycle.stopStep(uuid);
    }
}
