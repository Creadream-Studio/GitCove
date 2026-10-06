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
}
