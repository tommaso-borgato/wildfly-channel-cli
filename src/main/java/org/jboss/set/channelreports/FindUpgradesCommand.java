package org.jboss.set.channelreports;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelSession;
import org.wildfly.channel.MavenArtifact;
import org.wildfly.channel.Repository;
import org.wildfly.channel.Stream;
import org.wildfly.channel.maven.ChannelCoordinate;
import org.wildfly.channel.maven.VersionResolverFactory;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@CommandLine.Command(name = "find-upgrades",
        description = "Generates report showing possible upgrades for streams in given channel by directly querying " +
                "given Maven repositories.")
public class FindUpgradesCommand extends MavenBasedCommand {

    @CommandLine.Parameters(index = "0", description = "Base channel (URL of GAV).")
    private String channelCoordinateString;

    @CommandLine.Option(names = "--channel-repositories", split = ",",
            description = "Comma separated repositories URLs where the channels should be looked for, if a channel GAV is given.")
    private List<String> channelRepositoriesUrls;

    @CommandLine.Option(names = "--repositories", split = ",",
            description = "Comma separated repositories URLs where component upgrades should be looked for. Format is either `URL1,URL2,...` or `ID1::URL1,ID2::URL2,...`")
    private List<String> repositoryUrls;

    @Override
    public Integer call() throws Exception {
        final ChannelCoordinate channelCoordinate = toChannelCoordinate(channelCoordinateString);
        final List<RemoteRepository> channelRepositories = toRepositoryList(channelRepositoriesUrls);
        final List<RemoteRepository> repositories = toRepositoryList(repositoryUrls);

        final ArrayList<Pair<MavenArtifact, List<String>>> upgrades = new ArrayList<>();
        final Map<MavenArtifact, Map<String, String>> artifactsToRepositories = new HashMap<>();
        try (VersionResolverFactory resolverFactory = new VersionResolverFactory(system, systemSession)) {
            List<Channel> channels = resolverFactory.resolveChannels(List.of(channelCoordinate), channelRepositories);
            ChannelSession channelSession = new ChannelSession(channels, resolverFactory);
            Set<Stream> channelStreams = resolveStreams(channels, resolverFactory);

            for (Stream stream : channelStreams) {
                MavenArtifact resolvedArtifact = channelSession.resolveMavenArtifact(stream.getGroupId(), stream.getArtifactId(), "pom", null, null);
                VersionRangeResult versionRangeResult = resolveVersionRange(resolvedArtifact, repositories);
                List<Version> availableVersions = versionRangeResult.getVersions().stream()
                        .sorted(Comparator.reverseOrder()).toList();
                List<String> possibleUpgrades = findPossibleUpgrades(availableVersions);

                for (Version version: availableVersions) {
                    ArtifactRepository repository = versionRangeResult.getRepository(version);
                    artifactsToRepositories.compute(resolvedArtifact, (a, current) -> {
                       if (current == null) {
                           current = new HashMap<>();
                       }
                       current.put(version.toString(), repository.getId());
                       return current;
                    });
                }

                if (!possibleUpgrades.isEmpty()) {
                    //noinspection UnnecessaryLocalVariable
                    MavenArtifact a = resolvedArtifact;
                    System.out.printf("%s:%s:%s -> %s%n", a.getGroupId(), a.getArtifactId(), a.getVersion(),
                            String.join(", ", possibleUpgrades));
                    upgrades.add(Pair.of(resolvedArtifact, possibleUpgrades));
                }
            }
        }

        if (upgrades.isEmpty()) {
            // Don't write the report file if the report is empty.
            return CommandLine.ExitCode.OK;
        }

        List<Repository> discoveryRepositories = repositories.stream()
                .map(r -> new Repository(r.getId(), r.getUrl()))
                .toList();
        String reportHtml = new FormattingReportBuilder()
                .withRepositories(discoveryRepositories)
                .withUpgrades(upgrades)
                .withArtifactToRepositoryMap(artifactsToRepositories)
                .build();
        Files.write(Path.of("report.html"), reportHtml.getBytes());

        return CommandLine.ExitCode.OK;
    }

    private VersionRangeResult resolveVersionRange(MavenArtifact artifact, List<RemoteRepository> repositories) throws RepositoryException {
        VersionRangeRequest rangeRequest = new VersionRangeRequest();
        // Set version range from current version excluded:
        Artifact requestArtifact = new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(),
                artifact.getClassifier(), artifact.getExtension(), "(" + artifact.getVersion() + ",)");
        rangeRequest.setArtifact(requestArtifact);
        rangeRequest.setRepositories(repositories);

        VersionRangeResult rangeResult = system.resolveVersionRange(systemSession, rangeRequest);

        for (Exception e : rangeResult.getExceptions()) {
            logger.debugf("Version resolution exception: %s", e.getMessage());
        }

        return rangeResult;
    }

    /**
     * This returns highest version of each stream from given list of versions.
     * @param versions List of available versions, has to be sorted from highest to lowest
     * @return A subset of the list passed in `versions` argument, containing only highest versions of each stream.
     */
    static List<String> findPossibleUpgrades(List<? extends Version> versions) {
        if (versions.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<String> resultVersions = new ArrayList<>();

        // Always add the first (highest) version from given list.
        resultVersions.add(versions.get(0).toString());
        String[] lastSegments = parseVersion(versions.get(0).toString());
        String[] lastNumericalSegments = numericalSegments(lastSegments);
        String lastQualifier = qualifier(lastSegments);
        int lastIndex = lastNumericalSegments.length - 1;

        for (Version version: versions) {
            String[] segments = parseVersion(version.toString());
            String[] numericalSegments = numericalSegments(segments);
            String qualifier = qualifier(segments);

            boolean differs = false;
            for (int i = 0; i < lastIndex; i++) {
                if (i >= numericalSegments.length || !numericalSegments[i].equals(lastNumericalSegments[i])) {
                    differs = true;
                    break;
                }
            }

            if (!qualifier.equals(lastQualifier) || segments.length != lastSegments.length) {
                differs = true;
            }

            if (differs) {
                resultVersions.add(version.toString());
                lastSegments = segments;
                lastNumericalSegments = numericalSegments;
                lastQualifier = qualifier;
                lastIndex = numericalSegments.length - 1;
            }
        }

        Collections.reverse(resultVersions);
        return resultVersions;
    }

    private static String[] parseVersion(String version) {
        return version.split("[-._]");
    }

    private static String[] numericalSegments(String[] segments) {
        for (int i = 0; i < segments.length; i++) {
            try {
                Integer.valueOf(segments[i]);
            } catch (NumberFormatException e) {
                return Arrays.copyOf(segments, i);
            }
        }
        return segments;
    }

    private static String qualifier(String[] segments) {
        for (String segment : segments) {
            try {
                Integer.valueOf(segment);
            } catch (NumberFormatException e) {
                return segment;
            }
        }
        return "";
    }
}
