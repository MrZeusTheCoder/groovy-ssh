package org.hidetake.gradle.ssh.internal.session

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.agentproxy.ConnectorFactory
import com.jcraft.jsch.agentproxy.RemoteIdentityRepository
import groovy.util.logging.Slf4j
import org.hidetake.gradle.ssh.api.Remote
import org.hidetake.gradle.ssh.api.SshSettings

import static org.hidetake.gradle.ssh.internal.session.Retry.retry

/**
 * Factory and lifecycle manager class of JSch session.
 *
 * @author hidetake.org
 */
@Slf4j
class SessionManager {
    protected static final LOCALHOST = '127.0.0.1'

    final SshSettings sshSettings
    final JSch jsch
    final List<Session> sessions = []

    /**
     * Constructor.
     *
     * @param sshSettings1 ssh settings
     * @return a SessionManager instance
     */
    def SessionManager(SshSettings sshSettings1) {
        sshSettings = sshSettings1
        jsch = new JSch()

        if (sshSettings.knownHosts == SshSettings.allowAnyHosts) {
            jsch.setConfig('StrictHostKeyChecking', 'no')
            log.info('Strict host key checking is turned off. Use only for testing purpose.')
        } else {
            jsch.setKnownHosts(sshSettings.knownHosts.path)
            jsch.setConfig('StrictHostKeyChecking', 'yes')
            log.debug("Using known-hosts file: ${sshSettings.knownHosts.path}")
        }
    }

    /**
     * Establish a JSch session.
     *
     * @param remote target remote host
     * @return a JSch session
     */
    Session create(Remote remote) {
        if (remote.gateway) {
            def session = create(remote.gateway)
            def localPort = session.setPortForwardingL(0, remote.host, remote.port)
            log.info("Enabled local port forwarding from $localPort to ${remote.host}:${remote.port}")
            createVia(remote, LOCALHOST, localPort)
        } else {
            createVia(remote, remote.host, remote.port)
        }
    }

    /**
     * Establish a JSch session via given host and port.
     *
     * @param remote target remote host
     * @param host endpoint host (usually <code>remote.host</code>)
     * @param port endpoint port (usually <code>remote.port</code>)
     * @return a JSch session
     */
    protected Session createVia(Remote remote, String host, int port) {
        retry(sshSettings.retryCount, sshSettings.retryWaitSec) {
            def session = jsch.getSession(remote.user, host, port)
            if (remote.password) {
                session.password = remote.password
            }

            if (remote.agent) {
                jsch.identityRepository = remoteIdentityRepository
            } else {
                jsch.identityRepository = null    /* null means the default repository */
                jsch.removeAllIdentity()
                if (remote.identity) {
                    jsch.addIdentity(remote.identity.path, remote.passphrase as String)
                } else if (sshSettings.identity) {
                    jsch.addIdentity(sshSettings.identity.path, sshSettings.passphrase as String)
                }
            }

            session.connect()
            log.info("Established a session to $remote via $host:$port")
            sessions.add(session)
            session
        }
    }

    /**
     * Disconnect all sessions.
     */
    void disconnect() {
        sessions*.disconnect()
    }

    @Lazy
    protected remoteIdentityRepository = {
        def connectorFactory = ConnectorFactory.getDefault()
        def connector = connectorFactory.createConnector()
        new RemoteIdentityRepository(connector)
    }()
}
