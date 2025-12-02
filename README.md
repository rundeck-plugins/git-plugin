# Git Plugin

This is a git plugin (based on [Jgit](https://www.eclipse.org/jgit/)) which contains a Resource model and workflow steps.

For authentication, it can be use a private key or a password.
If the private key is not defined, then the password will be used. 

* The primary key will work with SSH protocol on the Git URL. 
* The password will work with http/https protocol on the Git URL (the most of the case, the username is needed on the URI, eg: `http[s]://username@host.xz[:port]/path/to/repo.git/`  when you use password authentication)


## Build

Run the following command to built the jar file:

```
./gradlew clean build
```

**Note:** This plugin requires Rundeck 5.16.0 or later.

## Install

Copy the `git-plugin-x.y.x.jar` file to the `$RDECK_BASE/libext/` directory inside your Rundeck installation.


## GIT Resource Model

This is a resource model plugin that uses Git to store resources model file.

### Configuration

You need to set up the following options to use the plugin:

![](images/resource_model.png)

### Repo Settings

* **Base Directory**: Directory for checkout
* **Git URL**: Checkout URL.
    See [git-clone](https://www.kernel.org/pub/software/scm/git/docs/git-clone.html)
    specifically the [GIT URLS](https://www.kernel.org/pub/software/scm/git/docs/git-clone.html#URLS) section.
    Some examples:
    * `ssh://[user@]host.xz[:port]/path/to/repo.git/`
    * `git://host.xz[:port]/path/to/repo.git/`
    * `http[s]://host.xz[:port]/path/to/repo.git/`
    * `ftp[s]://host.xz[:port]/path/to/repo.git/`
    * `rsync://host.xz/path/to/repo.git/`

* **Branch**: Checkout branch
* **Resource model File**: Resource model file inside the github repo. This is the file that will be added to Rundeck resource model.
* **File Format**:  File format of the resource model, it could be xml, yaml, json
* **Writable**: Allow to write the remote file

### Authentication

The plugin supports multiple authentication methods. **Key Storage is recommended** for secure credential management.

#### Password Authentication

##### Option 1: Key Storage (Recommended)
* **Git Password Storage Path**: Key storage path for Git password (secure)

**How to use:**
1. Navigate to Rundeck **System Menu** → **Key Storage**
2. Click **Add or Upload a Key** → **Password**
3. Enter a path like `keys/git/myrepo-password` and your Git password
4. In the Resource Model configuration, use the **Key Storage browser** to select the password path

**Example paths:**
```
keys/git/github-token          # GitHub personal access token
keys/git/gitlab-password        # GitLab password
keys/project1/git-auth          # Project-specific credentials
```

##### Option 2: Plain Text (Less Secure)
* **Git Password (Plain Text)**: Password to authenticate remotely (not recommended for production)

**Note:** If both are configured, Key Storage takes precedence.

---

#### SSH Key Authentication

##### Option 1: Key Storage (Recommended)
* **SSH Key Storage Path**: SSH Key from Rundeck Key Storage (secure)

**How to use:**
1. Navigate to Rundeck **System Menu** → **Key Storage**
2. Click **Add or Upload a Key** → **Private Key**
3. Upload your SSH private key (e.g., `id_rsa`) and save it with a path like `keys/git/ssh-key`
4. In the Resource Model configuration, use the **Key Storage browser** to select the key path

**Example paths:**
```
keys/git/deployment-key         # Deployment key for specific repo
keys/git/github-ssh-key         # GitHub SSH key
keys/shared/git-readonly-key    # Shared read-only access key
```

##### Option 2: Filesystem Path (Legacy)
* **SSH Key Path (Filesystem)**: Path to SSH key file on the Rundeck server filesystem

**Example:** `/home/rundeck/.ssh/id_rsa`

**Note:** If both are configured, Key Storage takes precedence.

##### SSH Host Key Checking
* **SSH: Strict Host Key Checking**: 
  - `yes` - Require remote host SSH key is defined in `~/.ssh/known_hosts` (more secure)
  - `no` - Skip host key verification (less secure, useful for testing)

---

#### Authentication Examples by Git URL Type

| Git URL Type | Recommended Auth | Example URL |
|--------------|------------------|-------------|
| HTTPS with token | Password Storage (token) | `https://github.com/user/repo.git` |
| HTTPS with password | Password Storage | `https://username@github.com/user/repo.git` |
| SSH | SSH Key Storage | `git@github.com:user/repo.git` |
| SSH | SSH Key Storage | `ssh://git@github.com/user/repo.git` |

**Important:** For HTTPS authentication, include the username in the URL: `https://username@host.com/repo.git`

---

#### Troubleshooting Authentication

**Problem: "Authentication failed" error**
- Verify the Key Storage path is correct (e.g., `keys/git/password`, not `/keys/git/password`)
- Ensure the credential exists in Key Storage
- For HTTPS: Include username in Git URL (`https://user@github.com/...`)
- For SSH: Verify host key is in `known_hosts` if strict checking is enabled

**Problem: "storageTree is null" in logs**
- The plugin requires Services API (Rundeck 5.16.0+)
- Fallback to filesystem/plain text options if Key Storage is unavailable

**Problem: SSH authentication fails**
- Verify SSH key format (OpenSSH format, starts with `-----BEGIN RSA PRIVATE KEY-----` or similar)
- Check SSH key permissions if using filesystem path (should be `600`)
- For GitHub/GitLab, ensure the public key is added to your account
- Try with `Strict Host Key Checking = no` for initial testing

**Problem: Key Storage path not found**
- Key Storage paths should start with `keys/` (e.g., `keys/git/password`)
- Use the Key Storage browser in the UI to select the correct path
- Verify the key type matches (password vs private key)

---

#### Security Best Practices

1. **Always use Key Storage** in production environments
2. **Use project-specific keys** when possible (e.g., `keys/project1/git-key`)
3. **Use deployment keys** with minimal permissions for SSH
4. **Use personal access tokens** instead of passwords for HTTPS (GitHub, GitLab, etc.)
5. **Rotate credentials regularly** and update them in Key Storage
6. **Enable strict host key checking** for SSH in production

### Limitations

* The plugin needs to clone the full repo on the local directory path (Base Directory option) to get the file that will be added to the resource model.
* Any time that you edit the nodes on the GUI, the commit will be performed with the message `Edit node from GUI`  (it is not editable)

## Workflow Steps

This plugin can clone/pull, add, commit, and push a git repository via 4 WorkflowSteps. All these steps have some generic configuration:

![](images/clone-workflow-step.png)

##### Repo Settings

* **Base Directory**: Directory for checkout. If `project.plugin.WorkflowStep.git-clone-step.gitUseProjectBasedSubdirectory` is set to true in the project configuration, this will be relative to a project-based subdirectory.
* **Git URL**: Checkout URL.
    See [git-clone](https://www.kernel.org/pub/software/scm/git/docs/git-clone.html)
    specifically the [GIT URLS](https://www.kernel.org/pub/software/scm/git/docs/git-clone.html#URLS) section.
    Some examples:
    * `ssh://[user@]host.xz[:port]/path/to/repo.git/`
    * `git://host.xz[:port]/path/to/repo.git/`
    * `http[s]://host.xz[:port]/path/to/repo.git/`
    * `https[s]://user@github.com/account/repo.git`
    * `ftp[s]://host.xz[:port]/path/to/repo.git/`
    * `rsync://host.xz/path/to/repo.git/`

##### Authentication

The workflow steps support the same authentication methods as the Resource Model:

* **Password Storage Path**: Key Storage path for Git password or access token (e.g., `keys/git/github-token`)
* **SSH Key Storage Path**: Key Storage path for SSH private key (e.g., `keys/git/ssh-key`)
* **SSH: Strict Host Key Checking**: 
  - `yes` - Require host key in `~/.ssh/known_hosts` (recommended for production)
  - `no` - Skip host key verification (useful for testing)

**Tip:** You can use GitHub/GitLab personal access tokens as passwords for HTTPS authentication.

For detailed authentication setup and troubleshooting, see the [Authentication section](#authentication) above.

### GIT Clone Workflow Step

This plugin can clone a git repo into a rundeck server folder.

For some use cases, it might be necessary to only allow checking out repositories in directories relative to the Rundeck home directory.
Allow users to checkout in any location on disk might be a security issue.

The setting `project.plugin.WorkflowStep.git-clone-step.gitUseProjectBasedSubdirectory` (per project) or  `framework.plugin.WorkflowStep.git-clone-step.gitUseProjectBasedSubdirectory` (Rundeck-wide)
can be set to `true` to enforce this (default is `false`). All values of `Base Directory` will be relative to a project-based subdirectory of the Rundeck home directory (e.g. `/var/lib/rundeck/A_Project`).

#### Configuration

You need to set up following additional options to use the plugin:

##### Repo Settings

* **Branch**: Checkout branch


### GIT Push Workflow Step

This plugin pushes a git repo from a prior created repo folder.

For some use cases, it might be necessary to only allow pushing repositories in directories relative to the Rundeck home directory.

The setting `project.plugin.WorkflowStep.git-push-step.gitUseProjectBasedSubdirectory` (per project) or  `framework.plugin.WorkflowStep.git-push-step.gitUseProjectBasedSubdirectory` (Rundeck-wide)
can be set to `true` to enforce this (default is `false`). All values of `Base Directory` will be relative to a project-based subdirectory of the Rundeck home directory (e.g. `/var/lib/rundeck/A_Project`).

#### Configuration

See above, nothing unique with this WorkflowStep.

### GIT Add Workflow Step

This plugin adds any new content from the repo for the next commit with an optional filter ability.

For some use cases, it might be necessary to only allow adding to repositories in directories relative to the Rundeck home directory.

The setting `project.plugin.WorkflowStep.git-add-step.gitUseProjectBasedSubdirectory` (per project) or  `framework.plugin.WorkflowStep.git-add-step.gitUseProjectBasedSubdirectory` (Rundeck-wide)
can be set to `true` to enforce this (default is `false`). All values of `Base Directory` will be relative to a project-based subdirectory of the Rundeck home directory (e.g. `/var/lib/rundeck/A_Project`).

#### Configuration

You need to set up following additional options to use the plugin:

##### Repo Settings

* **File Pattern**: File Pattern of files to be added. See [addFilepattern](http://archive.eclipse.org/jgit/docs/jgit-2.0.0.201206130900-r/apidocs/org/eclipse/jgit/api/AddCommand.html#addFilepattern(java.lang.String)) for more details

### GIT Commit Workflow Step

This plugin commits to a git repo from a prior created repo folder.

For some use cases, it might be necessary to only allow committing to repositories in directories relative to the Rundeck home directory.

The setting `project.plugin.WorkflowStep.git-commit-step.gitUseProjectBasedSubdirectory` (per project) or  `framework.plugin.WorkflowStep.git-commit-step.gitUseProjectBasedSubdirectory` (Rundeck-wide)
can be set to `true` to enforce this (default is `false`). All values of `Base Directory` will be relative to a project-based subdirectory of the Rundeck home directory (e.g. `/var/lib/rundeck/A_Project`).

#### Configuration

You need to set up following additional options to use the plugin:

##### Repo Settings

* **Message**: Commit message to be used. Defaults to `Rundeck Commit`
* **Add**: Adds all contents of the git repo before commiting. Defaults to `False`. If you need to be more specific, please use `GIT / Add` workflow step.
* **Push**: Pushes the repository after commiting the changes. Defaults to `False`.

---

## Quick Reference

### Key Storage Setup

**For Passwords/Tokens:**
1. System Menu → Key Storage → Add or Upload a Key → **Password**
2. Path format: `keys/git/your-credential-name`
3. Select in plugin using Key Storage browser

**For SSH Keys:**
1. System Menu → Key Storage → Add or Upload a Key → **Private Key**
2. Upload your private key file (e.g., `id_rsa`)
3. Path format: `keys/git/your-key-name`
4. Select in plugin using Key Storage browser

### Common Configuration Scenarios

#### Scenario 1: Public GitHub Repo (Read-Only)
```
Git URL: https://github.com/user/repo.git
Branch: main
Authentication: None required
```

#### Scenario 2: Private GitHub Repo with Personal Access Token
```
Git URL: https://github.com/user/repo.git
Branch: main
Authentication: Git Password Storage Path → keys/git/github-token
(Store GitHub PAT in Key Storage as password)
```

#### Scenario 3: Private GitLab Repo with SSH Key
```
Git URL: git@gitlab.com:user/repo.git
Branch: main
Authentication: SSH Key Storage Path → keys/git/gitlab-ssh-key
Strict Host Key Checking: yes
```

#### Scenario 4: Private Repo with HTTPS Username/Password
```
Git URL: https://username@github.com/user/repo.git
Branch: main
Authentication: Git Password Storage Path → keys/git/password
(Include username in URL)
```

### Property Reference

| Property Name | Description | Example Value |
|--------------|-------------|---------------|
| `gitUrl` | Git repository URL | `https://github.com/user/repo.git` |
| `gitBaseDirectory` | Local checkout directory | `/var/rundeck/git-repos/project1` |
| `gitBranch` | Branch to checkout | `main` or `develop` |
| `gitFile` | Resource model file in repo | `resources.yaml` |
| `gitFormatFile` | File format | `xml`, `yaml`, or `json` |
| `gitPasswordPath` | Plain text password | `mypassword` (not recommended) |
| `gitPasswordPathStorage` | Key Storage path for password | `keys/git/password` |
| `gitKeyPath` | Filesystem SSH key path | `/home/rundeck/.ssh/id_rsa` |
| `gitKeyPathStorage` | Key Storage path for SSH key | `keys/git/ssh-key` |
| `strictHostKeyChecking` | SSH host key verification | `yes` or `no` |
| `writable` | Allow writing to remote | `true` or `false` |

### Version Requirements

- **Rundeck 5.16.0 or later** - Required for Key Storage support
- Earlier versions can use filesystem paths and plain text authentication

### Support

For issues or questions:
- GitHub Issues: [rundeck-plugins/git-plugin](https://github.com/rundeck-plugins/git-plugin/issues)
- Rundeck Documentation: [https://docs.rundeck.com](https://docs.rundeck.com)