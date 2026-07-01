package com.rundeck.plugin

import com.dtolabs.rundeck.core.execution.ExecutionContext
import com.dtolabs.rundeck.core.execution.proxy.ProxyRunnerPlugin
import com.dtolabs.rundeck.core.execution.proxy.ProxySecretBundleCreator
import com.dtolabs.rundeck.core.storage.ResourceMeta
import com.dtolabs.rundeck.core.storage.StorageTree
import org.rundeck.storage.api.Resource
import spock.lang.Specification
import spock.lang.Unroll

class WorkflowStepSecretBundleSpec extends Specification {

    @Unroll
    def "#stepClass.simpleName implements ProxyRunnerPlugin and ProxySecretBundleCreator"() {
        expect:
        ProxyRunnerPlugin.isAssignableFrom(stepClass)
        ProxySecretBundleCreator.isAssignableFrom(stepClass)

        where:
        stepClass << [GitCloneWorkflowStep, GitPushWorkflowStep, GitCommitWorkflowStep]
    }

    @Unroll
    def "#stepClass.simpleName listSecretsPathWorkflowStep returns configured paths"() {
        given:
        def step = stepClass.getDeclaredConstructor().newInstance()
        def context = Mock(ExecutionContext)
        context.getDataContext() >> [:]
        def configuration = [
                gitPasswordPath: "keys/project/Test/password",
                gitKeyPath: "keys/project/Test/sshkey"
        ]

        when:
        def paths = step.listSecretsPathWorkflowStep(context, configuration)

        then:
        paths.size() == 2
        paths.contains("keys/project/Test/password")
        paths.contains("keys/project/Test/sshkey")

        where:
        stepClass << [GitCloneWorkflowStep, GitPushWorkflowStep, GitCommitWorkflowStep]
    }

    @Unroll
    def "#stepClass.simpleName listSecretsPathWorkflowStep returns empty when no secrets configured"() {
        given:
        def step = stepClass.getDeclaredConstructor().newInstance()
        def context = Mock(ExecutionContext)
        context.getDataContext() >> [:]
        def configuration = [gitUrl: "https://example.com/repo.git"]

        when:
        def paths = step.listSecretsPathWorkflowStep(context, configuration)

        then:
        paths.isEmpty()

        where:
        stepClass << [GitCloneWorkflowStep, GitPushWorkflowStep, GitCommitWorkflowStep]
    }

    @Unroll
    def "#stepClass.simpleName prepareSecretBundleWorkflowStep bundles secrets"() {
        given:
        def step = stepClass.getDeclaredConstructor().newInstance()

        def secretBytes = "secret-value".bytes
        def contents = Mock(ResourceMeta)
        contents.writeContent(_) >> { OutputStream os -> os.write(secretBytes); return (long) secretBytes.length }
        def resource = Mock(Resource)
        resource.getContents() >> contents
        def storageTree = Mock(StorageTree)
        storageTree.getResource("keys/project/Test/password") >> resource

        def context = Mock(ExecutionContext)
        context.getDataContext() >> [:]
        context.getStorageTree() >> storageTree

        def configuration = [gitPasswordPath: "keys/project/Test/password"]

        when:
        def bundle = step.prepareSecretBundleWorkflowStep(context, configuration)

        then:
        bundle != null
        bundle.getValue("keys/project/Test/password") == secretBytes

        where:
        stepClass << [GitCloneWorkflowStep, GitPushWorkflowStep, GitCommitWorkflowStep]
    }
}
