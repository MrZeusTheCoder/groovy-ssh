package org.hidetake.gradle.ssh.internal

import org.hidetake.gradle.ssh.internal.connection.ConnectionManager
import org.hidetake.gradle.ssh.internal.connection.ConnectionService
import org.hidetake.gradle.ssh.internal.session.SessionService
import org.hidetake.gradle.ssh.plugin.CompositeSettings
import org.hidetake.gradle.ssh.plugin.Remote
import org.hidetake.gradle.ssh.plugin.SshTaskHandler

import static org.gradle.util.ConfigureUtil.configure

/**
 * A delegate class of ssh task.
 *
 * @author hidetake.org
 */
class DefaultSshTaskHandler implements SshTaskHandler {
    /**
     * Task specific settings.
     * This overrides global settings.
     */
    private final CompositeSettings taskSpecificSettings = new CompositeSettings()

    private final List<Map> sessions = []

    void ssh(Closure closure) {
        assert closure, 'closure must be given'
        configure(closure, taskSpecificSettings)
    }

    void session(Remote remote, Closure closure) {
        assert remote, 'remote must be given'
        assert remote.host, "host must be given for the remote ${remote.name}"
        assert closure, 'closure must be given'
        sessions.add(remote: remote, closure: closure)
    }

    void session(Collection<Remote> remotes, Closure closure) {
        assert remotes, 'at least one remote must be given'
        remotes.each { remote -> session(remote, closure) }
    }

    void execute(CompositeSettings globalSettings) {
        def merged = CompositeSettings.DEFAULT + globalSettings + taskSpecificSettings

        def connectionService = ConnectionService.instance
        def sessionService = SessionService.instance

        connectionService.withManager(merged.connectionSettings) { ConnectionManager manager ->
            sessions.each { session ->
                session.delegate = sessionService.createDelegate(
                        session.remote as Remote, merged.operationSettings, manager)
            }
            sessions.each { session ->
                configure(session.closure as Closure, session.delegate)
            }
        }
    }
}