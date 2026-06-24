package com.rundeck.plugin.util

import spock.lang.Specification
import spock.lang.Unroll

class GitPluginUtilExtractRepoNameSpec extends Specification {

    @Unroll
    def "extractRepoName from '#url' returns '#expected'"() {
        expect:
        GitPluginUtil.extractRepoName(url) == expected

        where:
        url                                                     | expected
        'https://github.com/user/my-repo.git'                   | 'my-repo'
        'https://github.com/user/my-repo'                       | 'my-repo'
        'https://github.com/user/my-repo.git/'                  | 'my-repo'
        'ssh://git@github.com/user/my-repo.git'                 | 'my-repo'
        'ssh://git@host.xz:2222/path/to/repo.git'               | 'repo'
        'git://host.xz/path/to/repo.git/'                       | 'repo'
        'git@github.com:user/my-repo.git'                       | 'my-repo'
        'git@github.com:org/sub-project.git'                    | 'sub-project'
        'https://example.com/repo'                              | 'repo'
        'ftp://host.xz/path/to/repo.git'                        | 'repo'
        '.'                                                     | null
        '..'                                                    | null
        'repo:foo/bar'                                          | null
        'repo:foo\\bar'                                        | null
        null                                                    | null
        ''                                                      | null
    }
}
