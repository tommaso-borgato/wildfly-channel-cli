package org.jboss.set.channel.cli.utils;

import org.jboss.logging.Logger;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestMapper;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.Stream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;

public final class IOUtils {

    private static final Logger logger = Logger.getLogger(IOUtils.class);

    private IOUtils() {
    }

    public static void writeManifestFile(Path file, Collection<Stream> streams) throws IOException {
        logger.infof("Writing manifest into %s", file.toString());
        ChannelManifest manifest = new ChannelManifest(null, null, null, streams);
        String manifestString = ChannelManifestMapper.toYaml(manifest);
        Files.write(file, manifestString.getBytes(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
    }

    public static void writeChannelFile(Path file, Channel channel) throws IOException {
        logger.infof("Writing channel into %s", file.toString());
        String yaml = ChannelMapper.toYaml(channel);
        Files.write(file, yaml.getBytes(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
    }

}
