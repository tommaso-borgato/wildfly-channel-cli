package org.jboss.set.channelreports;

import org.assertj.core.api.Assertions;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.wildfly.channel.Stream;

import java.util.ArrayList;
import java.util.Collection;

public class MergeManifestsCommandTestCase {

    @Test
    public void testMergeStreams() {
        ArrayList<Stream> streams1 = new ArrayList<>();
        streams1.add(new Stream("g1", "a1", "v1"));
        streams1.add(new Stream("g2", "a2", "v2"));

        ArrayList<Stream> streams2 = new ArrayList<>();
        streams2.add(new Stream("g2", "a2", "overridden"));
        streams2.add(new Stream("g3", "a3", "v3"));

        Collection<Stream> mergedStreams = MergeManifestsCommand.mergeStreams(streams1, streams2);
        Assertions.assertThat(mergedStreams).extracting("groupId", "artifactId", "version").containsExactly(
                Tuple.tuple("g1", "a1", "v1"),
                Tuple.tuple("g2", "a2", "overridden"),
                Tuple.tuple("g3", "a3", "v3")
        );
    }
}
