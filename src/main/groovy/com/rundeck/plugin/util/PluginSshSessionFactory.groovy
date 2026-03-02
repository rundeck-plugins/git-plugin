package com.rundeck.plugin.util

import org.apache.sshd.client.config.hosts.HostConfigEntry
import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.transport.Transport
import org.eclipse.jgit.transport.sshd.SshdSessionFactory
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder
import org.eclipse.jgit.util.FS

import java.nio.file.Files
import java.nio.file.Path

/**
 * SSH session factory using Apache MINA SSHD instead of JSch.
 * Provides support for modern SSH algorithms including RSA with SHA-2 signatures.
 */
class PluginSshSessionFactory implements TransportConfigCallback {
    private byte[] privateKey
    Map<String, String> sshConfig
    private SshdSessionFactory sessionFactory

    PluginSshSessionFactory(final byte[] privateKey) {
        this.privateKey = privateKey
        this.sessionFactory = buildSessionFactory()
    }

    private SshdSessionFactory buildSessionFactory() {
        def builder = new SshdSessionFactoryBuilder()
        
        def factory = builder
            .setPreferredAuthentications("publickey")
            .build(null)
        
        return new CustomSshdSessionFactory(factory, privateKey, sshConfig)
    }

    @Override
    void configure(final Transport transport) {
        if (transport in SshTransport) {
            SshTransport sshTransport = (SshTransport) transport
            sshTransport.setSshSessionFactory(sessionFactory)
        }
    }

    private static class CustomSshdSessionFactory extends SshdSessionFactory {
        private final SshdSessionFactory delegate
        private final byte[] privateKey
        private final Map<String, String> sshConfig

        CustomSshdSessionFactory(SshdSessionFactory delegate, byte[] privateKey, Map<String, String> sshConfig) {
            super(null, null)
            this.delegate = delegate
            this.privateKey = privateKey
            this.sshConfig = sshConfig
        }

        @Override
        File getSshDirectory() {
            return delegate.getSshDirectory()
        }

        @Override
        List<Path> getDefaultIdentities(File sshDir) {
            if (privateKey) {
                Path tempKeyFile = Files.createTempFile("rundeck-git-key-", ".pem")
                tempKeyFile.toFile().deleteOnExit()
                Files.write(tempKeyFile, privateKey)
                return [tempKeyFile]
            }
            return delegate.getDefaultIdentities(sshDir)
        }

        void configure(HostConfigEntry hostConfig, org.apache.sshd.client.session.ClientSession session) {
            if (sshConfig) {
                if (sshConfig.containsKey('StrictHostKeyChecking')) {
                    String value = sshConfig['StrictHostKeyChecking']
                    if (value == 'no') {
                        session.setServerKeyVerifier({ clientSession, remoteAddress, serverKey -> true })
                    }
                }
            }
        }
    }
}

