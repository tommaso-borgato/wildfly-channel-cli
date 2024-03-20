package org.jboss.set.channel.cli;

import org.jboss.set.channel.cli.utils.ConversionUtils;
import org.wildfly.channel.Channel;
import org.wildfly.channel.Repository;
import org.wildfly.channel.maven.ChannelCoordinate;
import picocli.CommandLine;

import java.util.List;


@CommandLine.Command(name = "extract-repositories",
        description = "Extract URLs of the channel repositories, print them on stdout.")
public class ExtractRepositoriesCommand extends MavenBasedCommand {

    @CommandLine.Parameters(index = "0", description = "Channel coordinate (URL or GAV)",
            paramLabel = "channelCoordinate")
    private String channelCoord;

    @CommandLine.Option(names = "--repositories", split = ",",
            description = "Comma separated repositories URLs where the channel files should be looked for, if they need to be resolved via maven.",
            paramLabel = "URL")
    private List<String> repositoryUrls;


    @Override
    public Integer call() {
        ChannelCoordinate coordinate = ConversionUtils.toChannelCoordinate(channelCoord);
        List<Repository> repositories = ConversionUtils.toChannelRepositoryList(repositoryUrls);

        Channel channel = resolveChannel(coordinate, repositories);
        channel.getRepositories().stream().map(Repository::getUrl).forEach(System.out::println);

        return CommandLine.ExitCode.OK;
    }
}
