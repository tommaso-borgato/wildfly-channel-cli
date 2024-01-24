package org.jboss.set.channelreports.report;

import org.apache.commons.lang3.tuple.Pair;
import org.jboss.set.channelreports.report.FormattingReportBuilder;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.wildfly.channel.MavenArtifact;
import org.wildfly.channel.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This is a helper class that generates a formatted report file, which is to be checked visually.
 */
@Disabled // Just for development purposes.
public class FormattingReportBuilderTestCase {

    @Test
    public void test() throws IOException {
        ArrayList<Pair<MavenArtifact, List<String>>> upgrades = new ArrayList<>();
        MavenArtifact artifact;
        upgrades.add(Pair.of(new MavenArtifact("org.apache.activemq", "artemis-dto", "pom", null, "2.21.0.redhat-00045", new File(".")),
                List.of("2.28.0.redhat-00012", "2.21.0.redhat-00046")));
        upgrades.add(Pair.of(new MavenArtifact("org.apache.activemq", "artemis-core-client", "pom", null, "2.21.0.redhat-00045", new File(".")),
                List.of("2.28.0.redhat-00012", "2.21.0.redhat-00046")));
        upgrades.add(Pair.of(new MavenArtifact("org.apache.activemq", "artemis-bla", "pom", null, "2.21.0.redhat-00045", new File(".")),
                List.of("2.28.0.redhat-00012", "2.21.0.redhat-00046")));
        upgrades.add(Pair.of(artifact = new MavenArtifact("org.jboss", "jboss-component", "pom", null, "1.1.0", new File(".")),
                List.of("1.1.1", "1.1.2", "1.2.0")));

        HashMap<MavenArtifact, Map<String, String>> artifactToRepositoryMap = new HashMap<>();
        artifactToRepositoryMap.put(artifact, Map.of("1.1.1", "mrrc", "1.1.2", "mrrc2"));

        String html = new FormattingReportBuilder()
                .withRepositories(List.of(
                        new Repository("mrrc", "https://maven.repository.redhat.com/ga/"),
                        new Repository("mrrc2", "https://maven.repository.redhat.com/ga/")
                ))
                .withUpgrades(upgrades)
                .withArtifactToRepositoryMap(artifactToRepositoryMap)
                .build();
        Files.writeString(Path.of("dev-report.html"), html);
    }
}
