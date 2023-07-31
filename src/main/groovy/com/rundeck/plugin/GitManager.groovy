package com.rundeck.plugin

import com.rundeck.plugin.util.PluginSshSessionFactory
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.PullResult
import org.eclipse.jgit.transport.PushResult
import org.eclipse.jgit.api.TransportCommand
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.SubmoduleConfig.FetchRecurseSubmodulesMode
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.util.FileUtils
import org.eclipse.jgit.api.SubmoduleUpdateCommand

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import org.slf4j.Logger
import org.slf4j.LoggerFactory
/**
 * Created by luistoledo on 12/20/17.
 */
class GitManager {

    private static final Logger logger = LoggerFactory.getLogger(GitManager.class);

    public static final String REMOTE_NAME = "origin"
    Git git
    String branch
    String fileName
    Repository repo
    String strictHostKeyChecking
    String sshPrivateKeyPath
    String sshPrivateKey
    String gitPassword
    String gitURL

    GitManager(Properties configuration) {
        this.gitURL = configuration.getProperty(GitResourceModelFactory.GIT_URL)
        this.branch = configuration.getProperty(GitResourceModelFactory.GIT_BRANCH)
        this.fileName = configuration.getProperty(GitResourceModelFactory.GIT_FILE)
        this.strictHostKeyChecking = configuration.getProperty(GitResourceModelFactory.GIT_HOSTKEY_CHECKING)

    }

    Map<String, String> getSshConfig() {
        def config = [:]

        if (strictHostKeyChecking in ['yes', 'no']) {
            config['StrictHostKeyChecking'] = strictHostKeyChecking
        }
        config
    }


    void cloneOrCreate(File base) throws Exception {
        if (base.isDirectory() && new File(base, ".git").isDirectory()) {
            def arepo = new FileRepositoryBuilder().setGitDir(new File(base, ".git")).setWorkTree(base).build()
            def agit = new Git(arepo)

            def config = agit.getRepository().getConfig()
            def found = config.getString("remote", REMOTE_NAME, "url")

            def needsClone = false;

            if (found != this.gitURL) {
                logger.debug("url differs, re-cloning ${found}!=${this.gitURL}")
                needsClone = true
            } else if (agit.repository.getFullBranch() != "refs/heads/$branch") {
                logger.debug("branch differs, re-cloning")
                needsClone = true
            } else {
                performPull(agit)
            }

            if (needsClone) {
                removeWorkdir(base)
                performClone(base)
                return
            }

            git = agit
            repo = arepo
        } else {
            performClone(base)
        }
        repo.close()
    }

    void push(File base) {
        def arepo = new FileRepositoryBuilder().setGitDir(new File(base, ".git")).setWorkTree(base).build()
        def agit = new Git(arepo)

        git = agit
        repo = arepo

        performPush(git)
    }

    void commit(File base, String message) {
        def arepo = new FileRepositoryBuilder().setGitDir(new File(base, ".git")).setWorkTree(base).build()
        def agit = new Git(arepo)

        git = agit
        repo = arepo

        performCommit(git, message)
    }

    void add(File base, String filePattern) {
        def arepo = new FileRepositoryBuilder().setGitDir(new File(base, ".git")).setWorkTree(base).build()
        def agit = new Git(arepo)

        git = agit
        repo = arepo

        performAdd(git, filePattern)
    }

    private void removeWorkdir(File base) {
        FileUtils.delete(base, FileUtils.RECURSIVE)
    }


    private void performClone(File base) {

        def cloneCommand = Git.cloneRepository().
                setBranch(this.branch).
                setRemote(REMOTE_NAME).
                setDirectory(base).
                setURI(this.gitURL).
                setCloneSubmodules(true)

        try {
            setupTransportAuthentication(sshConfig, cloneCommand, this.gitURL)
            git = cloneCommand.call()
        } catch (Exception e) {
            e.printStackTrace()
            logger.debug("Failed cloning the repository from ${this.gitURL}: ${e.message}", e)
            throw new Exception("Failed cloning the repository from ${this.gitURL}: ${e.message}", e)
        }
        repo = git.getRepository()
    }


    private void performPull(Git git) {

        def pullCommand = git.pull()
                .setRebase(true)
                .setRecurseSubmodules(FetchRecurseSubmodulesMode.YES)

        try {
            setupTransportAuthentication(sshConfig, pullCommand, this.gitURL)
            PullResult result = pullCommand.call()
            
            if (!result.isSuccessful()) {
                logger.info("Pull is not successful.")
            } else {
                logger.debug("Pull is not successful.")
            }

            def submoduleUpdateCommand = new SubmoduleUpdateCommand(git.getRepository())
            submoduleUpdateCommand.call()

        } catch (Exception e) {
            e.printStackTrace()
            logger.debug("Failed pulling the repository from ${this.gitURL}: ${e.message}", e)
            throw new Exception("Failed pulling the repository from ${this.gitURL}: ${e.message}", e)
        }
        repo = git.getRepository()
    }

    private void performPush(Git git) {
        def pushCommand = git.push()
                .setPushAll()

        try {
            setupTransportAuthentication(sshConfig, pushCommand, this.gitURL)
            pushCommand.call()
            logger.info("Push is not successful.")
        } catch (Exception e) {
            e.printStackTrace()
            logger.debug("Failed pushing the repository to ${this.gitURL}: ${e.message}", e)
            throw new Exception("Failed pushing the repository to ${this.gitURL}: ${e.message}", e)
        }
    }

    private void performCommit(Git git, String message) {
        def commitCommand = git.commit()
                .setMessage(message)

        try {
            // setupTransportAuthentication(sshConfig, commitCommand, this.gitURL)
            commitCommand.call()
            logger.info("Commit is not successful.")
        } catch (Exception e) {
            e.printStackTrace()
            logger.debug("Failed committing the repository to ${this.gitURL}: ${e.message}", e)
            throw new Exception("Failed committing the repository to ${this.gitURL}: ${e.message}", e)
        }
    }

    private void performAdd(Git git, String filePattern) {
        def addCommand = git.add()
                .addFilepattern(filePattern)

        try {
            // setupTransportAuthentication(sshConfig, addCommand, this.gitURL)
            addCommand.call()
            logger.info("Add is not successful.")
        } catch (Exception e) {
            e.printStackTrace()
            logger.debug("Failed adding the repository to ${this.gitURL}: ${e.message}", e)
            throw new Exception("Failed adding the repository to ${this.gitURL}: ${e.message}", e)
        }
    }

    void setupTransportAuthentication(
            Map<String, String> sshConfig,
            TransportCommand command,
            String url = null) throws Exception {
        if (!url) {
            url = command.repository.config.getString('remote', REMOTE_NAME, 'url')
        }
        if (!url) {
            throw new NullPointerException("url for remote was not set")
        }

        URIish u = new URIish(url);
        logger.debug("transport url ${u}, scheme ${u.scheme}, user ${u.user}")
        if ((u.scheme == null || u.scheme == 'ssh') && u.user && (sshPrivateKeyPath || sshPrivateKey)) {

            byte[] keyData
            if (sshPrivateKeyPath) {
                logger.debug("using ssh private key path ${sshPrivateKeyPath}")
                Path path = Paths.get(sshPrivateKeyPath);
                keyData = Files.readAllBytes(path);

            } else if (sshPrivateKey) {
                logger.debug("using ssh private key")
                keyData = sshPrivateKey.getBytes()
            }

            def factory = new PluginSshSessionFactory(keyData)
            factory.sshConfig = sshConfig
            command.setTransportConfigCallback(factory)
        } else if (u.user && gitPassword) {
            logger.debug("using password")

            if (null != gitPassword && gitPassword.length() > 0) {
                command.setCredentialsProvider(new UsernamePasswordCredentialsProvider(u.user, gitPassword))
            }
        }
    }

    PullResult gitPull(Git git1 = null) {
        def pullCommand = (git1 ?: git).pull()
                .setRemote(REMOTE_NAME)
                .setRemoteBranchName(branch)
                .setRecurseSubmodules(FetchRecurseSubmodulesMode.YES)
        setupTransportAuthentication(sshConfig, pullCommand)
        pullCommand.call()
    }

    def gitCommitAndPush() {

        ////PERFORM COMMIT
        git.add()
                .addFilepattern(this.fileName)
                .call();

        // and then commit the changes
        //TODO: define a custom (or imput name) for the commit
        git.commit()
                .setMessage("Edit node from GUI")
                .call();

        println("Committed file " + this.fileName + " to repository at " + repo.getDirectory());

        /// PERFORM PUSH
        def pushb = git.push()
        pushb.setRemote(REMOTE_NAME)
        pushb.add(branch)
        setupTransportAuthentication(sshConfig, pushb)

        def push
        try {
            push = pushb.call()
        } catch (Exception e) {
            logger.debug("Failed push to remote: ${e.message}", e)
            throw new Exception("Failed push to remote: ${e.message}", e)
        }
        def sb = new StringBuilder()
        def updates = (push*.remoteUpdates).flatten()
        updates.each {
            sb.append it.toString()
        }

        String message = ""
        def failedUpdates = updates.findAll { it.status != RemoteRefUpdate.Status.OK }
        if (failedUpdates) {
            message = "Some updates failed: " + failedUpdates
        } else {
            message = "Remote push result: OK."
        }

        logger.debug(message)

    }

    InputStream getFile(String localPath) {

        File base = new File(localPath)

        if (!base) {
            base.mkdir()
        }

        //start the new repo, if the repo is create nothing will be done
        this.cloneOrCreate(base)

        File file = new File(localPath + "/" + fileName)

        //always perform a pull
        //TODO: check if it is needed check for the repo status
        //and perform the pull when the last commit is different to the last commit on the local repo
        this.gitPull()

        return file.newInputStream()

    }


}
