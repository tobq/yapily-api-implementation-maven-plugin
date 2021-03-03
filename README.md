# `yapily-api-implementation-maven-plugin`

Automatically handles API server stub generation, for service implementations

# Usage

## Adding plugin to pom.xml

```xml
<plugin>
    <groupId>com.yapily</groupId>
    <artifactId>yapily-api-implementation-maven-plugin</artifactId>
    <executions>
        <execution>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <apiType>yapily-platform-consent-service</apiType>
                <apiVersion>0.0.4</apiVersion>
            </configuration>
        </execution>
    </executions>
</plugin>
```
### Debug `<configuration/>` parameters
* You can use `<gitUrl/>` to override the default git repository to clone from
* You can also use `<localSpecPath/>` to skip cloning, and develop using a locally managed api

### Generate stubbing

```shell
mvn yapily-api-implementation:generate
```
Fetches (cached) API, before generating the server stubbing using the OpenAPI spec
- `generate` automatically runs during the `generate-sources` phase (so you do not need to explicitly execute them to get started)
- Auto-generated artifacts are automatically .gitignored by the plugin

### Fetch Specification

```shell
mvn yapily-api-implementation:fetch
```

Fetches API from yapily-api bitbucket repository

### Clean
```shell
mvn yapily-api-implementation:clean
```
Cleans the auto-generated server stubbing
- Automatically runs as part of the `clean` phase

### Clean Specifications
```shell
mvn yapily-api-implementation:clean-specs
```
Cleans the downloaded specifications
- This was left out of the `clean` phase, to allow one to clean and then recompile, while offline

