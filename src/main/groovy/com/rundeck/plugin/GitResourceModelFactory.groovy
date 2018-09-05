package com.rundeck.plugin

import com.dtolabs.rundeck.core.common.Framework
import com.dtolabs.rundeck.core.plugins.Plugin
import com.dtolabs.rundeck.core.plugins.configuration.ConfigurationException
import com.dtolabs.rundeck.core.plugins.configuration.Describable
import com.dtolabs.rundeck.core.plugins.configuration.Description
import com.dtolabs.rundeck.core.plugins.configuration.PropertyUtil
import com.dtolabs.rundeck.core.resources.ResourceModelSource
import com.dtolabs.rundeck.core.resources.ResourceModelSourceFactory
import com.dtolabs.rundeck.plugins.ServiceNameConstants
import com.dtolabs.rundeck.plugins.descriptions.PluginDescription
import com.dtolabs.rundeck.plugins.util.DescriptionBuilder
import com.rundeck.plugin.util.GitPluginUtil

/**
 * Created by luistoledo on 12/18/17.
 */
@Plugin(name = GitResourceModelFactory.PROVIDER_NAME, service = ServiceNameConstants.ResourceModelSource)
@PluginDescription(title = GitResourceModelFactory.PROVIDER_TITLE, description = GitResourceModelFactory.PROVIDER_DESCRIPTION)
class GitResourceModelFactory implements ResourceModelSourceFactory,Describable {

    private Framework framework;

    public static final String PROVIDER_NAME = "git-resource-model";
    public static final String PROVIDER_TITLE = "Git / Resource Model"
    public static final String PROVIDER_DESCRIPTION ="Writable Resource Model on a Git repository"

    public static final List<String> LIST_HOSTKEY_CHECKING =['yes', 'no']
    public static final List<String> LIST_FILE_TYPE =['xml', 'yaml','json']

    public final static String GIT_URL="gitUrl"
    public final static String GIT_BASE_DIRECTORY="gitBaseDirectory"
    public final static String GIT_FILE="gitFile"
    public final static String GIT_FORMAT_FILE="gitFormatFile"
    public final static String GIT_BRANCH="gitBranch"
    public final static String GIT_HOSTKEY_CHECKING="strictHostKeyChecking"
    public final static String GIT_KEY_STORAGE="gitKeyPath"
    public final static String GIT_PASSWORD_STORAGE="gitPasswordPath"
    public static final String WRITABLE="writable";


    final static Map<String, Object> renderingOptionsAuthentication = GitPluginUtil.getRenderOpt("Authentication",false)
    final static Map<String, Object> renderingOptionsAuthenticationPassword = GitPluginUtil.getRenderOpt("Authentication",false, true)
    final static Map<String, Object> renderingOptionsConfig = GitPluginUtil.getRenderOpt("Configuration",false)

    GitResourceModelFactory(Framework framework) {
        this.framework = framework
    }

    static Description DESCRIPTION = DescriptionBuilder.builder()
            .name(PROVIDER_NAME)
            .title(PROVIDER_TITLE)
            .description(PROVIDER_DESCRIPTION)
            .property(PropertyUtil.string(GIT_BASE_DIRECTORY, "Base Directory", "Directory for checkout.", true,
            null,null,null, renderingOptionsConfig))
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
            .property(PropertyUtil.string(GIT_FILE, "Resource model File", "Resource model file inside the github repo.", true,
            null,null,null, renderingOptionsConfig))
            .property(PropertyUtil.select(GIT_FORMAT_FILE, "File Format", 'File Format', true,
            "xml",GitResourceModelFactory.LIST_FILE_TYPE,null, renderingOptionsConfig))
            .property(PropertyUtil.bool(WRITABLE, "Writable",
            "Allow to write the remote file.",
            false,"false",null,renderingOptionsConfig))
            .property(PropertyUtil.string(GIT_PASSWORD_STORAGE, "Git Password", 'Password to authenticate remotely', false,
            null,null,null, renderingOptionsAuthenticationPassword))
            .property(PropertyUtil.select(GIT_HOSTKEY_CHECKING, "SSH: Strict Host Key Checking", '''Use strict host key checking.
If `yes`, require remote host SSH key is defined in the `~/.ssh/known_hosts` file, otherwise do not verify.''', false,
            "yes",GitResourceModelFactory.LIST_HOSTKEY_CHECKING,null, renderingOptionsAuthentication))
            .property(PropertyUtil.string(GIT_KEY_STORAGE, "SSH Key Path", 'SSH Key Path', false,
            null,null,null, renderingOptionsAuthentication))
            .build()



    @Override
    Description getDescription() {
        return DESCRIPTION
    }

    @Override
    ResourceModelSource createResourceModelSource(Properties configuration) throws ConfigurationException {
        final GitResourceModel resource = new GitResourceModel(configuration,framework)

        return resource
    }
}
