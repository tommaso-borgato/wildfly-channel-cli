package org.jboss.set.channel.cli;

import org.jboss.set.channel.cli.utils.ConversionUtils;
import org.jboss.set.channel.cli.utils.IOUtils;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.Repository;
import org.wildfly.channel.Stream;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@CommandLine.Command(name = "merge-manifests",
        description = "Merges two manifest. The second manifest streams always override the first manifest streams.")
public class MergeManifestsCommand extends MavenBasedCommand {

    @CommandLine.Parameters(index = "0", description = "First manifest coordinate (URL or GAV)",
            paramLabel = "manifestCoordinate1")
    private String firstManifestCoordinateString;

    @CommandLine.Parameters(index = "1", description = "Second manifest coordinate (URL or GAV)",
            paramLabel = "manifestCoordinate2")
    private String secondManifestCoordinateString;

    @CommandLine.Option(names = "--manifest-repositories", split = ",",
            description = "Comma separated repositories URLs where the manifest should be looked for, if they need to be resolved via maven.",
    paramLabel = "URL")
    private List<String> manifestRepositoriesUrls;

    @CommandLine.Option(names = {"--output-file", "-o"}, defaultValue = "manifest.yaml",
            description = "Manifest file to be written.")
    private Path outputFile;

    @Override
    public Integer call() throws Exception {
        ChannelManifestCoordinate firstCoordinate = ConversionUtils.toManifestCoordinate(firstManifestCoordinateString);
        ChannelManifestCoordinate secondCoordinate = ConversionUtils.toManifestCoordinate(secondManifestCoordinateString);

        if (firstCoordinate == null) {
            throw new IllegalArgumentException("Invalid coordinate: " + firstManifestCoordinateString);
        }
        if (secondCoordinate == null) {
            throw new IllegalArgumentException("Invalid coordinate: " + secondManifestCoordinateString);
        }

        List<Repository> repositories = ConversionUtils.toChannelRepositoryList(manifestRepositoriesUrls);

        ChannelManifest firstManifest = resolveManifest(firstCoordinate, repositories);
        ChannelManifest secondManifest = resolveManifest(secondCoordinate, repositories);

        Collection<Stream> mergedStreams = mergeStreams(firstManifest.getStreams(), secondManifest.getStreams());
        IOUtils.writeManifestFile(outputFile, mergedStreams);

        return CommandLine.ExitCode.OK;
    }

    static Collection<Stream> mergeStreams(Collection<Stream> streams1, Collection<Stream> streams2) {
        final Map<String, Stream> firstMap = new LinkedHashMap<>();
        streams1.forEach(s -> firstMap.putIfAbsent(key(s), s));
        final Map<String, Stream> secondMap = new LinkedHashMap<>();
        streams2.forEach(s -> secondMap.putIfAbsent(key(s), s));

        final Map<String, Stream> mergedMap = new LinkedHashMap<>(firstMap);
        mergedMap.putAll(secondMap);

        return mergedMap.values();
    }

    private static String key(Stream s) {
        return s.getGroupId() + ":" + s.getArtifactId();
    }

}
