package com.yapily.plugins.api;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Mojo(name = "fetch")
public class FetchMojo extends AbstractMojo {
    @Parameter(required = true)
    String apiVersion;
    @Parameter(required = true)
    String apiName;
    @Parameter(readonly = true)
    String gitUrlTemplate;
    @Parameter(defaultValue = Utils.DEFAULT_GIT_BRANCH_TEMPLATE)
    String gitBranchTemplate;
    @Component
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {
        var api = new YapilyApi(apiName, apiVersion);
        Utils.fetchApi(api, project, gitUrlTemplate, gitBranchTemplate);
    }
}

