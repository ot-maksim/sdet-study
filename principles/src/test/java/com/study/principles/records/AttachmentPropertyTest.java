package com.study.principles.records;

import com.study.principles.records.traps.good.Attachment;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;

import static org.assertj.core.api.Assertions.assertThat;

/** The equals/hashCode contract for an array component, stated once for any content. */
class AttachmentPropertyTest {

    @Property
    void equalContentMeansEqualAttachments(@ForAll @AlphaChars @StringLength(min = 1, max = 12) String name,
                                           @ForAll byte[] content) {
        var a = new Attachment(name, content);
        var b = new Attachment(name, content.clone());

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Property
    void generatedEqualsNeverMatchesDistinctArrays(@ForAll byte[] content) {
        var a = new com.study.principles.records.traps.bad.Attachment("f", content);
        var b = new com.study.principles.records.traps.bad.Attachment("f", content.clone());

        assertThat(a).isNotEqualTo(b);
    }
}
