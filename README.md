# wildfly-channel-reports

Comparison and report generation tool for [Wildfly Channels](https://github.com/wildfly-extras/wildfly-channel).

## Usage

For general usage information, run:

```shell
java -jar path/to/wildfly-channel-reports-*-jar-with-dependencies.jar --help
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

Example command:

```shell
java -jar target/wildfly-channel-reports-*-jar-with-dependencies.jar \
  find-upgrades file:base-channel.yaml \
  --repositories mrrc::https://maven.repository.redhat.com/ga/ \
  --exclude-pattern "[.-]fuse-" \
  --include-pattern "[.-]redhat-"
```