package com.rundeck.plugin

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.submodule.SubmoduleWalk
import spock.lang.Specification
import spock.lang.TempDir

/**
 * Exercises GitManager against real repositories on the local filesystem,
 * cloned over file:// URLs.
 */
class GitManagerSpec extends Specification {

    @TempDir
    File tmp

    Git source
    File sourceDir

    def setup() {
        sourceDir = new File(tmp, 'source')
        source = Git.init().setDirectory(sourceDir).setInitialBranch('main').call()
    }

    def cleanup() {
        source?.close()
    }

    private RevCommit commitFile(String content) {
        new File(sourceDir, 'resources.yaml').text = content
        source.add().addFilepattern('resources.yaml').call()
        return source.commit().setMessage(content).setSign(false).call()
    }

    private void tag(String name, boolean force = false, boolean annotated = false) {
        source.tag().setName(name).setAnnotated(annotated).setSigned(false)
                .setMessage(annotated ? name : null).setForceUpdate(force).call()
    }

    private GitManager manager(String branchOrTag) {
        Properties config = new Properties()
        config.setProperty(GitResourceModelFactory.GIT_URL, 'file://' + sourceDir.absolutePath)
        config.setProperty(GitResourceModelFactory.GIT_BRANCH, branchOrTag)
        config.setProperty(GitResourceModelFactory.GIT_FILE, 'resources.yaml')
        return new GitManager(config)
    }

    private static String head(File checkout) {
        Git.open(checkout).withCloseable { it.repository.resolve(Constants.HEAD).name() }
    }

    def "cloning a tag checks out the tagged commit"() {
        given:
        RevCommit tagged = commitFile('v1')
        tag('v1.0')
        commitFile('after the tag')
        File checkout = new File(tmp, 'checkout')

        when:
        manager('v1.0').cloneOrCreate(checkout)

        then:
        head(checkout) == tagged.name()
        new File(checkout, 'resources.yaml').text == 'v1'
    }

    def "a checked out tag is updated in place, and a moved tag is picked up (annotated: #annotated)"() {
        given:
        commitFile('v1')
        tag('v1.0', false, annotated)
        File checkout = new File(tmp, 'checkout')
        manager('v1.0').cloneOrCreate(checkout)
        File marker = new File(checkout, '.git/marker')
        marker.text = 'still the same clone'

        and: "the tag is moved to a new commit"
        RevCommit moved = commitFile('v1 again')
        tag('v1.0', true, annotated)

        when:
        manager('v1.0').cloneOrCreate(checkout)

        then: "the directory was not cloned again"
        marker.exists()

        and:
        head(checkout) == moved.name()
        new File(checkout, 'resources.yaml').text == 'v1 again'

        where:
        annotated << [false, true]
    }

    def "a branch with the same name as the checked out tag takes precedence once it exists"() {
        given:
        commitFile('tagged')
        tag('release')
        File checkout = new File(tmp, 'checkout')
        manager('release').cloneOrCreate(checkout)

        and: "the remote gains a branch with the tag's name"
        source.branchCreate().setName('release').call()
        source.checkout().setName('refs/heads/release').call()
        RevCommit onBranch = commitFile('on the branch')

        when:
        manager('release').cloneOrCreate(checkout)

        then: "as on a first clone, the branch is checked out"
        Git.open(checkout).withCloseable { it.repository.fullBranch } == 'refs/heads/release'
        head(checkout) == onBranch.name()
    }

    def "submodules follow a moved tag"() {
        given: "a library repository used as a submodule"
        File libDir = new File(tmp, 'lib')
        Git lib = Git.init().setDirectory(libDir).setInitialBranch('main').call()
        new File(libDir, 'lib.txt').text = 'lib v1'
        lib.add().addFilepattern('lib.txt').call()
        lib.commit().setMessage('lib v1').setSign(false).call()
        source.submoduleAdd().setPath('lib').setURI('file://' + libDir.absolutePath).call().close()
        commitFile('v1')
        tag('v1.0')
        File checkout = new File(tmp, 'checkout')
        manager('v1.0').cloneOrCreate(checkout)

        and: "the tag is moved to a commit that uses a newer library commit"
        new File(libDir, 'lib.txt').text = 'lib v2'
        lib.add().addFilepattern('lib.txt').call()
        lib.commit().setMessage('lib v2').setSign(false).call()
        SubmoduleWalk.getSubmoduleRepository(source.repository, 'lib').withCloseable { repo ->
            Git.wrap(repo).pull().call()
        }
        source.add().addFilepattern('lib').call()
        commitFile('v1 again')
        tag('v1.0', true)

        when:
        manager('v1.0').cloneOrCreate(checkout)

        then:
        new File(checkout, 'resources.yaml').text == 'v1 again'
        new File(checkout, 'lib/lib.txt').text == 'lib v2'

        cleanup:
        lib?.close()
    }

    def "a branch is still pulled"() {
        given:
        commitFile('first')
        File checkout = new File(tmp, 'checkout')
        manager('main').cloneOrCreate(checkout)
        File marker = new File(checkout, '.git/marker')
        marker.text = 'still the same clone'
        RevCommit second = commitFile('second')

        when:
        manager('main').cloneOrCreate(checkout)

        then:
        marker.exists()
        head(checkout) == second.name()
        new File(checkout, 'resources.yaml').text == 'second'
    }

    def "getFile reads the resource model file from a tag"() {
        given:
        commitFile('nodes at v1')
        tag('v1.0')
        File checkout = new File(tmp, 'checkout')
        GitManager gitManager = manager('v1.0')

        when: "read twice, so the second read updates the existing clone"
        gitManager.getFile(checkout.path).text
        String content = gitManager.getFile(checkout.path).text

        then:
        content == 'nodes at v1'
    }

    def "saving nodes is refused when a tag is checked out"() {
        given:
        commitFile('nodes at v1')
        tag('v1.0')
        File checkout = new File(tmp, 'checkout')
        GitManager gitManager = manager('v1.0')
        gitManager.getFile(checkout.path).close()
        new File(checkout, 'resources.yaml').text = 'edited'

        when:
        gitManager.gitCommitAndPush()

        then:
        Exception e = thrown()
        e.message.contains('tag v1.0 is checked out')
    }

    def "pushing is refused when HEAD is detached"() {
        given:
        commitFile('v1')
        tag('v1.0')
        File checkout = new File(tmp, 'checkout')
        GitManager gitManager = manager('v1.0')
        gitManager.cloneOrCreate(checkout)

        when:
        gitManager.push(checkout)

        then:
        Exception e = thrown()
        e.message.contains('HEAD is detached')
    }
}
