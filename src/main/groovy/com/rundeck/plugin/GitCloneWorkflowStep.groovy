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


@Plugin(name = GitCloneWorkflowStep.PROVIDER_NAME, service = ServiceNameConstants.WorkflowStep)
@PluginDescription(title = GitCloneWorkflowStep.PROVIDER_TITLE, description = GitCloneWorkflowStep.PROVIDER_DESCRIPTION)
class GitCloneWorkflowStep implements StepPlugin, Describable{
    public static final String PROVIDER_NAME = "git-clone-step"
    public static final String PROVIDER_TITLE = "Git / Clone"
    public static final String PROVIDER_DESCRIPTION ="Clone a Git repository on Rundeck server"

    public final static String GIT_URL="gitUrl"
    public final static String GIT_BASE_DIRECTORY="gitBaseDirectory"
    public final static String GIT_LOG_DISABLE ="gitLogDisable"
    public final static String GIT_BRANCH="gitBranch"
    public final static String GIT_HOSTKEY_CHECKING="strictHostKeyChecking"
    public final static String GIT_KEY_STORAGE="gitKeyPath"
    public final static String GIT_PASSWORD_STORAGE="gitPasswordPath"
    public final static String GIT_PROJECT_BASED_SUBDIRECTORY="gitUseProjectBasedSubdirectory"


    final static Map<String, Object> renderingOptionsAuthentication = GitPluginUtil.getRenderOpt("Authentication", false)
    final static Map<String, Object> renderingOptionsAuthenticationPassword = GitPluginUtil.getRenderOpt("Authentication",false, false, true)
    final static Map<String, Object> renderingOptionsAuthenticationKey = GitPluginUtil.getRenderOpt("Authentication",false, false, false, true)

    final static Map<String, Object> renderingOptionsConfig = GitPluginUtil.getRenderOpt("Configuration",false)

    GitManager gitManager

    static Description DESCRIPTION = DescriptionBuilder.builder()
                                                       .name(PROVIDER_NAME)
                                                       .title(PROVIDER_TITLE)
                                                       .description(PROVIDER_DESCRIPTION)
                                                       .property(
            PropertyUtil.string(GIT_BASE_DIRECTORY, "Base Directory", "Directory for checkout.", true,
                                null, null, null, renderingOptionsConfig))
                                                       .property(PropertyUtil.string(GIT_URL, "Git URL", '''Checkout url.
See [git-clone](https://www.kernel.org/pub/software/scm/git/docs/git-clone.html)
specifically the [GIT URLS](https://www.kernel.org/pub/software/scm/git/docs/git-clone.html#URLS) section.
Some examples:
* `ssh://[user@]host.xz[:port]/path/to/repo.git/`
* `git://host.xz[:port]/path/to/repo.git/`
* `http[s]://host.xz[:port]/path/to/repo.git/`
* `ftp[s]://host.xz[:port]/path/to/repo.git/`
* `rsync://host.xz/path/to/repo.git/`''', true,
                                                                                     null,null,null, renderingOptionsConfig))
                                                       .property(PropertyUtil.string(GIT_BRANCH, "Branch", "Checkout branch.", true,
                                                                                     "master",null,null, renderingOptionsConfig))
                                                       .property(PropertyUtil.bool(GIT_LOG_DISABLE, "Disable log output", "Enabling this flag, the plugin will not show the output log", true,
                                                                                     "false",null, renderingOptionsConfig))
                                                       .property(PropertyUtil.string(GIT_PASSWORD_STORAGE, "Git Password", 'Password to authenticate remotely', false,
                                                                                     null,null,null, renderingOptionsAuthenticationPassword))
                                                       .property(PropertyUtil.select(GIT_HOSTKEY_CHECKING, "SSH: Strict Host Key Checking", '''Use strict host key checking.
If `yes`, require remote host SSH key is defined in the `~/.ssh/known_hosts` file, otherwise do not verify.''', false,
                                                                                     "yes",GitResourceModelFactory.LIST_HOSTKEY_CHECKING,null, renderingOptionsAuthentication))
    .property(PropertyUtil.string(GIT_KEY_STORAGE, "SSH Key Path", 'SSH Key Path', false,
                                                                                     null,null,null, renderingOptionsAuthenticationKey))
    .property(PropertyUtil.bool(GIT_PROJECT_BASED_SUBDIRECTORY, "Use per-project subdirectories", "Check repositories out in project-based subdirectories of the Rundeck home directory.",
    false, "false", PropertyScope.Project, renderingOptionsConfig))
                                                       .build()

    GitCloneWorkflowStep() {
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

        if(configuration.get(GIT_PASSWORD_STORAGE)){
            def password = GitPluginUtil.getFromKeyStorage(configuration.get(GIT_PASSWORD_STORAGE), context)
            gitManager.setGitPassword(password)
        }

        if(configuration.get(GIT_KEY_STORAGE)){
            def key = GitPluginUtil.getFromKeyStorage(configuration.get(GIT_KEY_STORAGE), context)
            gitManager.setSshPrivateKey(key)
        }

        ExecutionListener logger = context.getExecutionContext().getExecutionListener()
        logger.log(3, "Cloning Repo ${gitManager.gitURL} to local path ${localPath}")

        File base = new File(localPath)

        if(!base){
            base.mkdir()
        }

        Map<String, String> meta = new HashMap<>();
        meta.put("content-data-type", "application/json");

        try{
            gitManager.cloneOrCreate(base)

            if (!Boolean.parseBoolean((String) configuration.get(GIT_LOG_DISABLE))) {
                def jsonMap = base.listFiles().collect { file ->
                    return [name: file.name, directory: file.directory, file: file.file, path: file.absolutePath]
                }
                def json = JsonOutput.toJson(jsonMap)
                logger.log(2, json, meta)
            }

        }catch(Exception e){
            logger.log(0, e.getMessage())
            throw new StepException("Error with Authentication ${e.getMessage()}", GitFailureReason.AuthenticationError)

        }



    }
}
