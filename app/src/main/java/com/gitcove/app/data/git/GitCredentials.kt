package com.gitcove.app.data.git

import com.gitcove.app.data.store.AuthStore
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.net.URI

/**
 * 凭据解析：HTTPS 远端按 host 匹配访问令牌；
 * SSH 远端由 SshTransport 注册的密钥处理。
 */
object GitCredentials {

    fun hostOf(url: String): String? = runCatching {
        val uri = URI.create(url.trim())
        uri.host ?: url.substringAfter("://").substringBefore('/').substringBefore('@')
    }.getOrNull()

    fun isHttp(url: String): Boolean =
        url.trim().startsWith("http://") || url.trim().startsWith("https://")

    fun providerFor(url: String, auth: AuthStore): CredentialsProvider? {
        if (!isHttp(url)) return null
        val host = hostOf(url) ?: return null
        val token = auth.tokenFor(host) ?: return null
        val user = auth.usernameFor(host) ?: "git"
        return UsernamePasswordCredentialsProvider(user, token)
    }

    /** 平台识别（功能 2 / 47） */
    fun platformOf(url: String): String = when {
        url.contains("github.com") -> "GitHub"
        url.contains("gitlab.com") -> "GitLab"
        url.contains("gitea.com") || url.contains("/gitea") -> "Gitea"
        url.contains("bitbucket.org") -> "Bitbucket"
        else -> "Git"
    }

    /** 仓库显示名（去掉 .git） */
    fun repoNameOf(url: String): String =
        url.trim().trimEnd('/').substringAfterLast('/').removeSuffix(".git").ifBlank { "repository" }

    // ── 克隆地址协议（HTTPS / SSH / Git 只读） ─────────────────────

    enum class CloneProto { HTTPS, SSH, GIT }

    /** 按地址形式识别协议（识别不出按 HTTPS 处理） */
    fun detectProto(url: String): CloneProto = when {
        url.trim().startsWith("git://") -> CloneProto.GIT
        url.trim().startsWith("ssh://") || url.trim().startsWith("git@") -> CloneProto.SSH
        else -> CloneProto.HTTPS
    }

    /**
     * 从任意形式 Git 地址提取 host 与路径（.git 后缀保留），失败返回 null。
     * 支持：https://host/path、http://…、ssh://git@host/path、git://host/path、git@host:path
     */
    fun hostPathOf(url: String): Pair<String, String>? {
        val u = url.trim().trimEnd('/')
        if (u.isBlank()) return null
        return when {
            // scp 风格：git@host:path
            !u.contains("://") && u.contains('@') && u.contains(':') -> {
                val host = u.substringAfter('@').substringBefore(':').trim()
                val path = u.substringAfter(':').trim()
                if (host.isBlank() || path.isBlank()) null else host to path
            }
            u.startsWith("ssh://") -> {
                val rest = u.removePrefix("ssh://")
                val host = rest.substringBefore('/')
                    .substringAfter('@', missingDelimiterValue = rest.substringBefore('/'))
                    .substringBefore(':')
                    .trim()
                val path = rest.substringAfter('/', "").trim()
                if (host.isBlank() || path.isBlank()) null else host to path
            }
            u.contains("://") -> {
                val rest = u.substringAfter("://")
                val host = rest.substringBefore('/').substringAfter('@').substringBefore(':').trim()
                val path = rest.substringAfter('/', "").trim()
                if (host.isBlank() || path.isBlank()) null else host to path
            }
            else -> null
        }
    }

    /**
     * 把已输入的远端地址转换成目标协议形式（解析失败原样返回）：
     * https://host/path[.git] ⇄ git@host:path[.git] ⇄ git://host/path[.git]
     */
    fun convertToProto(url: String, proto: CloneProto): String {
        val (host, path) = hostPathOf(url) ?: return url
        return when (proto) {
            CloneProto.HTTPS -> "https://$host/$path"
            CloneProto.SSH -> "git@$host:$path"
            CloneProto.GIT -> "git://$host/$path"
        }
    }
}
