package com.gitcove.app.data.git

import com.gitcove.app.data.store.AuthStore
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import org.eclipse.jgit.transport.JschConfigSessionFactory
import org.eclipse.jgit.transport.OpenSshConfig
import org.eclipse.jgit.transport.SshSessionFactory
import org.eclipse.jgit.util.FS

/**
 * SSH 传输层（功能 45）：
 * 注册应用内 ssh/ 目录全部私钥到 JSch 会话工厂，
 * 支持 RSA / ECDSA / Ed25519（导入），跳过 StrictHostKeyChecking 便于工具类使用。
 *
 * 注意：JGit 5.x 的 JSch 工厂类位于 org.eclipse.jgit.transport 包
 * （6.x 才迁移到 org.eclipse.jgit.transport.ssh.jsch）。
 */
object SshTransport {

    @Volatile
    private var configured = false

    fun setup(auth: AuthStore) {
        if (configured) return
        synchronized(this) {
            if (configured) return
            val factory = object : JschConfigSessionFactory() {
                override fun configureJSch(jsch: JSch) {
                    super.configureJSch(jsch)
                    auth.sshDir.listFiles { f -> f.isFile && !f.name.endsWith(".pub") }?.forEach { key ->
                        runCatching { jsch.addIdentity(key.absolutePath) }
                    }
                }

                override fun configure(hc: OpenSshConfig.Host, session: Session) {
                    session.setConfig("StrictHostKeyChecking", "no")
                    session.setConfig("PreferredAuthentications", "publickey,password")
                }
            }
            SshSessionFactory.setInstance(factory)
            configured = true
        }
    }
}
