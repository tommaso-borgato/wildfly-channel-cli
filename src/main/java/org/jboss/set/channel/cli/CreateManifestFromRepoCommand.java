package org.jboss.set.channel.cli;

import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestMapper;
import org.wildfly.channel.Stream;
import picocli.CommandLine;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "create-manifest-from-repo",
        description = "Scans a local maven repository and creates a manifest file representing the GAVs existing in the repository.")
public class CreateManifestFromRepoCommand implements Callable<Integer> {

    private static final String MAVEN_METADATA_XML = "maven-metadata.xml";
    private static final String MAVEN_METADATA_XML_LOCAL = "maven-metadata-local.xml";

    @CommandLine.Parameters(index = "0",
            description = "Local Maven repository path to generate the manifest from.",
            paramLabel = "path")
    private Path repositoryPath;

    @CommandLine.Option(names = {"--output-file", "-o"}, defaultValue = "manifest.yaml",
            description = "Manifest file to be written.")
    private Path outputFile;

    @Override
    public Integer call() throws Exception {
        ArrayList<Stream> streams = new ArrayList<>();

        try (java.util.stream.Stream<Path> stream = Files.walk(repositoryPath)) {
            List<Path> metadataFiles = stream.filter(
                        p -> MAVEN_METADATA_XML.equals(p.getFileName().toString()) || MAVEN_METADATA_XML_LOCAL.equals(p.getFileName().toString())
                    )
                    .toList();

            for (Path metadataFile : metadataFiles) {
                try (InputStream is = new FileInputStream(metadataFile.toFile())) {
                    MetadataXpp3Reader reader = new MetadataXpp3Reader();
                    Metadata metadata = reader.read(is);
                    if (metadata.getVersion() != null) {
                        // Skip metadata files that list specific artifact files, we are just interested in versions.
                        continue;
                    }
                    for (String version : metadata.getVersioning().getVersions()) {
                        streams.add(new Stream(metadata.getGroupId(), metadata.getArtifactId(), version));
                    }
                }
            }


        }

        ChannelManifest manifest = new ChannelManifest("generated manifest", null, null, streams);
        String yaml = ChannelManifestMapper.toYaml(manifest);
        Files.writeString(outputFile, yaml, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);

        return CommandLine.ExitCode.OK;
    }
}
