package org.jboss.set.channelreports;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.jboss.logging.Logger;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestCoordinate;
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
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@CommandLine.Command(name = "compare-channels",
        description = "Generates report that identifies intersecting streams of two given channels, and highlights streams where their versions differ.")
public class CompareChannels implements Callable<Integer> {

    public static final Path LOCAL_MAVEN_REPO = Paths.get(System.getProperty("user.home"), ".m2", "repository");

    private static final Logger logger = Logger.getLogger(CompareChannels.class);

    @CommandLine.Parameters(index = "0", description = "Base channel (URL of GAV)")
    private String baseChannelCoordinate;

    @CommandLine.Parameters(index = "1", description = "Channel to compare the base channel with (URL or GAV)")
    private String targetChannelCoordinate;

    @CommandLine.Option(names = "--channel-repositories", description = "Comma separated repositories URLs where the channels should be looked for",
            split = ",")
    private List<String> channelRepositoriesUrls;

    @Override
    public Integer call() throws Exception {
        final ChannelCoordinate baseCoordinate = toChannelCoordinate(baseChannelCoordinate);
        final ChannelCoordinate targetCoordinate = toChannelCoordinate(targetChannelCoordinate);
        final List<RemoteRepository> channelRepositories = toRepositoryList(channelRepositoriesUrls);
        final RepositorySystem system = newRepositorySystem();
        final DefaultRepositorySystemSession systemSession = newRepositorySystemSession(system);

        try (VersionResolverFactory resolverFactory = new VersionResolverFactory(system, systemSession)) {
            List<Channel> baseChannels = resolverFactory.resolveChannels(List.of(baseCoordinate), channelRepositories);
            List<Channel> targetChannels = resolverFactory.resolveChannels(List.of(targetCoordinate), channelRepositories);

            ChannelSession baseChannelSession = new ChannelSession(baseChannels, resolverFactory);
            ChannelSession targetChannelSession = new ChannelSession(targetChannels, resolverFactory);

            Set<Stream> baseStreams = resolveStreams(baseChannels, resolverFactory);

            ArrayList<Pair<MavenArtifact, MavenArtifact>> diff = new ArrayList<>();
            for (Stream stream : baseStreams) {
                try {
                    MavenArtifact baseArtifact = baseChannelSession.resolveMavenArtifact(stream.getGroupId(), stream.getArtifactId(), "pom", null, null);
                    MavenArtifact targetArtifact = targetChannelSession.resolveMavenArtifact(stream.getGroupId(), stream.getArtifactId(), "pom", null, null);
                    if (!baseArtifact.getVersion().equals(targetArtifact.getVersion())) {
                        diff.add(Pair.of(baseArtifact, targetArtifact));
                        System.out.printf("%s:%s:%s -> %s%n", baseArtifact.getGroupId(), baseArtifact.getArtifactId(),
                                baseArtifact.getVersion(), targetArtifact.getVersion());
                    }
                } catch (RuntimeException e) {
                    logger.errorf(e, "Failure to compare stream %s", stream);
                }
                if (stream.getGroupId().contains("opensaml")) {
                    break;
                }
            }

            List<Repository> targetRepositories = targetChannels.stream()
                    .flatMap(ch -> ch.getRepositories().stream())
                    .toList();
            String reportHtml = new FormattingReportBuilder()
                    .withRepositories(targetRepositories)
                    .withUpgrades(diff)
                    .build();
            Files.write(Path.of("report.html"), reportHtml.getBytes());

            return CommandLine.ExitCode.OK;
        }
    }

    private Set<Stream> resolveStreams(List<Channel> channels, VersionResolverFactory resolverFactory) {
        List<ChannelManifestCoordinate> manifestCoordinates = channels.stream()
                .map(Channel::getManifestCoordinate).toList();
        List<Repository> repositories = channels.stream()
                .flatMap(channel -> channel.getRepositories().stream()).toList();

        try (MavenVersionsResolver resolver = resolverFactory.create(repositories)) {
            List<URL> resolvedBaseManifests = resolver.resolveChannelMetadata(manifestCoordinates);
            List<ChannelManifest> baseManifests = resolvedBaseManifests.stream().map(ChannelManifestMapper::from).toList();
            return baseManifests.stream().flatMap(manifest -> manifest.getStreams().stream()).collect(Collectors.toSet());
        }
    }

    private static List<RemoteRepository> toRepositoryList(List<String> urls) {
        if (urls == null) {
            return Collections.emptyList();
        }
        return IntStream.range(0, urls.size())
                .mapToObj(i -> new RemoteRepository.Builder("repo-" + i, "default", urls.get(i)).build())
                .collect(Collectors.toList());
    }

    private static ChannelCoordinate toChannelCoordinate(String coordinateString) {
        ChannelCoordinate coordinate;
        try {
            coordinate = new ChannelCoordinate(new URL(coordinateString));
        } catch (MalformedURLException e) {
            String[] segments = coordinateString.split(":");
            if (segments.length == 2) {
                coordinate = new ChannelCoordinate(segments[0], segments[1]);
            } else if (segments.length == 3) {
                coordinate = new ChannelCoordinate(segments[0], segments[1], segments[2]);
            } else {
                throw new IllegalArgumentException("Given string is not URL or GAV: " + coordinateString);
            }
        }
        return coordinate;
    }

    private static RepositorySystem newRepositorySystem() {
        final DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
                logger.error("A Maven service creation failed", exception);
            }
        });
        return locator.getService(RepositorySystem.class);
    }

    private static DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system) throws IOException {
        // TODO: Create empty temporary dir to act as local maven repo - we don't want any artifacts loaded from the
        //  local repo, because the channel would filter those out, leaving us with no artifact available.
        //  There is a PR open that could resolve this issue: https://github.com/wildfly-extras/wildfly-channel/pull/218
        //  If that's merged we could try to use standard local maven repo here.
        FileAttribute<Set<PosixFilePermission>> attrs = PosixFilePermissions.asFileAttribute(
                PosixFilePermissions.fromString("rwx------"));
        Path localRepoPath = Files.createTempDirectory("local-maven-cache-", attrs);

        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        LocalRepository localRepo = new LocalRepository(localRepoPath.toFile());
//        LocalRepository localRepo = new LocalRepository(LOCAL_MAVEN_REPO.toFile());
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

        // TODO: For some reason I get metadata checksum failures from MRRC, even though when I check them manually the
        //  checksum seems to be correct.
        session.setChecksumPolicy("warn");

        return session;
    }

}
