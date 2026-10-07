package com.gitcove.app.data.store

import android.content.Context
import android.util.Base64
import com.gitcove.app.i18n.AppLanguage
import com.gitcove.app.ui.theme.ShapeStyle
import com.gitcove.app.ui.theme.ThemeMode
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

/**
 * 认证与配置存储：
 * - Git 身份（name/email）
 * - 平台访问令牌（Base64 混淆存储；Keystore/StrongBox 硬件保护列入路线图）
 * - SSH 密钥文件管理
 * - 主题 / 自动同步等设置
 */
class AuthStore(context: Context) {

    private val prefs = context.getSharedPreferences("gitcove_auth", Context.MODE_PRIVATE)
    val sshDir: File = File(context.filesDir, "ssh").apply { mkdirs() }

    // ── Git 身份 ────────────────────────────────────────────────────────────
    var gitName: String
        get() = prefs.getString("git_name", "") ?: ""
        set(value) = prefs.edit().putString("git_name", value).apply()

    var gitEmail: String
        get() = prefs.getString("git_email", "") ?: ""
        set(value) = prefs.edit().putString("git_email", value).apply()

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

    /** MD3 动态取色（Material You，Android 12+ 生效） */
    var dynamicColor: Boolean
        get() = prefs.getBoolean("dynamic_color", false)
        set(value) = prefs.edit().putBoolean("dynamic_color", value).apply()

    /** MD3 圆角风格：0 标准 / 1 圆润 / 2 紧凑 */
    var shapeStyle: ShapeStyle
        get() = ShapeStyle.from(prefs.getInt("shape_style", 0))
        set(value) = prefs.edit().putInt("shape_style", value.value).apply()

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
