package org.jboss.set.channelreports;

import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command()
class MainCommand implements Callable<Integer> {

    @CommandLine.Spec
    protected CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getOut());
        return CommandLine.ExitCode.OK;
    }
}
