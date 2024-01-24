package org.jboss.set.channel.cli.report;

import j2html.tags.ContainerTag;
import j2html.tags.DomContent;
import org.apache.commons.lang3.tuple.Pair;
import org.jboss.logging.Logger;
import org.jboss.set.channel.cli.utils.VersionUtils;
import org.wildfly.channel.MavenArtifact;
import org.wildfly.channel.Repository;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static j2html.TagCreator.caption;
import static j2html.TagCreator.div;
import static j2html.TagCreator.each;
import static j2html.TagCreator.h2;
import static j2html.TagCreator.li;
import static j2html.TagCreator.p;
import static j2html.TagCreator.rawHtml;
import static j2html.TagCreator.span;
import static j2html.TagCreator.table;
import static j2html.TagCreator.tbody;
import static j2html.TagCreator.td;
import static j2html.TagCreator.text;
import static j2html.TagCreator.th;
import static j2html.TagCreator.thead;
import static j2html.TagCreator.tr;
import static j2html.TagCreator.ul;

public class FormattingReportBuilder {

    private static final Logger log = Logger.getLogger(FormattingReportBuilder.class);

    protected static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final String BASIC_STYLES = "font-family: Verdana,sans-serif;" +
            "font-size: 10pt;";
    private static final String BOLD_FONT = "font-weight: bold;";
    private static final String TABLE_STYLES = "margin: 2em 0;" +
            "border-collapse: collapse;";
    private static final String CAPTION_STYLES = "text-align: left;" +
            BOLD_FONT;
    private static final String PADDING = "padding: 5px;";
    private static final String BORDER_TOP = "border-top: 1px solid #ddd;";
    private static final String TH_TD_STYLES = "padding: 5px;" +
            "text-align: left;";
    private static final String GREY_TEXT = "color: #999;";
    private static final String SUBITEM_STYLES = "padding-left: 2em;" + GREY_TEXT;
    private static final String GAV_STYLES = "font-family: \"Courier New\";";
    private static final String UL_STYLES = "list-style-type: circle;";
    private static final String LI_STYLES = "margin: 7px 0;";
    private static final String REPO_LABEL_STYLES = "border-radius: 5px;" +
            "padding: 3px; margin-left: 1em;";

    private static final String BG1 = "background-color: #a8df65;";
    private static final String BG2 = "background-color: #edf492;";
    private static final String BG3 = "background-color: #efb960;";
    private static final String BG4 = "background-color: #ee91bc;";
    private static final String[] BACKGROUNDS = {BG1, BG2, BG3, BG4};

    private List<Repository> repositories;
    private List<String> repositoryIds = Collections.emptyList();
    private List<Pair<MavenArtifact, List<String>>> upgrades;
    private HashMap<MavenArtifact, Integer> aggregatedCounter;
    private Map<MavenArtifact, Map<String, String>> artifactsToRepositoryMap = Collections.emptyMap();

    public FormattingReportBuilder withRepositories(List<Repository> remoteRepositories) {
        this.repositories = remoteRepositories;
        this.repositoryIds = remoteRepositories.stream().map(Repository::getId).toList();
        return this;
    }

    public FormattingReportBuilder withUpgrades(List<Pair<MavenArtifact, List<String>>> upgrades) {
        this.upgrades = upgrades;
        return this;
    }

    public FormattingReportBuilder withArtifactToRepositoryMap(Map<MavenArtifact, Map<String, String>> artifactsToRepositoryMap) {
        this.artifactsToRepositoryMap = artifactsToRepositoryMap;
        return this;
    }

    public String build() {
        if (upgrades.isEmpty()) {
            log.info("No components to upgrade.");
            return null;
        }

        List<Pair<MavenArtifact, List<String>>> sortedUpgrades =
                upgrades.stream().sorted(AlphabeticalComparator.INSTANCE)
                        .toList();

        List<Pair<MavenArtifact, List<String>>> aggregatedUpgrades = new ArrayList<>();
        aggregatedCounter = new HashMap<>();
        for (Pair<MavenArtifact, List<String>> pair : sortedUpgrades) {
            MavenArtifact a1 = pair.getLeft();
            List<String> versions = pair.getRight();

            Optional<Pair<MavenArtifact, List<String>>> found = aggregatedUpgrades.stream()
                    .filter(p -> p.getLeft().getGroupId().equals(a1.getGroupId())
                            && p.getLeft().getVersion().equals(a1.getVersion())
                            && p.getRight().containsAll(versions))
                    .findAny();
            if (found.isEmpty()) {
                aggregatedUpgrades.add(pair);
            } else {
                aggregatedCounter.compute(found.get().getLeft(),
                        (a, i) -> i == null ? 1 : ++i);
            }
        }

        return div().withStyle(BASIC_STYLES).with(
                h2("Component Upgrade Report"),
                p("Following repositories were searched:"),
                ul().withStyle(UL_STYLES).with(
                        each(repositories,
                                entry -> li().withStyle(LI_STYLES).with(
                                        span(entry.getId())
                                                .withStyle(REPO_LABEL_STYLES + repositoryColor(entry.getId())),
                                        text(" " + entry.getUrl())
                                ))
                ),
                table().withStyle(BASIC_STYLES + TABLE_STYLES).with(
                        caption("Possible Component Upgrades").withStyle(CAPTION_STYLES),
                        thead(tr().with(tableHeaders())),
                        each(aggregatedUpgrades, this::tableData),
                        tr(td(aggregatedUpgrades.size() + " items").withStyle(TH_TD_STYLES + BORDER_TOP).attr("colspan", "4"))),
                p("Generated on " + DATE_FORMATTER.format(ZonedDateTime.now()))/*,
                    p().withStyle(FOOTER_STYLES).with(
                            text("Report generated by "),
                            a("Maven Dependency Updater")
                                    .withHref(PROJECT_URL)
                                    .withStyle(FOOTER_STYLES)
                    )*/
        ).render();
    }


    private DomContent[] tableHeaders() {
        ArrayList<DomContent> headers = new ArrayList<>();
        headers.add(th("GAV").withStyle(TH_TD_STYLES));
        headers.add(th("New Version").withStyle(TH_TD_STYLES));
//        headers.add(th("Repository").withStyle(TH_TD_STYLES));
        /*if (configuration.getLogger().isSet()) {
            headers.add(th("Since").withStyle(TH_TD_STYLES));
        }*/
        return headers.toArray(new DomContent[]{});
    }

    private DomContent tableData(Pair<MavenArtifact, List<String>> upgrade) {
        final MavenArtifact artifact = upgrade.getLeft();
        final ContainerTag<?> tbody = tbody();

        boolean first = true;
        for (String version : upgrade.getRight()) {
            final String repoId = artifactsToRepositoryMap.getOrDefault(artifact, Collections.emptyMap()).get(version);

            ArrayList<DomContent> cells = new ArrayList<>();
            if (first) {
                cells.add(td(artifact.getGroupId()
                        + ":" + artifact.getArtifactId()
                        + ":" + artifact.getVersion())
                        .withStyle(PADDING + GAV_STYLES));
                cells.add(td().with(
                        span(version).withStyle(VersionUtils.isTheSameMinor(artifact.getVersion(), version) ? BOLD_FONT : ""),
                        repoId != null ? span(repoId).withStyle(REPO_LABEL_STYLES + repositoryColor(repoId)) : span()
                ).withStyle(PADDING));
            } else {
                cells.add(td(rawHtml("&#8627;")).withStyle(SUBITEM_STYLES));
                cells.add(td().with(
                        span(version).withStyle(VersionUtils.isTheSameMinor(artifact.getVersion(), version) ? BOLD_FONT : ""),
                        repoId != null ? span(repoId).withStyle(REPO_LABEL_STYLES + repositoryColor(repoId)) : span()
                ).withStyle(PADDING));
            }
            tbody.with(tr().with(cells).withStyle(BORDER_TOP));

            first = false;
        }

        Integer counter = aggregatedCounter.get(artifact);
        if (counter != null && counter > 0) {
            tbody.with(tr().with(td(counter + " more artifacts from the same groupId")
                    .withStyle(SUBITEM_STYLES)));
        }

        return tbody;
    }

    private static class AlphabeticalComparator implements Comparator<Pair<MavenArtifact, List<String>>> {

        static final AlphabeticalComparator INSTANCE = new AlphabeticalComparator();

        @Override
        public int compare(Pair<MavenArtifact, List<String>> u1, Pair<MavenArtifact, List<String>> u2) {
            int diff = u1.getLeft().getGroupId().compareTo(u2.getLeft().getGroupId());
            if (diff == 0) {
                diff = u1.getLeft().getArtifactId().compareTo(u2.getLeft().getArtifactId());
            }
            if (diff == 0) {
                diff = u1.getLeft().getVersion().compareTo(u2.getLeft().getVersion());
            }
            return diff;
        }
    }

    private String repositoryColor(String key) {
        int idx = repositoryIds.indexOf(key);
        return BACKGROUNDS[idx % BACKGROUNDS.length];
    }

}
