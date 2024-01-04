# wildfly-channel-reports
Comparison and report generation tool for wildfly-channel manifests.

## Example Usage

```shell
java -jar target/wildfly-channel-reports-*-jar-with-dependencies.jar \
  find-upgrades file:base-channel.yaml \
  --repositories=mrrc::https://maven.repository.redhat.com/ga/
```
    