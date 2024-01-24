# wildfly-channel-reports

Manipulation and report generation tool for [Wildfly Channels](https://github.com/wildfly-extras/wildfly-channel).

## Usage

Execute the fat JAR with `--help` option to get up-to-date help message:

```text
$ java -jar path/to/wildfly-channel-reports-*-jar-with-dependencies.jar --help
Usage: <main class> [COMMAND]
Commands:
  compare-channels           Generates report that identifies intersecting
                               streams of two given channels, and highlights
                               streams where their versions differ.
  find-upgrades              Generates report showing possible upgrades for
                               streams in given channel by directly querying
                               given Maven repositories. This also generates
                               two manifest files, diff-manifest.yaml and
                               upgraded-manifest.yaml, containing upgraded
                               streams and all streams with upgraded versions
                               respectively.
  create-manifest-from-repo  Scans a local maven repository and creates a
                               manifest file representing the GAVs existing in
                               the repository.
  create-channel             Creates a channel file according to given
                               parameters.
  merge-manifests            Merges two manifest. The second manifest streams
                               always override the first manifest streams.

```

### `find-upgrades` command

This command compares a Wildfly Channel against given Maven repositories. I.e. it reports what streams in the channel 
could be upgraded to a newer version. 

```shell
java -jar path/to/wildfly-channel-reports-*-jar-with-dependencies.jar \
  find-upgrades "channel-url-or-maven-gav" \
  --repositories "repo1-id::repo1-url,..." \
  [--exclude-pattern "exclude-version-regexp"] \
  [--include-pattern "include-version-regexp"] \
  [--blocklist-coordinate "blocklist-ulr-or-maven-gav"]
```

Example command invocation:

```shell
java -jar target/wildfly-channel-reports-*-jar-with-dependencies.jar \
  find-upgrades file:base-channel.yaml \
  --repositories mrrc::https://maven.repository.redhat.com/ga/ \
  --exclude-pattern "[.-]fuse-" \
  --include-pattern "[.-]redhat-"
```