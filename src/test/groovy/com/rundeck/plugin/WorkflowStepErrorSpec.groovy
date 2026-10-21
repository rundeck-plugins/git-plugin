package com.rundeck.plugin

import com.dtolabs.rundeck.core.execution.ExecutionContext
import com.dtolabs.rundeck.core.execution.ExecutionListener
import com.dtolabs.rundeck.core.execution.workflow.steps.StepException
import com.dtolabs.rundeck.plugins.step.PluginStepContext
import spock.lang.Specification
import spock.lang.Unroll

class WorkflowStepErrorSpec extends Specification {

    @Unroll
    def "#stepClass.simpleName reports the underlying git error"() {
        given:
        def failure = new Exception("simulated git failure")
        def gitManager = Stub(GitManager)
        gitManager.add(_, _) >> { throw failure }
        gitManager.commit(_, _) >> { throw failure }
        gitManager.push(_) >> { throw failure }

        def step = stepClass.getDeclaredConstructor().newInstance()
        step.gitManager = gitManager

        def executionContext = Mock(ExecutionContext)
        executionContext.getExecutionListener() >> Mock(ExecutionListener)
        def context = Mock(PluginStepContext)
        context.getExecutionContext() >> executionContext

        def configuration = [
                gitBaseDirectory : "build/test-repo",
                gitAddFilePattern: ".",
                gitMessage       : "test commit"
        ]

        when:
        step.executeStep(context, configuration)

        then:
        StepException e = thrown()
        e.message == expectedMessage
        e.cause.is(failure)
        e.failureReason == GitFailureReason.AuthenticationError

        where:
        stepClass             | expectedMessage
        GitAddWorkflowStep    | "Error running git add: simulated git failure"
        GitCommitWorkflowStep | "Error running git commit: simulated git failure"
        GitPushWorkflowStep   | "Error running git push: simulated git failure"
    }
}
