package org.jboss.set.channelreports;

import org.eclipse.aether.version.Version;
import org.junit.jupiter.api.Test;
import org.wildfly.channel.Blocklist;
import org.wildfly.channel.BlocklistEntry;
import org.wildfly.channel.Stream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

public class FindUpgradesCommandTestCase {

    private static final Stream STREAM = new Stream("g", "a", "v");

    @Test
    public void testFindPossibleUpgradesMethod() {
        // Has to be sorted in decreasing order:
        List<String> versions = new ArrayList<>(List.of(
                "1.0.0", "1.0.2",
                "1.1.0", "1.1.1", "1.1.1.redhat-00001", "1.1.1.redhat-00002",
                "2.0.1", "2.1.0", "2.1.1", "2.2.0", "2.2.1"
        ));
        Collections.reverse(versions);
        List<String> possibleUpgrades = FindUpgradesCommand.findPossibleUpgrades(STREAM, toVersionObjects(versions), null, null, null);
        assertThat(possibleUpgrades).containsExactly("1.0.2",
                "1.1.1", "1.1.1.redhat-00002",
                "2.0.1", "2.1.1", "2.2.1");
    }

    @Test
    public void testFindPossibleUpgradesMethod2() {
        // Has to be sorted in decreasing order:
        List<String> versions = new ArrayList<>(List.of(
                "3.8.6.redhat-00002", "3.8.6.redhat-00001", "3.8.4.redhat-00001", "3.8.1.redhat-00001",
                "3.6.3.redhat-00012", "3.6.3.redhat-00010"
        ));
        List<String> possibleUpgrades = FindUpgradesCommand.findPossibleUpgrades(STREAM, toVersionObjects(versions), null, null, null);
        assertThat(possibleUpgrades).containsExactly("3.6.3.redhat-00012", "3.8.6.redhat-00002");
    }

    @Test
    public void testFindPossibleUpgradesIncludeExclude() {
        // Has to be sorted in decreasing order:
        List<String> versions = new ArrayList<>(List.of(
                "1.0.0", "1.1.0.redhat-00001", "1.2.0.fuse-redhat-00001", "1.3.0.jre-redhat-00001"
        ));
        Collections.reverse(versions);

        Pattern include = Pattern.compile("[.-]redhat-\\d+");
        Pattern exclude = Pattern.compile("[.-]fuse-");

        List<String> possibleUpgrades = FindUpgradesCommand.findPossibleUpgrades(STREAM, toVersionObjects(versions), include, null, null);
        assertThat(possibleUpgrades).containsExactly("1.1.0.redhat-00001", "1.2.0.fuse-redhat-00001", "1.3.0.jre-redhat-00001");

        possibleUpgrades = FindUpgradesCommand.findPossibleUpgrades(STREAM, toVersionObjects(versions), include, exclude, null);
        assertThat(possibleUpgrades).containsExactly("1.1.0.redhat-00001", "1.3.0.jre-redhat-00001");
    }

    @Test
    public void testFindPossibleUpgradesBlocklist() {
        // Has to be sorted in decreasing order:
        List<String> versions = new ArrayList<>(List.of(
                "1.0.0", "1.1.0.redhat-00001", "1.2.0.fuse-redhat-00001", "1.3.0.jre-redhat-00001"
        ));
        Collections.reverse(versions);

        Blocklist blocklist = new Blocklist("1.0.0", Set.of(new BlocklistEntry("g", "a",
                Set.of(
                        "1.1.0.redhat-00001", "1.2.0.fuse-redhat-00001"
                ))));

        List<String> possibleUpgrades = FindUpgradesCommand.findPossibleUpgrades(STREAM, toVersionObjects(versions), null, null, List.of(blocklist));
        assertThat(possibleUpgrades).containsExactly(
                "1.0.0", "1.3.0.jre-redhat-00001"
        );
    }

    private static List<? extends Version> toVersionObjects(List<String> versions) {
        return versions.stream().map(CustomVersion::new).toList();
    }

    private record CustomVersion(String version) implements Version {

        @Override
        public String toString() {
            return version;
        }

        /**
         * This can be ignored here.
         */
        @Override
        public int compareTo(Version o) {
            return version.compareTo(o.toString());
        }
    }
}
