package org.jboss.set.channelreports;

import picocli.CommandLine;

public class Main {
    public static void main(String[] args) {
        CommandLine commandLine = new CommandLine(new MainCommand());
        commandLine.addSubcommand(new CompareChannels());
        commandLine.execute(args);
    }
}