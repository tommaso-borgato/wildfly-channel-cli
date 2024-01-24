package org.jboss.set.channelreports;

import org.assertj.core.api.Assertions;
import org.jboss.set.channelreports.utils.VersionUtils;
import org.junit.jupiter.api.Test;

public class VersionUtilsTestCase {

    @Test
    public void qualifierTest() {
        Assertions.assertThat(VersionUtils.qualifier("1.2.3")).isEqualTo("");
        Assertions.assertThat(VersionUtils.qualifier("1.2.3.redhat")).isEqualTo("redhat");
        Assertions.assertThat(VersionUtils.qualifier("1.2.3.redhat-00001")).isEqualTo("redhat-00001");
        Assertions.assertThat(VersionUtils.qualifier("redhat-00001")).isEqualTo("redhat-00001");
    }


    @Test
    public void firstQualifierSegmentTest() {
        Assertions.assertThat(VersionUtils.firstQualifierSegment("1.2.3")).isEqualTo("");
        Assertions.assertThat(VersionUtils.firstQualifierSegment("1.2.3.redhat")).isEqualTo("redhat");
        Assertions.assertThat(VersionUtils.firstQualifierSegment("1.2.3.redhat-00001")).isEqualTo("redhat");
        Assertions.assertThat(VersionUtils.firstQualifierSegment("redhat-00001")).isEqualTo("redhat");
    }
}
