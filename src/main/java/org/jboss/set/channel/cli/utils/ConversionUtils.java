package org.jboss.set.channel.cli.utils;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.aether.repository.RemoteRepository;
import org.wildfly.channel.BlocklistCoordinate;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.Repository;
import org.wildfly.channel.maven.ChannelCoordinate;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class ConversionUtils {

    private ConversionUtils() {
    }

    public static List<RemoteRepository> toRepositoryList(List<String> pairs) {
        if (pairs == null) {
            return Collections.emptyList();
        }
        return IntStream.range(0, pairs.size())
                .mapToObj(i -> {
                    String item = pairs.get(i);
                    String[] split = item.split("::");
                    if (split.length == 1) {
                        return new RemoteRepository.Builder("repo-" + i, "default", item).build();
                    } else if (split.length == 2){
                        return new RemoteRepository.Builder(split[0], "default", split[1]).build();
                    } else {
                        throw new IllegalArgumentException("Invalid repository format, expected is 'repo-id::repo-url': " + item);
                    }
                })
                .collect(Collectors.toList());
    }

    public static List<Repository> toChannelRepositoryList(List<String> pairs) {
        if (pairs == null) {
            return Collections.emptyList();
        }
        return IntStream.range(0, pairs.size())
                .mapToObj(i -> {
                    String item = pairs.get(i);
                    String[] split = item.split("::");
                    if (split.length == 1) {
                        return new Repository("repo-" + i, item);
                    } else if (split.length == 2){
                        return new Repository(split[0], split[1]);
                    } else {
                        throw new IllegalArgumentException("Invalid repository format, expected is 'repo-id::repo-url': " + item);
                    }
                })
                .collect(Collectors.toList());
    }

    public static List<Repository> toChannelRepositories(List<RemoteRepository> repositories) {
        return repositories.stream()
                .map(r -> new Repository(r.getId(), r.getUrl()))
                .toList();
    }

    public static ChannelCoordinate toChannelCoordinate(String coordinateString) {
        if (StringUtils.isBlank(coordinateString)) {
            throw new IllegalArgumentException("The channel coordinate has to be a non-empty string.");
        }
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
                try {
                    coordinate = new ChannelCoordinate(Path.of(coordinateString).toUri().toURL());
                } catch (MalformedURLException e2) {
                    throw new IllegalArgumentException("Given string is not URL or GAV: " + coordinateString);
                }
            }
        }
        return coordinate;
    }

    public static BlocklistCoordinate toBlocklistCoordinate(String coordinateString) {
        if (StringUtils.isBlank(coordinateString)) {
            return null;
        }
        BlocklistCoordinate coordinate;
        try {
            coordinate = new BlocklistCoordinate(new URL(coordinateString));
        } catch (MalformedURLException e) {
            String[] segments = coordinateString.split(":");
            if (segments.length == 2) {
                coordinate = new BlocklistCoordinate(segments[0], segments[1]);
            } else if (segments.length == 3) {
                coordinate = new BlocklistCoordinate(segments[0], segments[1], segments[2]);
            } else {
                try {
                    coordinate = new BlocklistCoordinate(Path.of(coordinateString).toUri().toURL());
                } catch (MalformedURLException e2) {
                    throw new IllegalArgumentException("Given string is not URL or GAV: " + coordinateString);
                }
            }
        }
        return coordinate;
    }

    public static ChannelManifestCoordinate toManifestCoordinate(String coordinateString) {
        if (StringUtils.isBlank(coordinateString)) {
            return null;
        }
        ChannelManifestCoordinate coordinate;
        try {
            coordinate = new ChannelManifestCoordinate(new URL(coordinateString));
        } catch (MalformedURLException e) {
            String[] segments = coordinateString.split(":");
            if (segments.length == 2) {
                coordinate = new ChannelManifestCoordinate(segments[0], segments[1]);
            } else if (segments.length == 3) {
                coordinate = new ChannelManifestCoordinate(segments[0], segments[1], segments[2]);
            } else {
                try {
                    coordinate = new ChannelManifestCoordinate(Path.of(coordinateString).toUri().toURL());
                } catch (MalformedURLException e2) {
                    throw new IllegalArgumentException("Given string is not URL or GAV: " + coordinateString);
                }
            }
        }
        return coordinate;
    }

    public static Channel.NoStreamStrategy toNoStreamStrategy(String strategyString) {
        if (strategyString == null)
            return null;
        return switch (strategyString) {
            case "latest" -> Channel.NoStreamStrategy.LATEST;
            case "maven-latest" -> Channel.NoStreamStrategy.MAVEN_LATEST;
            case "maven-release" -> Channel.NoStreamStrategy.MAVEN_RELEASE;
            case "none" -> Channel.NoStreamStrategy.NONE;
            default -> throw new IllegalArgumentException("Unknown NoStreamStrategy name: " + strategyString);
        };
    }

}
