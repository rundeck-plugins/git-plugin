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


@Plugin(name = GitCommitWorkflowStep.PROVIDER_NAME, service = ServiceNameConstants.WorkflowStep)
@PluginDescription(title = GitCommitWorkflowStep.PROVIDER_TITLE, description = GitCommitWorkflowStep.PROVIDER_DESCRIPTION)
class GitCommitWorkflowStep implements StepPlugin, Describable{
    public static final String PROVIDER_NAME = "git-commit-step"
    public static final String PROVIDER_TITLE = "Git / Commit"
    public static final String PROVIDER_DESCRIPTION ="Commit a Git repository on Rundeck server"

    public final static String GIT_URL="gitUrl"
    public final static String GIT_BASE_DIRECTORY="gitBaseDirectory"
    public final static String GIT_LOG_DISABLE ="gitLogDisable"
    public final static String GIT_MESSAGE="gitMessage"
    public final static String GIT_ADD_ENABLE ="gitAddEnable"
    public final static String GIT_PUSH_ENABLE ="gitPushEnable"
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
            PropertyUtil.string(GIT_BASE_DIRECTORY, "Base Directory", "Directory to commit.", true,
                                null, null, null, renderingOptionsConfig))
                                                       .property(PropertyUtil.string(GIT_URL, "Git URL", '''Checkout url.
                                                        See [git-commit](https://www.kernel.org/pub/software/scm/git/docs/git-commit.html)
                                                        specifically the [GIT URLS](https://www.kernel.org/pub/software/scm/git/docs/git-push.html#URLS) section.
                                                        Some examples:
                                                        * `ssh://[user@]host.xz[:port]/path/to/repo.git/`
                                                        * `git://host.xz[:port]/path/to/repo.git/`
                                                        * `http[s]://host.xz[:port]/path/to/repo.git/`
                                                        * `ftp[s]://host.xz[:port]/path/to/repo.git/`
                                                        * `rsync://host.xz/path/to/repo.git/`''', true,
                                                                                     null,null,null, renderingOptionsConfig))
                                                       .property(PropertyUtil.string(GIT_MESSAGE, "Message", "Commit Message.", true,
                                                                                     "Rundeck Commit",null,null, renderingOptionsConfig))
                                                       .property(PropertyUtil.bool(GIT_ADD_ENABLE, "Add", "Enabling this flag will add all new files and changes to the commit before commiting", true,
                                                                                     "false",null, renderingOptionsConfig))
                                                       .property(PropertyUtil.bool(GIT_PUSH_ENABLE, "Push Commit", "Enabling this flag will push the commit upstream", true,
                                                                                     "false",null, renderingOptionsConfig))
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

    GitCommitWorkflowStep() {
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
        logger.log(3, "Committing to repo ${gitManager.gitURL} from local path ${localPath}")

        File base = new File(localPath)

        Map<String, String> meta = new HashMap<>();
        meta.put("content-data-type", "application/json");

        try{
            if (Boolean.parseBoolean((String) configuration.get(GIT_ADD_ENABLE))) {
                gitManager.add(base, ".")
            }

            gitManager.commit(base, configuration.get(GIT_MESSAGE))

            if (Boolean.parseBoolean((String) configuration.get(GIT_PUSH_ENABLE))) {
                gitManager.push(base)
            }

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
