package com.rundeck.plugin.util

import com.dtolabs.rundeck.core.execution.ExecutionContext
import com.dtolabs.rundeck.core.execution.proxy.SecretBundle
import com.dtolabs.rundeck.core.storage.ResourceMeta
import com.dtolabs.rundeck.core.storage.StorageTree
import org.rundeck.storage.api.Resource
import spock.lang.Specification

class GitPluginUtilSecretBundleSpec extends Specification {

    def "listSecretsPathForStep returns empty list when no storage keys configured"() {
        given:
        def context = Mock(ExecutionContext)
        context.getDataContext() >> [:]
        def configuration = [gitUrl: "https://example.com/repo.git"]

        when:
        def paths = GitPluginUtil.listSecretsPathForStep(context, configuration, "gitPasswordPath", "gitKeyPath")

        then:
        paths != null
        paths.isEmpty()
    }

    def "listSecretsPathForStep returns password path when configured"() {
        given:
        def context = Mock(ExecutionContext)
        context.getDataContext() >> [:]
        def configuration = [gitPasswordPath: "keys/project/MyProject/git_password"]

        when:
        def paths = GitPluginUtil.listSecretsPathForStep(context, configuration, "gitPasswordPath", "gitKeyPath")

        then:
        paths.size() == 1
        paths[0] == "keys/project/MyProject/git_password"
    }

    def "listSecretsPathForStep returns SSH key path when configured"() {
        given:
        def context = Mock(ExecutionContext)
        context.getDataContext() >> [:]
        def configuration = [gitKeyPath: "keys/project/MyProject/ssh_key"]

        when:
        def paths = GitPluginUtil.listSecretsPathForStep(context, configuration, "gitPasswordPath", "gitKeyPath")

        then:
        paths.size() == 1
        paths[0] == "keys/project/MyProject/ssh_key"
    }

    def "listSecretsPathForStep returns both paths when both configured"() {
        given:
        def context = Mock(ExecutionContext)
        context.getDataContext() >> [:]
        def configuration = [
                gitPasswordPath: "keys/project/MyProject/git_password",
                gitKeyPath: "keys/project/MyProject/ssh_key"
        ]

        when:
        def paths = GitPluginUtil.listSecretsPathForStep(context, configuration, "gitPasswordPath", "gitKeyPath")

        then:
        paths.size() == 2
        paths.contains("keys/project/MyProject/git_password")
        paths.contains("keys/project/MyProject/ssh_key")
    }

    def "listSecretsPathForStep resolves data references in paths"() {
        given:
        def context = Mock(ExecutionContext)
        context.getDataContext() >> [option: [secretPath: "keys/project/MyProject/resolved_key"]]
        def configuration = [gitKeyPath: '${option.secretPath}']

        when:
        def paths = GitPluginUtil.listSecretsPathForStep(context, configuration, "gitPasswordPath", "gitKeyPath")

        then:
        paths.size() == 1
        paths[0] == "keys/project/MyProject/resolved_key"
    }

    def "prepareSecretBundleForStep creates bundle with secret content"() {
        given:
        def secretBytes = "secret-password".bytes
        def contents = Mock(ResourceMeta)
        contents.writeContent(_) >> { OutputStream os -> os.write(secretBytes); return (long) secretBytes.length }

        def resource = Mock(Resource)
        resource.getContents() >> contents

        def storageTree = Mock(StorageTree)
        storageTree.getResource("keys/project/MyProject/git_password") >> resource

        def context = Mock(ExecutionContext)
        context.getDataContext() >> [:]
        context.getStorageTree() >> storageTree

        def configuration = [gitPasswordPath: "keys/project/MyProject/git_password"]

        when:
        SecretBundle bundle = GitPluginUtil.prepareSecretBundleForStep(context, configuration, "gitPasswordPath", "gitKeyPath")

        then:
        bundle != null
        bundle.getValue("keys/project/MyProject/git_password") == secretBytes
    }

    def "prepareSecretBundleForStep handles missing storage path gracefully"() {
        given:
        def storageTree = Mock(StorageTree)
        storageTree.getResource(_) >> { throw new RuntimeException("Not found") }

        def context = Mock(ExecutionContext)
        context.getDataContext() >> [:]
        context.getStorageTree() >> storageTree

        def configuration = [gitPasswordPath: "keys/project/MyProject/missing"]

        when:
        SecretBundle bundle = GitPluginUtil.prepareSecretBundleForStep(context, configuration, "gitPasswordPath")

        then:
        noExceptionThrown()
        bundle != null
        bundle.getValue("keys/project/MyProject/missing") == null
    }

    def "prepareSecretBundleForStep bundles multiple secrets"() {
        given:
        def passwordBytes = "my-password".bytes
        def keyBytes = "ssh-private-key-content".bytes

        def passwordContents = Mock(ResourceMeta)
        passwordContents.writeContent(_) >> { OutputStream os -> os.write(passwordBytes); return (long) passwordBytes.length }
        def passwordResource = Mock(Resource)
        passwordResource.getContents() >> passwordContents

        def keyContents = Mock(ResourceMeta)
        keyContents.writeContent(_) >> { OutputStream os -> os.write(keyBytes); return (long) keyBytes.length }
        def keyResource = Mock(Resource)
        keyResource.getContents() >> keyContents

        def storageTree = Mock(StorageTree)
        storageTree.getResource("keys/project/P/password") >> passwordResource
        storageTree.getResource("keys/project/P/sshkey") >> keyResource

        def context = Mock(ExecutionContext)
        context.getDataContext() >> [:]
        context.getStorageTree() >> storageTree

        def configuration = [
                gitPasswordPath: "keys/project/P/password",
                gitKeyPath: "keys/project/P/sshkey"
        ]

        when:
        SecretBundle bundle = GitPluginUtil.prepareSecretBundleForStep(context, configuration, "gitPasswordPath", "gitKeyPath")

        then:
        bundle.getValue("keys/project/P/password") == passwordBytes
        bundle.getValue("keys/project/P/sshkey") == keyBytes
    }
}
