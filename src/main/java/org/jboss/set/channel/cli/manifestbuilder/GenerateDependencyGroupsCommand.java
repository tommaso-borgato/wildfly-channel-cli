package org.jboss.set.channel.cli.manifestbuilder;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.commonjava.maven.ext.common.model.Project;
import org.commonjava.maven.ext.common.util.PropertyResolver;
import org.commonjava.maven.ext.core.ManipulationSession;
import org.commonjava.maven.ext.io.PomIO;
import org.jboss.logging.Logger;
import picocli.CommandLine;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "generate-dependency-groups",
        description = "Generate dependency groups for validation purposes. (All dependencies in a group are expected to be of the same version.)")
public class GenerateDependencyGroupsCommand implements Callable<Integer> {

    protected static final Logger logger = Logger.getLogger(GenerateDependencyGroupsCommand.class);

    @CommandLine.Parameters(description = "Path(s) to the project pom file(s)",
            paramLabel = "pomFile", arity = "1..*")
    private File[] pomFiles;

    @CommandLine.Option(names = {"-o", "--output-file"}, defaultValue = "-")
    private File output;

    @CommandLine.Option(names = {"-e", "--equivalent"}, arity = "0..*",
            description = "prop1=prop2 pair meaning that group defined by a version property \"prop1\" should be merged into group defined by a version property \"prop2\".")
    private String[] equivalents;

    private PomIO pomIO;
    private ManipulationSession session;
    private final Map<String, String> equivalencyMapping = new HashMap<>();

    private void init() throws ComponentLookupException, PlexusContainerException {
        final DefaultContainerConfiguration config = new DefaultContainerConfiguration();
        config.setClassPathScanning(PlexusConstants.SCANNING_ON);
        config.setComponentVisibility(PlexusConstants.GLOBAL_VISIBILITY);
        config.setName("Wildfly Channel CLI");
        DefaultPlexusContainer container = new DefaultPlexusContainer(config);

        pomIO = container.lookup(PomIO.class);
        session = container.lookup(ManipulationSession.class);

        if (equivalents != null) {
            for (String option : equivalents) {
                String[] split = option.split("=");
                if (split.length != 2 || split[0].isBlank() || split[1].isBlank()) {
                    throw new IllegalArgumentException("Invalid equivalency option: " + option);
                }
                equivalencyMapping.put(split[0], split[1]);
            }
        }
    }

    @Override
    public Integer call() throws Exception {
        init();

        Map<String, DependencyGroup> depsPerVersionProperty = new TreeMap<>();

        for (File pomFile: pomFiles) {
            List<Project> projects = pomIO.parseProject(pomFile);
            for (Project project : projects) {
                DependencyManagement dependencyManagement = project.getModel().getDependencyManagement();
                if (dependencyManagement != null) {
                    for (Dependency dep : dependencyManagement.getDependencies()) {
                        String groupId = dep.getGroupId();
                        if (groupId.startsWith("${")) {
                            groupId = PropertyResolver.resolveInheritedProperties(session, project, groupId);
                        }

                        String artifactId = dep.getArtifactId();
                        if (artifactId.startsWith("${")) {
                            artifactId = PropertyResolver.resolveInheritedProperties(session, project, artifactId);
                        }

                        if (!dep.getVersion().startsWith("${")) {
                            logger.errorf("Dependency %s:%s:%s does not use property version, group cannot be determined.",
                                    groupId, dep.getArtifactId(), dep.getVersion());
                            continue;
                        }

                        final String versionProperty = dep.getVersion().substring(2, dep.getVersion().length() - 1);
                        final String resolvedVersion = PropertyResolver.resolveInheritedProperties(session, project,
                                dep.getVersion());
                        final String bucket = equivalencyMapping.getOrDefault(versionProperty, versionProperty);
                        DependencyGroup dependencyGroup = depsPerVersionProperty.computeIfAbsent(bucket,
                                b -> new DependencyGroup(versionProperty));
                        dependencyGroup.getDependencies().add(groupId + ":" + artifactId + ":" + resolvedVersion);
                    }
                }
            }
        }

        YAMLMapper mapper = new YAMLMapper();
        if (output.getPath().equals("-")) {
            System.out.println(mapper.writeValueAsString(new ArrayList<>(depsPerVersionProperty.values())));
        } else {
            mapper.writeValue(output, new ArrayList<>(depsPerVersionProperty.values()));
        }

        return null;
    }
}
