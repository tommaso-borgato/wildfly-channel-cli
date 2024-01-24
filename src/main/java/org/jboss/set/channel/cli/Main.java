package org.jboss.set.channel.cli;

import picocli.CommandLine;

public class Main {
    public static void main(String[] args) {
        CommandLine commandLine = new CommandLine(new MainCommand());
        commandLine.addSubcommand(new CompareChannelsCommand());
        commandLine.addSubcommand(new FindUpgradesCommand());
        commandLine.addSubcommand(new CreateManifestFromRepoCommand());
        commandLine.addSubcommand(new CreateChannelCommand());
        commandLine.addSubcommand(new MergeManifestsCommand());
        commandLine.execute(args);
    }
}