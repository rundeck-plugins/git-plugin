package com.rundeck.plugin.util

import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.transport.Transport
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase
import org.eclipse.jgit.transport.sshd.SshdSessionFactory

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.PublicKey

/**
 * SSH session factory using Apache MINA SSHD instead of JSch.
 * Provides support for modern SSH algorithms including RSA with SHA-2 signatures.
 */
class PluginSshSessionFactory implements TransportConfigCallback {
    private byte[] privateKey
    Map<String, String> sshConfig

    PluginSshSessionFactory(final byte[] privateKey) {
        this.privateKey = privateKey
    }

    @Override
    void configure(final Transport transport) {
        if (transport in SshTransport) {
            SshTransport sshTransport = (SshTransport) transport
            sshTransport.setSshSessionFactory(buildSessionFactory())
        }
    }

    private SshdSessionFactory buildSessionFactory() {
        return new CustomSshdSessionFactory(privateKey, sshConfig)
    }

    private static class CustomSshdSessionFactory extends SshdSessionFactory {
        private final byte[] privateKey
        private final Map<String, String> sshConfig
        private Path cachedKeyFile

        CustomSshdSessionFactory(byte[] privateKey, Map<String, String> sshConfig) {
            super(null, null)
            this.privateKey = privateKey
            this.sshConfig = sshConfig
        }

        @Override
        protected List<Path> getDefaultIdentities(File sshDir) {
            if (privateKey) {
                if (cachedKeyFile == null || !Files.exists(cachedKeyFile)) {
                    cachedKeyFile = Files.createTempFile("rundeck-git-key-", ".pem")
                    try {
                        Files.setPosixFilePermissions(cachedKeyFile, PosixFilePermissions.fromString("rw-------"))
                    } catch (UnsupportedOperationException ignored) {
                        // Non-POSIX filesystem (e.g. Windows)
                    }
                    Files.write(cachedKeyFile, privateKey)
                    cachedKeyFile.toFile().deleteOnExit()
                }
                return [cachedKeyFile]
            }
            return super.getDefaultIdentities(sshDir)
        }

        @Override
        protected ServerKeyDatabase getServerKeyDatabase(File homeDir, File sshDir) {
            if (sshConfig?.get('StrictHostKeyChecking') == 'no') {
                return new ServerKeyDatabase() {
                    @Override
                    List<PublicKey> lookup(String connectAddress, InetSocketAddress remoteAddress, ServerKeyDatabase.Configuration config) {
                        return Collections.emptyList()
                    }

                    @Override
                    boolean accept(String connectAddress, InetSocketAddress remoteAddress, PublicKey serverKey, ServerKeyDatabase.Configuration config, CredentialsProvider provider) {
                        return true
                    }
                }
            }
            return super.getServerKeyDatabase(homeDir, sshDir)
        }
    }
}
