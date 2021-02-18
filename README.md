# `yapily-api-maven-plugin`

Automatically handles API server stub generation

# Usage 

## Adding plugin to pom.xml
```xml
<plugin>
    <groupId>com.yapily</groupId>
    <artifactId>yapily-api-maven-plugin</artifactId>
    <configuration>
        <apiType>yapily-platform-security</apiType>
        <apiVersion>0.0.1</apiVersion>
    </configuration>
</plugin>
```

## Steps
* Note: these steps automatically run during the natural project life-cycle (so you do not need to explicitly execute them to get started)

### generate
```shell
mvn yapily-api:generate
```
Fetches (cached) API, before generating the server stubbing using the OpenAPI spec

### fetch
```shell
mvn yapily-api:fetch
```
Fetches API from yapily-api bitbucket repository

### Clean
```shell
mvn yapily-api:clean
```
- Cleans the downloaded specifications (not automatically cleaned during the `clean` phase, to allow offline cleaning/compilation)
- Auto-generated artifacts are automatically .gitignored by the plugin
