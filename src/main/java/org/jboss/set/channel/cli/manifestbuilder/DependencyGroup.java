package org.jboss.set.channel.cli.manifestbuilder;

import java.util.Set;
import java.util.TreeSet;

public class DependencyGroup {

    private final String versionProperty;
    private final Set<String> dependencies = new TreeSet<>();

    public DependencyGroup(String versionProperty) {
        this.versionProperty = versionProperty;
    }

    public String getVersionProperty() {
        return versionProperty;
    }

    public Set<String> getDependencies() {
        return dependencies;
    }
}
