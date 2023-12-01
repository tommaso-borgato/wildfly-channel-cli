package org.jboss.set.channelreports;

import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command()
class MainCommand implements Callable<Integer> {
    @Override
    public Integer call() throws Exception {
        // TODO: print usage
        System.out.println("Main command");
        return CommandLine.ExitCode.OK;
    }
}
