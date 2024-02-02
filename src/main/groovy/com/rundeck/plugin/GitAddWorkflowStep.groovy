package com.rundeck.plugin

import com.dtolabs.rundeck.core.execution.ExecutionListener
import com.dtolabs.rundeck.core.execution.workflow.steps.StepException
import com.dtolabs.rundeck.core.plugins.Plugin
import com.dtolabs.rundeck.core.plugins.configuration.Describable
import com.dtolabs.rundeck.core.plugins.configuration.Description
import com.dtolabs.rundeck.core.plugins.configuration.PropertyUtil
import com.dtolabs.rundeck.plugins.ServiceNameConstants
import com.dtolabs.rundeck.core.plugins.configuration.PropertyScope
import com.dtolabs.rundeck.plugins.descriptions.PluginDescription
import com.dtolabs.rundeck.plugins.step.PluginStepContext
import com.dtolabs.rundeck.plugins.step.StepPlugin
import com.dtolabs.rundeck.plugins.util.DescriptionBuilder
import com.rundeck.plugin.util.GitPluginUtil
import java.nio.file.Path
import java.nio.file.Paths

import groovy.json.JsonOutput


@Plugin(name = GitAddWorkflowStep.PROVIDER_NAME, service = ServiceNameConstants.WorkflowStep)
@PluginDescription(title = GitAddWorkflowStep.PROVIDER_TITLE, description = GitAddWorkflowStep.PROVIDER_DESCRIPTION)
class GitAddWorkflowStep implements StepPlugin, Describable{
    public static final String PROVIDER_NAME = "git-add-step"
    public static final String PROVIDER_TITLE = "Git / Add"
    public static final String PROVIDER_DESCRIPTION ="Add to a Git repository on Rundeck server"

    public final static String GIT_BASE_DIRECTORY="gitBaseDirectory"
    public final static String GIT_LOG_DISABLE ="gitLogDisable"
    public final static String GIT_ADD_FILE_PATTERN="gitAddFilePattern"
    public final static String GIT_PROJECT_BASED_SUBDIRECTORY="gitUseProjectBasedSubdirectory"

    final static Map<String, Object> renderingOptionsConfig = GitPluginUtil.getRenderOpt("Configuration",false)

    GitManager gitManager

    static Description DESCRIPTION = DescriptionBuilder.builder()
                                                       .name(PROVIDER_NAME)
                                                       .title(PROVIDER_TITLE)
                                                       .description(PROVIDER_DESCRIPTION)
                                                       .property(
            PropertyUtil.string(GIT_BASE_DIRECTORY, "Base Directory", "Directory to add to.", true,
                                null, null, null, renderingOptionsConfig))
                                                       .property(PropertyUtil.string(GIT_ADD_FILE_PATTERN, "File Pattern", '''File Pattern of files to be added.
See [addFilepattern](http://archive.eclipse.org/jgit/docs/jgit-2.0.0.201206130900-r/apidocs/org/eclipse/jgit/api/AddCommand.html#addFilepattern(java.lang.String)) for more details.''', true,
                                                                                     ".",null,null, renderingOptionsConfig))
                                                       .property(PropertyUtil.bool(GIT_LOG_DISABLE, "Disable log output", "Enabling this flag, the plugin will not show the output log", true,
                                                                                     "false",null, renderingOptionsConfig))
    .property(PropertyUtil.bool(GIT_PROJECT_BASED_SUBDIRECTORY, "Use per-project subdirectories", "Check repositories out in project-based subdirectories of the Rundeck home directory.",
    false, "false", PropertyScope.Project, renderingOptionsConfig))
                                                       .build()

    GitAddWorkflowStep() {
    }

    @Override
    Description getDescription() {
        return DESCRIPTION
    }

    @Override
    void executeStep(final PluginStepContext context, final Map<String, Object> configuration) throws StepException {
        Properties proConfiguration = new Properties()
        proConfiguration.putAll(configuration)

        if(gitManager==null){
            gitManager = new GitManager(proConfiguration)
        }

        String localPath

        if (Boolean.parseBoolean((String) configuration.get(GIT_PROJECT_BASED_SUBDIRECTORY))) {
            String configPath = configuration.get(GIT_BASE_DIRECTORY)
            String project = context.getFrameworkProject()
            Path localPath_p = Paths.get(project, configPath)
            localPath = localPath_p.toString()
        } else {
            localPath = configuration.get(GIT_BASE_DIRECTORY)
        }

        ExecutionListener logger = context.getExecutionContext().getExecutionListener()
        logger.log(3, "Cloning Repo ${gitManager.gitURL} to local path ${localPath}")

        File base = new File(localPath)

        Map<String, String> meta = new HashMap<>();
        meta.put("content-data-type", "application/json");

        try{
            gitManager.add(base, configuration.get(GIT_ADD_FILE_PATTERN))

            if (!Boolean.parseBoolean((String) configuration.get(GIT_LOG_DISABLE))) {
                def jsonMap = base.listFiles().collect { file ->
                    return [name: file.name, directory: file.directory, file: file.file, path: file.absolutePath]
                }
                def json = JsonOutput.toJson(jsonMap)
                logger.log(2, json, meta)
            }

        }catch(Exception e){
            logger.log(0, e.getMessage())
            throw new StepException("Error ${op} VM.", GitFailureReason.AuthenticationError)

        }



    }
}
