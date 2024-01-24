package org.jboss.set.channelreports;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.set.channelreports.report.FormattingReportBuilder;
import org.jboss.set.channelreports.utils.ConversionUtils;
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
import java.util.List;
import java.util.Set;

@CommandLine.Command(name = "compare-channels",
        description = "Generates report that identifies intersecting streams of two given channels, and highlights " +
                "streams where their versions differ.")
public class CompareChannelsCommand extends MavenBasedCommand {

    @CommandLine.Parameters(index = "0", description = "Base channel coordinate (URL of GAV)")
    private String baseChannelCoordinate;

    @CommandLine.Parameters(index = "1", description = "Comparison channel coordinate (URL or GAV)")
    private String targetChannelCoordinate;

    @CommandLine.Option(names = "--channel-repositories", description = "Comma separated repositories URLs where the channels should be looked for",
            split = ",")
    private List<String> channelRepositoriesUrls;

    @Override
    public Integer call() throws Exception {
        final ChannelCoordinate baseCoordinate = ConversionUtils.toChannelCoordinate(baseChannelCoordinate);
        final ChannelCoordinate targetCoordinate = ConversionUtils.toChannelCoordinate(targetChannelCoordinate);
        final List<RemoteRepository> channelRepositories = ConversionUtils.toRepositoryList(channelRepositoriesUrls);

        try (VersionResolverFactory resolverFactory = new VersionResolverFactory(system, systemSession)) {
            List<Channel> baseChannels = resolverFactory.resolveChannels(List.of(baseCoordinate), channelRepositories);
            List<Channel> targetChannels = resolverFactory.resolveChannels(List.of(targetCoordinate), channelRepositories);

            ChannelSession baseChannelSession = new ChannelSession(baseChannels, resolverFactory);
            ChannelSession targetChannelSession = new ChannelSession(targetChannels, resolverFactory);

            Set<Stream> baseStreams = resolveStreams(baseChannels, resolverFactory);

            ArrayList<Pair<MavenArtifact, List<String>>> diff = new ArrayList<>();
            for (Stream stream : baseStreams) {
                try {
                    MavenArtifact baseArtifact = baseChannelSession.resolveMavenArtifact(stream.getGroupId(), stream.getArtifactId(), "pom", null, null);
                    MavenArtifact targetArtifact = targetChannelSession.resolveMavenArtifact(stream.getGroupId(), stream.getArtifactId(), "pom", null, null);
                    if (!baseArtifact.getVersion().equals(targetArtifact.getVersion())) {
                        diff.add(Pair.of(baseArtifact, List.of(targetArtifact.getVersion())));
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

}
