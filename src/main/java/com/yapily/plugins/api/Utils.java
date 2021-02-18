package com.yapily.plugins.api;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.twdata.maven.mojoexecutor.MojoExecutor;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
class Utils {
    final Path SPEC_PARENT_RELATIVE_TO_PROJECT = Path.of("yapily-api");

    Path getPath(YapilyApi api, MavenProject project) {
        return getSpecParent(project).resolve(api.getLocalGitRepositoryFolderName());
    }

    Path getSpec(YapilyApi api, MavenProject project) {
        return getPath(api, project).resolve("api.yml");
    }

    /**
     * Gets spec path relative to project root<br/>
     * As opposed to project build directory, like below: <br/>
     * <code>Path.of(project.getBuild().getDirectory()).resolve("yapily-api");</code>
     *
     * @param project to resolve against
     * @return folder containing the yapil-api-spec local repository
     */
    Path getSpecParent(MavenProject project) {
        return project.getBasedir().toPath().resolve(SPEC_PARENT_RELATIVE_TO_PROJECT);
    }

    void cleanSpecLocalGitRepository(YapilyApi api, MavenProject project) throws IOException {
        cleanDirectoryIfExists(getPath(api, project));
    }

    void cleanSpecParent(MavenProject project) throws IOException {
        cleanDirectoryIfExists(getSpecParent(project));
    }

    boolean cleanDirectoryIfExists(Path path) throws IOException {
        boolean exists = Files.exists(path);
        if (exists) cleanDirectory(path);

        return exists;
    }

    static void cleanDirectory(Path path) throws IOException {
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }

    static void fetchApi(YapilyApi api, MavenProject project) throws IOException, GitAPIException {
        Path outputPath = getPath(api, project);
        String apiGitUrl = api.getGitUrl();

        if (cleanDirectoryIfExists(outputPath)) {
            log.info("Cleaned up invalid git repository at: {}", outputPath);
        }

        log.info("Cloning {} into {}", apiGitUrl, outputPath);
        Git.cloneRepository()
           .setURI(apiGitUrl)
           .setBranch("refs/tags/" + api.getVersionTag())
           .setDirectory(outputPath.toFile())
           .call()
           .close();
    }

    static boolean isGitRepository(Path p) {
        if (!Files.isDirectory(p)) return false;
        var dir = p.toFile();
        return new RepositoryBuilder()
                       .addCeilingDirectory(dir)
                       .findGitDir(dir)
                       .getGitDir() != null;
    }

    static void cleanServerStubbing(MavenProject project) throws IOException {
        cleanDirectoryIfExists(getGeneratedSourcesDirectory(project));
    }

    private static Path getGeneratedSourcesDirectory(MavenProject project) {
        return Path.of(project.getBuild().getDirectory())
                   .resolve("generated-sources")
                   .resolve("yapily-api");
    }

    static Plugin getOpenApiplugin(String openapiGeneratorVersion) {
        return MojoExecutor.plugin("org.openapitools", "openapi-generator-maven-plugin", openapiGeneratorVersion);
    }
}
