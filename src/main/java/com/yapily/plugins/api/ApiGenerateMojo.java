package com.yapily.plugins.api;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.jgit.lib.RepositoryBuilder;

import lombok.extern.slf4j.Slf4j;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;

@Slf4j
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class ApiGenerateMojo extends AbstractMojo {
    public static final String GITIGNORE_ENTRIES_SUFFIX = "### END OF AUTO-GENERATION yapily-api-maven-plugin";

    @Parameter
    Map<String, Object> serverApi;
    @Parameter
    Map<String, Object> clientApi;
    @Parameter
    String gitUrl;
    @Parameter(defaultValue = "true")
    boolean autoGitignore;
    @Parameter(property = "version.openapi-generator", defaultValue = "5.0.1")
    String openapiGeneratorVersion;
    @Parameter
    private Map<?, ?> openapiConfigurationOverrides;
    @Component
    private MavenProject project;
    @Component
    private MavenSession mavenSession;
    @Component
    private BuildPluginManager pluginManager;

    @Override
    public void execute() throws MojoExecutionException {
        if (serverApi != null) {
            generate(serverApi, "/openapi-generator.configuration.server.xml");
        }
        if (clientApi != null) {
            var compileSourceRoot = generate(clientApi, "/openapi-generator.configuration.client.xml");
            try {
                Utils.cleanDirectoryIfExists(compileSourceRoot.resolve("test"));
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to clean generated tests: " + e.getClass().getName() + ": " + e.getMessage(), e);
            }
        }


        if (autoGitignore) {
            try {
                autoGitIgnoreArtifacts();
            } catch (IOException e) {
                log.debug("Failed to automatically ignore fetched specs", e);
            }
        }
    }

    private Path generate(Map<String, Object> apiParam, String s) throws MojoExecutionException {
        var api = new YapilyApi((String) apiParam.get("type"), (String) apiParam.get("version"));
        var localSpecPath = (String) apiParam.get("localSpecPath");

        String specPath = localSpecPath != null ? localSpecPath : Utils.getSpec(api, project).toString();
        var configuration = configuration(specPath, s);

        if (localSpecPath == null) {
            fetchApi(api);
        }

        log.debug("Generating stubbing using configuration: {}", configuration);
        try {
            executeMojo(
                    plugin("org.openapitools", "openapi-generator-maven-plugin", openapiGeneratorVersion),
                    goal("generate"),
                    configuration,
                    executionEnvironment(project, mavenSession, pluginManager)
            );
        } catch (MojoExecutionException e) {
            log.error("Failed to generate server stubbing", e);
            throw e;
        }

        var compileSourceRoot = Utils.getCompileSourceRoot(project);
        log.debug("Adding compile source root: {}", compileSourceRoot);

        project.addCompileSourceRoot(compileSourceRoot.toString());

        return compileSourceRoot;
    }

    private void autoGitIgnoreArtifacts() throws IOException {
        var nearestGitDir = new RepositoryBuilder()
                .findGitDir(project.getBasedir())
                .getGitDir();

        if (nearestGitDir != null) {
            var gitRepo = nearestGitDir.getParentFile().toPath();
            var gitIgnorePath = gitRepo.resolve(".gitignore");

            if (Files.exists(gitIgnorePath)) {
                var versionedGitignoreEntriesPrefix = "### AUTO-GENERATED BY yapily-api-maven-plugin " + getClass().getPackage().getImplementationVersion();
                var specParentIgnoreEntry = gitRepo
                        .relativize(Utils.getSpecParent(project))
                        .toString()
                        .replace("\\", "/");
                var openapitoolsPath = project.getBasedir().toPath().resolve("openapitools.json");
                var openapitoolsEnrty = gitRepo
                        .relativize(openapitoolsPath)
                        .toString()
                        .replace("\\", "/");


                try (var br = new BufferedReader(new FileReader(gitIgnorePath.toFile()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.startsWith(versionedGitignoreEntriesPrefix)) return;
                    }
                }
                log.info("Appending generated yapily-api specifications folder to .gitignore of repository: {}", gitRepo);
                try (var os = new BufferedWriter(new FileWriter(gitIgnorePath.toFile(), true))) {
                    os.newLine();
                    os.write(versionedGitignoreEntriesPrefix);
                    os.newLine();
                    os.write(specParentIgnoreEntry);
                    os.newLine();
                    os.write(openapitoolsEnrty);
                    os.newLine();
                    os.write(GITIGNORE_ENTRIES_SUFFIX);
                }
            }
        }
    }

    private Xpp3Dom configuration(String specPath, String configResourcePath) throws MojoExecutionException {
        Xpp3Dom openapiMavenPluginConfiguration = readConfig(configResourcePath);
        var outputDirectory = Utils.getGeneratedSources(project);
        openapiMavenPluginConfiguration.addChild(element("output", outputDirectory.toString()).toDom());
        var configOptions = openapiMavenPluginConfiguration.getChild("configOptions");
        if (configOptions == null) throw new MojoExecutionException("Invalid openapi-generator configuration. configOptions missing");
        configOptions.addChild(element("sourceFolder", Utils.RELATIVE_GENERATED_SOURCE_FOLDER.toString()).toDom());

        //        if (openapiConfigurationOverrides != null) {
        //            log.info("Merging user-defined openapi-generator configuration");
        //            log.debug("\t config {}", openapiConfigurationOverrides);
        //
        //            openapiMavenPluginConfiguration = Xpp3Dom.mergeXpp3Dom(
        //                    openapiMavenPluginConfiguration,
        //                    Utils.buildElement((Map<String, Object>) openapiConfigurationOverrides, "configuration").toDom()
        //            );
        //        }

        // add the inputSpec (-i) path (from the yapily-api local-repo)
        openapiMavenPluginConfiguration.addChild(element("inputSpec", specPath).toDom());

        return openapiMavenPluginConfiguration;
    }

    private Xpp3Dom readConfig(String s) throws MojoExecutionException {
        Xpp3Dom openapiMavenPluginConfiguration;
        try (var is = getClass().getResourceAsStream(s)) {
            if (is == null) {
                throw new IOException("Failed to obtain embdedded openapi-generator configuration");
            }
            openapiMavenPluginConfiguration = Xpp3DomBuilder.build(is, StandardCharsets.UTF_8.toString());
        } catch (XmlPullParserException | IOException e) {
            log.error("Failed to parse embedded openapi-generator configuration", e);
            throw new MojoExecutionException("Failed to parse embedded openapi-generator configuration", e);
        }
        return openapiMavenPluginConfiguration;
    }

    private void fetchApi(YapilyApi api) throws MojoExecutionException {
        log.info("Checking cache for {}", api);

        if (Utils.isGitRepository(Utils.getApiRepositoryPath(api, project))) {
            log.info("\t{} already cached", api);
        } else {
            Utils.fetchApi(api, project, gitUrl);
        }
    }
}
