package com.rundeck.plugin

import com.dtolabs.rundeck.core.execution.workflow.steps.FailureReason

enum GitFailureReason implements FailureReason {

    /**
     * Authentication Error
     */
    AuthenticationError,

}
