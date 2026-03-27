package com.rundeck.plugin.util

import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.transport.Transport
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase
import org.eclipse.jgit.transport.sshd.SshdSessionFactory
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.KeyPairGenerator
import java.security.PublicKey

class PluginSshSessionFactorySpec extends Specification {

    private static final byte[] FAKE_KEY = "-----BEGIN RSA PRIVATE KEY-----\nfake-key-data\n-----END RSA PRIVATE KEY-----".bytes

    def "constructor accepts private key and implements TransportConfigCallback"() {
        when:
        def factory = new PluginSshSessionFactory(FAKE_KEY)

        then:
        factory != null
        factory instanceof org.eclipse.jgit.api.TransportConfigCallback
    }

    def "configure sets SshdSessionFactory on SshTransport"() {
        given:
        def factory = new PluginSshSessionFactory(FAKE_KEY)
        factory.sshConfig = [StrictHostKeyChecking: 'no']

        def sshTransport = Mock(SshTransport)

        when:
        factory.configure(sshTransport)

        then:
        1 * sshTransport.setSshSessionFactory(_ as SshdSessionFactory)
    }

    def "configure ignores non-SSH transports"() {
        given:
        def factory = new PluginSshSessionFactory(FAKE_KEY)
        def transport = Mock(Transport)

        when:
        factory.configure(transport)

        then:
        0 * transport._(*_)
    }

    def "sshConfig is available to session factory when set before configure"() {
        given:
        def factory = new PluginSshSessionFactory(FAKE_KEY)
        factory.sshConfig = [StrictHostKeyChecking: 'no']

        SshdSessionFactory captured = null
        def sshTransport = Mock(SshTransport) {
            setSshSessionFactory(_) >> { args -> captured = args[0] }
        }

        when:
        factory.configure(sshTransport)

        then:
        captured != null
        captured instanceof SshdSessionFactory
    }

    def "session factory provides private key as default identity"() {
        given:
        def factory = new PluginSshSessionFactory(FAKE_KEY)
        factory.sshConfig = [:]

        SshdSessionFactory captured = null
        def sshTransport = Mock(SshTransport) {
            setSshSessionFactory(_) >> { args -> captured = args[0] }
        }
        factory.configure(sshTransport)

        when:
        List<Path> identities = captured.getDefaultIdentities(new File(System.getProperty("java.io.tmpdir")))

        then:
        identities.size() == 1
        Files.exists(identities[0])
        identities[0].toFile().name.startsWith("rundeck-git-key-")
        identities[0].toFile().name.endsWith(".pem")
        Files.readAllBytes(identities[0]) == FAKE_KEY
    }

    def "temp key file is cached across multiple calls"() {
        given:
        def factory = new PluginSshSessionFactory(FAKE_KEY)
        factory.sshConfig = [:]

        SshdSessionFactory captured = null
        def sshTransport = Mock(SshTransport) {
            setSshSessionFactory(_) >> { args -> captured = args[0] }
        }
        factory.configure(sshTransport)

        def tmpDir = new File(System.getProperty("java.io.tmpdir"))

        when:
        List<Path> firstCall = captured.getDefaultIdentities(tmpDir)
        List<Path> secondCall = captured.getDefaultIdentities(tmpDir)

        then:
        firstCall[0] == secondCall[0]
    }

    def "temp key file has restricted permissions on POSIX systems"() {
        given:
        def factory = new PluginSshSessionFactory(FAKE_KEY)
        factory.sshConfig = [:]

        SshdSessionFactory captured = null
        def sshTransport = Mock(SshTransport) {
            setSshSessionFactory(_) >> { args -> captured = args[0] }
        }
        factory.configure(sshTransport)

        when:
        List<Path> identities = captured.getDefaultIdentities(new File(System.getProperty("java.io.tmpdir")))
        def perms = Files.getPosixFilePermissions(identities[0])

        then:
        perms == PosixFilePermissions.fromString("rw-------")
    }

    def "session factory without private key delegates to default identities"() {
        given:
        def factory = new PluginSshSessionFactory(null)
        factory.sshConfig = [:]

        SshdSessionFactory captured = null
        def sshTransport = Mock(SshTransport) {
            setSshSessionFactory(_) >> { args -> captured = args[0] }
        }
        factory.configure(sshTransport)

        when:
        List<Path> identities = captured.getDefaultIdentities(new File(System.getProperty("java.io.tmpdir")))

        then:
        notThrown(Exception)
        identities != null
    }

    def "StrictHostKeyChecking=no returns accept-all ServerKeyDatabase"() {
        given:
        def factory = new PluginSshSessionFactory(FAKE_KEY)
        factory.sshConfig = [StrictHostKeyChecking: 'no']

        SshdSessionFactory captured = null
        def sshTransport = Mock(SshTransport) {
            setSshSessionFactory(_) >> { args -> captured = args[0] }
        }
        factory.configure(sshTransport)

        def homeDir = new File(System.getProperty("user.home"))
        def sshDir = new File(homeDir, ".ssh")

        when:
        ServerKeyDatabase db = captured.getServerKeyDatabase(homeDir, sshDir)

        then:
        db != null

        and:
        def keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048)
        PublicKey randomKey = keyPairGen.generateKeyPair().getPublic()
        def addr = new InetSocketAddress("github.com", 22)

        db.accept("github.com:22", addr, randomKey, null, null) == true
        db.lookup("github.com:22", addr, null) == []
    }

    def "StrictHostKeyChecking=yes uses default ServerKeyDatabase"() {
        given:
        def factory = new PluginSshSessionFactory(FAKE_KEY)
        factory.sshConfig = [StrictHostKeyChecking: 'yes']

        SshdSessionFactory captured = null
        def sshTransport = Mock(SshTransport) {
            setSshSessionFactory(_) >> { args -> captured = args[0] }
        }
        factory.configure(sshTransport)

        def homeDir = new File(System.getProperty("user.home"))
        def sshDir = new File(homeDir, ".ssh")

        when:
        ServerKeyDatabase db = captured.getServerKeyDatabase(homeDir, sshDir)

        then:
        db != null
    }

    def "null sshConfig uses default ServerKeyDatabase"() {
        given:
        def factory = new PluginSshSessionFactory(FAKE_KEY)
        factory.sshConfig = null

        SshdSessionFactory captured = null
        def sshTransport = Mock(SshTransport) {
            setSshSessionFactory(_) >> { args -> captured = args[0] }
        }
        factory.configure(sshTransport)

        def homeDir = new File(System.getProperty("user.home"))
        def sshDir = new File(homeDir, ".ssh")

        when:
        ServerKeyDatabase db = captured.getServerKeyDatabase(homeDir, sshDir)

        then:
        db != null
    }

    def "each call to configure creates a fresh session factory with current sshConfig"() {
        given:
        def factory = new PluginSshSessionFactory(FAKE_KEY)

        SshdSessionFactory first = null
        SshdSessionFactory second = null
        def sshTransport = Mock(SshTransport) {
            setSshSessionFactory(_) >> { args ->
                if (first == null) first = args[0]
                else second = args[0]
            }
        }

        when:
        factory.sshConfig = [StrictHostKeyChecking: 'yes']
        factory.configure(sshTransport)
        factory.sshConfig = [StrictHostKeyChecking: 'no']
        factory.configure(sshTransport)

        then:
        first != null
        second != null
        !first.is(second)
    }
}
