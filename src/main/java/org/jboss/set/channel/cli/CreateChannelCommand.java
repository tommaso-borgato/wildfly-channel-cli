package org.jboss.set.channel.cli;

import org.jboss.set.channel.cli.utils.ConversionUtils;
import org.jboss.set.channel.cli.utils.IOUtils;
import org.wildfly.channel.BlocklistCoordinate;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.Repository;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "create-channel",
        description = "Creates a channel file according to given parameters.")
public class CreateChannelCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"--output-file", "-o"}, defaultValue = "channel.yaml",
            description = "Channel file to be written.")
    private Path outputFile;

    @CommandLine.Option(names = {"--name", "-n"},
            description = "Channel name.")
    private String name;

    @CommandLine.Option(names = {"--description", "-d"},
            description = "Channel description.")
    private String description;

    @CommandLine.Option(names = {"--repositories", "-r"}, split = ",", required = true, paramLabel = "ID::URL",
            description = "Channel repositories in the format `ID1::URL1,ID2::URL2,...`")
    private List<String> repositoriesString;

    @CommandLine.Option(names = {"--manifest-coordinate", "-m"}, required = true,
            description = "Manifest coordinate, GAV or URL.",
            paramLabel = "manifestCoordinate")
    private String manifestCoordinateString;

    @CommandLine.Option(names = {"--blocklist-coordinate", "-b"},
            description = "Blocklist coordinate, GAV or URL.",
            paramLabel = "blocklistCoordinate")
    private String blocklistCoordinateString;

    @CommandLine.Option(names = {"--no-stream-strategy", "-s"},
            description = "No stream strategy: latest, maven-latest, maven-release or none.",
            paramLabel = "strategy")
    private String noStreamStrategyString;

    @Override
    public Integer call() throws Exception {
        List<Repository> repositories = ConversionUtils.toChannelRepositoryList(repositoriesString);
        ChannelManifestCoordinate manifestCoordinate = ConversionUtils.toManifestCoordinate(manifestCoordinateString);
        BlocklistCoordinate blocklistCoordinate = ConversionUtils.toBlocklistCoordinate(blocklistCoordinateString);
        Channel.NoStreamStrategy noStreamStrategy = ConversionUtils.toNoStreamStrategy(noStreamStrategyString);

        Channel channel = new Channel(name, description, null, repositories, manifestCoordinate, blocklistCoordinate, noStreamStrategy);
        IOUtils.writeChannelFile(outputFile, channel);

        return CommandLine.ExitCode.OK;
    }
}
