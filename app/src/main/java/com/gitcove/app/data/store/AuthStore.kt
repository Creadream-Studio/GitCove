package com.gitcove.app.data.store

import android.content.Context
import android.util.Base64
import com.gitcove.app.i18n.AppLanguage
import com.gitcove.app.ui.theme.Md2Accent
import com.gitcove.app.ui.theme.ShapeStyle
import com.gitcove.app.ui.theme.ThemeMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/** SSH 密钥信息 */
data class SshKey(
    val name: String,           // 不含 .pub 的文件名
    val type: String,           // ssh-rsa / ecdsa-sha2-nistp256 ...
    val publicKey: String,      // 完整公钥单行
    val addedAt: Long
)

/** 访问令牌条目（功能 46） */
data class TokenEntry(
    val host: String,           // github.com / gitlab.com / gitea.com / 自定义
    val username: String,
    val token: String
)

/** Git 提交身份：可配置多套，其一为默认（提交时写入 user.name / user.email） */
@Serializable
data class GitIdentity(
    val id: Long,
    val label: String = "",     // 别名（可选），如：个人 / 公司
    val name: String,           // 提交作者姓名
    val email: String           // 提交作者邮箱
) {
    /** 展示标题：优先用别名 */
    val displayName: String get() = label.ifBlank { name }
}

/** 当前登录的 GitHub 账号摘要（头像解析用） */
@Serializable
data class GitHubAccount(
    val login: String,
    val email: String? = null,
    val avatarUrl: String? = null
)

/**
 * 认证与配置存储：
 * - Git 身份（name/email）
 * - 平台访问令牌（Base64 混淆存储；Keystore/StrongBox 硬件保护列入路线图）
 * - SSH 密钥文件管理
 * - 主题 / 自动同步等设置
 */
class AuthStore(context: Context) {

    private val prefs = context.getSharedPreferences("gitcove_auth", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    val sshDir: File = File(context.filesDir, "ssh").apply { mkdirs() }

    init {
        // 旧版本只存了一套 git_name / git_email：迁移为一条默认身份
        if (!prefs.contains("git_identities")) {
            val legacyName = prefs.getString("git_name", "").orEmpty()
            if (legacyName.isNotBlank()) {
                val legacy = GitIdentity(
                    id = System.currentTimeMillis(),
                    name = legacyName,
                    email = prefs.getString("git_email", "").orEmpty()
                )
                prefs.edit()
                    .putString("git_identities", json.encodeToString(listOf(legacy)))
                    .putLong("git_default_identity", legacy.id)
                    .apply()
            }
        }
    }

    // ── Git 身份（多套 + 默认）──────────────────────────────────────────────
    fun identities(): List<GitIdentity> = runCatching {
        json.decodeFromString<List<GitIdentity>>(prefs.getString("git_identities", "[]").orEmpty())
    }.getOrDefault(emptyList()).sortedBy { it.id }

    /** 新增或更新身份（按 id 匹配） */
    fun saveIdentity(identity: GitIdentity) {
        val list = identities().toMutableList()
        val idx = list.indexOfFirst { it.id == identity.id }
        if (idx >= 0) list[idx] = identity else list.add(identity)
        persistIdentities(list)
    }

    fun deleteIdentity(id: Long) {
        val list = identities().filter { it.id != id }
        persistIdentities(list)
        if (defaultIdentityId == id) {
            prefs.edit().putLong("git_default_identity", list.firstOrNull()?.id ?: -1L).apply()
        }
    }

    /** 设为默认身份 */
    fun setDefaultIdentity(id: Long) {
        if (identities().any { it.id == id }) {
            prefs.edit().putLong("git_default_identity", id).apply()
        }
    }

    var defaultIdentityId: Long
        get() = prefs.getLong("git_default_identity", -1L)
        private set(value) = prefs.edit().putLong("git_default_identity", value).apply()

    /** 当前默认身份（未显式指定时取第一条） */
    fun defaultIdentity(): GitIdentity? {
        val list = identities()
        if (list.isEmpty()) return null
        return list.firstOrNull { it.id == defaultIdentityId } ?: list.first()
    }

    private fun persistIdentities(list: List<GitIdentity>) {
        prefs.edit().putString("git_identities", json.encodeToString(list)).apply()
    }

    // ── GitHub 账号摘要（头像解析）──────────────────────────────────────────
    var githubAccount: GitHubAccount?
        get() = runCatching {
            prefs.getString("github_account", null)?.let {
                json.decodeFromString<GitHubAccount>(it)
            }
        }.getOrNull()
        private set(value) {
            prefs.edit()
                .putString("github_account", value?.let { json.encodeToString(it) } ?: "")
                .apply()
        }

    /** 保存/刷新 GitHub 账号摘要（登录成功或进入身份页时刷新） */
    fun saveGithubAccount(account: GitHubAccount) {
        githubAccount = account
    }

    // ── 访问令牌（host → user:token 混淆）────────────────────────────────────
    fun setToken(host: String, username: String, token: String) {
        val encoded = Base64.encodeToString("$username:$token".toByteArray(), Base64.NO_WRAP)
        prefs.edit().putString("token_$host", encoded).apply()
    }

    fun removeToken(host: String) {
        prefs.edit().remove("token_$host").apply()
    }

    fun tokenFor(host: String): String? {
        val encoded = prefs.getString("token_$host", null) ?: return null
        return runCatching {
            String(Base64.decode(encoded, Base64.NO_WRAP)).substringAfter(':')
        }.getOrNull()
    }

    fun usernameFor(host: String): String? {
        val encoded = prefs.getString("token_$host", null) ?: return null
        return runCatching {
            String(Base64.decode(encoded, Base64.NO_WRAP)).substringBefore(':')
        }.getOrNull()
    }

    fun tokens(): List<TokenEntry> =
        prefs.all.keys.filter { it.startsWith("token_") }.mapNotNull { key ->
            val host = key.removePrefix("token_")
            val encoded = prefs.getString(key, null) ?: return@mapNotNull null
            runCatching {
                val raw = String(Base64.decode(encoded, Base64.NO_WRAP))
                TokenEntry(host, raw.substringBefore(':'), raw.substringAfter(':'))
            }.getOrNull()
        }.sortedBy { it.host }

    // ── SSH 密钥（功能 45）──────────────────────────────────────────────────
    fun sshKeys(): List<SshKey> =
        sshDir.listFiles { f -> !f.name.endsWith(".pub") }
            ?.filter { File(it.absolutePath + ".pub").exists() }
            ?.map { keyFile ->
                val pubLine = runCatching { File(keyFile.absolutePath + ".pub").readText().trim() }.getOrDefault("")
                SshKey(
                    name = keyFile.name,
                    type = pubLine.substringBefore(' '),
                    publicKey = pubLine,
                    addedAt = keyFile.lastModified()
                )
            }?.sortedBy { it.name } ?: emptyList()

    fun saveSshKey(name: String, privateKey: String, publicKey: String): SshKey {
        val safeName = name.ifBlank { "key_${System.currentTimeMillis()}" }
        File(sshDir, safeName).writeText(privateKey.trimEnd() + "\n")
        File(sshDir, "$safeName.pub").writeText(publicKey.trimEnd() + "\n")
        return SshKey(safeName, publicKey.substringBefore(' '), publicKey.trimEnd(), System.currentTimeMillis())
    }

    fun deleteSshKey(name: String) {
        File(sshDir, name).delete()
        File(sshDir, "$name.pub").delete()
    }

    /** 指纹用于 UI 展示（MD5 简短指纹） */
    fun fingerprintOf(publicKey: String): String = runCatching {
        val blob = Base64.decode(publicKey.substringAfter(' ', ""), Base64.DEFAULT)
        val md = MessageDigest.getInstance("MD5").digest(blob)
        md.joinToString(":") { String.format("%02x", it) }
    }.getOrDefault("")

    // ── 应用设置 ────────────────────────────────────────────────────────────
    var themeMode: ThemeMode
        get() = ThemeMode.from(prefs.getInt("theme_mode", 0))
        set(value) = prefs.edit().putInt("theme_mode", value.value).apply()

    /** 应用语言：system（跟随设备语言，首次启动默认）/ zh / en */
    var appLanguage: AppLanguage
        get() = AppLanguage.from(prefs.getString("app_language", AppLanguage.SYSTEM.code))
        set(value) = prefs.edit().putString("app_language", value.code).apply()

    /** MD2 强调色：0 天蓝 / 1 湾青 / 2 靛紫 / 3 珊瑚 / 4 琥珀 */
    var accent: Md2Accent
        get() = Md2Accent.from(prefs.getInt("accent", 0))
        set(value) = prefs.edit().putInt("accent", value.value).apply()

    /** 圆角风格：0 标准 / 1 圆润 / 2 紧凑 */
    var shapeStyle: ShapeStyle
        get() = ShapeStyle.from(prefs.getInt("shape_style", 0))
        set(value) = prefs.edit().putInt("shape_style", value.value).apply()

    /** 默认保存目录：新建 / 克隆仓库时默认使用的父目录；null 表示使用应用默认仓库根 */
    var defaultRepoParent: String?
        get() = prefs.getString("default_repo_parent", null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString("default_repo_parent", value).apply()

    /** 自动同步（功能 19/20/21） */
    var autoSyncEnabled: Boolean
        get() = prefs.getBoolean("auto_sync", false)
        set(value) = prefs.edit().putBoolean("auto_sync", value).apply()

    var autoSyncIntervalMin: Int
        get() = prefs.getInt("auto_sync_interval", 30)
        set(value) = prefs.edit().putInt("auto_sync_interval", value).apply()

    // ── 最近访问（功能 63）──────────────────────────────────────────────────
    fun pushRecentFile(repoId: Long, path: String) {
        val list = recentFiles(repoId).toMutableList()
        list.remove(path)
        list.add(0, path)
        prefs.edit().putString("recent_$repoId", list.take(20).joinToString("\n")).apply()
    }

    fun recentFiles(repoId: Long): List<String> =
        prefs.getString("recent_$repoId", null)?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
}
