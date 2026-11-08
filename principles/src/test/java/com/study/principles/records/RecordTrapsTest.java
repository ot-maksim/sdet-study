package com.study.principles.records;

import com.study.principles.records.traps.bad.Attachment;
import com.study.principles.records.traps.bad.Credentials;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RecordTrapsTest {
    private static final byte[] PDF = "%PDF-1.7 report".getBytes(StandardCharsets.US_ASCII);

    @Test
    void badCredentialsPrintThePassword() {
        assertThat(new Credentials("admin", "s3cret")).hasToString("Credentials[login=admin, password=s3cret]");
    }

    @Test
    void goodCredentialsMaskThePasswordButKeepValueEquality() {
        var creds = new com.study.principles.records.traps.good.Credentials("admin", "s3cret");

        assertThat(creds.toString()).doesNotContain("s3cret");
        assertThat(creds).isEqualTo(new com.study.principles.records.traps.good.Credentials("admin", "s3cret"));
    }

    @Test
    void badAttachmentWithSameContentIsNotEqual() {
        var uploaded = new Attachment("report.pdf", PDF);
        var downloaded = new Attachment("report.pdf", PDF.clone());

        assertThat(downloaded).isNotEqualTo(uploaded);           // a correct download "fails"
        assertThat(uploaded.toString()).contains("content=[B@");  // and the message is useless
    }

    @Test
    void badAttachmentChangesThroughTheAccessor() {
        var attachment = new Attachment("report.pdf", PDF.clone());

        attachment.content()[0] = 0;

        assertThat(attachment.content()[0]).isZero();
    }

    @Test
    void goodAttachmentComparesByContentAndIsImmutable() {
        byte[] source = PDF.clone();
        var uploaded = new com.study.principles.records.traps.good.Attachment("report.pdf", source);
        var downloaded = new com.study.principles.records.traps.good.Attachment("report.pdf", PDF.clone());

        source[0] = 0;
        uploaded.content()[1] = 0;

        assertThat(downloaded).isEqualTo(uploaded).hasSameHashCodeAs(uploaded);
        assertThat(uploaded.toString()).contains("size=15", "255044462d");   // "%PDF-" in hex
    }
}
