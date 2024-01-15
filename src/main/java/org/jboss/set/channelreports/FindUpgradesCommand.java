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
import org.wildfly.channel.Blocklist;
import org.wildfly.channel.BlocklistCoordinate;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestMapper;
import org.wildfly.channel.ChannelSession;
import org.wildfly.channel.MavenArtifact;
import org.wildfly.channel.Repository;
import org.wildfly.channel.Stream;
import org.wildfly.channel.maven.ChannelCoordinate;
import org.wildfly.channel.maven.VersionResolverFactory;
import org.wildfly.channel.spi.MavenVersionsResolver;
import picocli.CommandLine;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@CommandLine.Command(name = "find-upgrades",
        description = "Generates report showing possible upgrades for streams in given channel by directly querying " +
                "given Maven repositories. This also generates two manifest files, " +
                "diff-manifest.yaml and upgraded-manifest.yaml, containing upgraded streams and all streams with " +
                "upgraded versions respectively.")
public class FindUpgradesCommand extends MavenBasedCommand {

    private final Path REPORT_FILE = Path.of("report.html");
    private final Path DIFF_MANIFEST_FILE = Path.of("diff-manifest.yaml");
    private final Path UPGRADED_MANIFEST_FILE = Path.of("upgraded-manifest.yaml");

    @CommandLine.Parameters(index = "0", description = "Base channel coordinate (URL of GAV).")
    private String channelCoordinateString;

    @CommandLine.Option(names = "--channel-repositories", split = ",",
            description = "Comma separated repositories URLs where the channels should be looked for, if a channel GAV is given.")
    private List<String> channelRepositoriesUrls;

    @CommandLine.Option(names = "--repositories", split = ",",
            description = "Comma separated repositories URLs where component upgrades should be looked for. Format is either `URL1,URL2,...` or `ID1::URL1,ID2::URL2,...`")
    private List<String> repositoryUrls;

    @CommandLine.Option(names = "--include-pattern",
            description = "Regexp that versions need to match in order to be added to the report.")
    private String versionsInclude;

    @CommandLine.Option(names = "--exclude-pattern",
            description = "Regexp to exclude versions from being added to the report.")
    private String versionsExclude;

    @CommandLine.Option(names = "--blocklist-coordinate",
            description = "Blocklist coordinate (URL or Maven GAV)")
    private String blocklistCoordinateString;

    private final ArrayList<Pair<MavenArtifact, List<String>>> upgrades = new ArrayList<>();
    private final Set<Stream> diffStreams = new HashSet<>();
    private final Set<Stream> upgradedStreams = new HashSet<>();
    private final List<UpgradeDiscoveryListener> discoveryListeners = new ArrayList<>();
    private final Map<MavenArtifact, Map<String, String>> artifactsToRepositories = new HashMap<>();
    private final List<RemoteRepository> repositories = new ArrayList<>();
    private final List<RemoteRepository> channelRepositories = new ArrayList<>();
    private Blocklist blocklist = null;

    public FindUpgradesCommand() {
        discoveryListeners.add(new UpgradeCollectingListener());
        discoveryListeners.add(new StreamCollectingListener());
    }

    @Override
    public Integer call() throws Exception {
        final ChannelCoordinate channelCoordinate = toChannelCoordinate(channelCoordinateString);
        channelRepositories.addAll(toRepositoryList(channelRepositoriesUrls));
        repositories.addAll(toRepositoryList(repositoryUrls));

        Pattern inclusionPattern = null;
        Pattern exclusionPattern = null;
        if (versionsInclude != null) {
            inclusionPattern = Pattern.compile(versionsInclude);
        }
        if (versionsExclude != null) {
            exclusionPattern = Pattern.compile(versionsExclude);
        }

        try (VersionResolverFactory resolverFactory = new VersionResolverFactory(system, systemSession)) {
            loadBlocklist(resolverFactory);
            List<Channel> channels = resolverFactory.resolveChannels(List.of(channelCoordinate), channelRepositories);
            ChannelSession channelSession = new ChannelSession(channels, resolverFactory);
            Set<Stream> channelStreams = resolveStreams(channels, resolverFactory);
            upgradedStreams.addAll(channelStreams);

            for (Stream stream : channelStreams) {
                MavenArtifact resolvedArtifact = channelSession.resolveMavenArtifact(stream.getGroupId(), stream.getArtifactId(), "pom", null, null);
                VersionRangeResult versionRangeResult = resolveVersionRange(resolvedArtifact);
                List<Version> availableVersions = versionRangeResult.getVersions().stream()
                        .sorted(Comparator.reverseOrder()).toList();
                List<String> possibleUpgrades = findPossibleUpgrades(stream, availableVersions, inclusionPattern, exclusionPattern, blocklist);

                for (Version version : availableVersions) {
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
                    logger.infof("Found upgrades: %s:%s:%s -> %s", a.getGroupId(), a.getArtifactId(), a.getVersion(),
                            String.join(", ", possibleUpgrades));

                    for (UpgradeDiscoveryListener listener: discoveryListeners) {
                        listener.upgrade(resolvedArtifact, possibleUpgrades);
                    }
                }
            }
        }

        if (upgrades.isEmpty()) {
            // Don't write the report file if the report is empty.
            return CommandLine.ExitCode.OK;
        }

        writeReportFile();

        // Write manifest file that contains only upgraded components
        writeManifestFile(DIFF_MANIFEST_FILE, diffStreams);
        // Write manifest file that contains both original and upgraded components
        writeManifestFile(UPGRADED_MANIFEST_FILE, upgradedStreams);

        return CommandLine.ExitCode.OK;
    }

    private VersionRangeResult resolveVersionRange(MavenArtifact artifact) throws RepositoryException {
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

    private void writeReportFile() throws IOException {
        List<Repository> discoveryRepositories = toChannelRepositories(repositories);
        String reportHtml = new FormattingReportBuilder()
                .withRepositories(discoveryRepositories)
                .withUpgrades(upgrades)
                .withArtifactToRepositoryMap(artifactsToRepositories)
                .build();

        logger.infof("Writing report file into %s", REPORT_FILE.toString());
        Files.write(REPORT_FILE, reportHtml.getBytes());
    }

    private void writeManifestFile(Path file, Collection<Stream> streams) throws IOException {
        ChannelManifest manifest = new ChannelManifest(null, null, null, streams);
        String manifestString = ChannelManifestMapper.toYaml(manifest);
        Files.write(file, manifestString.getBytes());
    }

    private void loadBlocklist(VersionResolverFactory resolverFactory) {
        final BlocklistCoordinate blocklistCoordinate = toBlocklistCoordinate(blocklistCoordinateString);
        if (blocklistCoordinate != null) {
            try (MavenVersionsResolver resolver = resolverFactory.create(toChannelRepositories(channelRepositories))) {
                List<URL> urls = resolver.resolveChannelMetadata(List.of(blocklistCoordinate));
                if (urls.size() > 0) {
                    blocklist = Blocklist.from(urls.get(0));
                }
            }
        }
    }

    /**
     * This returns highest version of each stream from given list of versions.
     *
     * @param versions List of available versions, has to be sorted from highest to lowest
     * @return A subset of the list passed in `versions` argument, containing only highest versions of each stream.
     */
    static List<String> findPossibleUpgrades(Stream stream, List<? extends Version> versions, Pattern include, Pattern exclude, Blocklist blocklist) {
        final Set<String> blockedVersions = new HashSet<>();
        if (blocklist != null) {
            blockedVersions.addAll(blocklist.getVersionsFor(stream.getGroupId(), stream.getArtifactId()));
        }

        // Apply inclusions, exclusions and blocklist, only work with the resulting subset
        versions = versions.stream()
                .filter(v -> include == null || include.matcher(v.toString()).find())
                .filter(v -> exclude == null || !exclude.matcher(v.toString()).find())
                .filter(v -> blocklist == null || !blockedVersions.contains(v.toString()))
                .toList();

        if (versions.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<String> resultVersions = new ArrayList<>();

        // Always add the first (highest) version from given list.
        String highestVersion = versions.get(0).toString();
        resultVersions.add(highestVersion);
        String[] lastSegments = VersionUtils.parseVersion(highestVersion);
        String[] lastNumericalSegments = VersionUtils.numericalSegments(lastSegments);
        String lastQualifier = VersionUtils.firstQualifierSegment(lastSegments);
        int lastIndex = lastNumericalSegments.length - 1;

        for (Version version : versions) {
            String[] segments = VersionUtils.parseVersion(version.toString());
            String[] numericalSegments = VersionUtils.numericalSegments(segments);
            String qualifier = VersionUtils.firstQualifierSegment(segments);

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

    private interface UpgradeDiscoveryListener {
        void upgrade(MavenArtifact artifact, List<String> possibleUpgrades);
    }

    private class UpgradeCollectingListener implements UpgradeDiscoveryListener {
        @Override
        public void upgrade(MavenArtifact artifact, List<String> possibleUpgrades) {
            upgrades.add(Pair.of(artifact, possibleUpgrades));
        }
    }

    private class StreamCollectingListener implements UpgradeDiscoveryListener {
        @Override
        public void upgrade(MavenArtifact artifact, List<String> possibleUpgrades) {
            Optional<String> latestMicro = VersionUtils.findMicroUpgrade(artifact.getVersion(), possibleUpgrades);
            if (latestMicro.isPresent()) {
                // Add to the collection containing only upgraded streams
                diffStreams.add(new Stream(artifact.getGroupId(), artifact.getArtifactId(), latestMicro.get()));

                // Update the stream in the collection containing all streams
                Optional<Stream> originalStream = upgradedStreams.stream()
                        .filter(s -> s.getGroupId().equals(artifact.getGroupId())
                                && s.getArtifactId().equals(artifact.getArtifactId()))
                        .findAny();
                if (originalStream.isPresent()) {
                    upgradedStreams.remove(originalStream.get());
                    upgradedStreams.add(new Stream(artifact.getGroupId(), artifact.getArtifactId(), latestMicro.get()));
                }
            }
        }
    }

}
