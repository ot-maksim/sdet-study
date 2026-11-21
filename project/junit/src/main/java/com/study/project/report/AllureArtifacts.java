package com.study.project.report;

import io.qameta.allure.Allure;
import java.io.ByteArrayInputStream;

/** Artifacts as attachments of the running test in the Allure report. */
public final class AllureArtifacts implements ArtifactListener {

    @Override
    public void artifactCreated(Artifact artifact) {
        Allure.addAttachment(artifact.name(), artifact.mediaType(), new ByteArrayInputStream(artifact.content()),
                "." + artifact.extension());
    }
}
