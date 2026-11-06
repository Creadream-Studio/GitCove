package com.gitcove.app.data.backup

import android.content.Context
import android.util.Base64
import com.gitcove.app.data.store.AiConfigStore
import com.gitcove.app.data.store.AuthStore
import com.gitcove.app.data.store.GitHubAccount
import com.gitcove.app.data.store.GitIdentity
import com.gitcove.app.data.store.TokenEntry
import com.gitcove.app.i18n.AppLanguage
import com.gitcove.app.ui.theme.Md2Accent
import com.gitcove.app.ui.theme.ShapeStyle
import com.gitcove.app.ui.theme.ThemeMode
import com.gitcove.app.work.SyncWorker
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** 应用设置快照（null = 备份里没有这一项，导入时保持现状） */
@Serializable
data class BackupSettings(
    val themeMode: Int? = null,
    val appLanguage: String? = null,
    val accent: Int? = null,
    val shapeStyle: Int? = null,
    val defaultRepoParent: String? = null,
    val autoSyncEnabled: Boolean? = null,
    val autoSyncIntervalMin: Int? = null
)

/** SSH 密钥（含私钥原文；备份文件若设了密码则随整包加密） */
@Serializable
data class BackupSshKey(
    val name: String,
    val privateKey: String,
    val publicKey: String,
    val addedAt: Long? = null
)

/** AI 配置（OpenAI 兼容：Base URL / API Key / 模型名） */
@Serializable
data class BackupAi(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = ""
) {
    val usable: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}

/** 除仓库以外的全部数据 */
@Serializable
data class BackupPayload(
    val settings: BackupSettings? = null,
    val identities: List<GitIdentity> = emptyList(),
    val defaultIdentityId: Long? = null,
    val githubAccount: GitHubAccount? = null,
    val tokens: List<TokenEntry> = emptyList(),
    val sshKeys: List<BackupSshKey> = emptyList(),
    val ai: BackupAi? = null
) {
    /** 内容摘要（导入确认框 / 还原完成提示用） */
    fun summary(): String = buildList {
        if (settings != null) add("设置")
        if (identities.isNotEmpty()) add("身份 ${identities.size}")
        if (tokens.isNotEmpty()) add("令牌 ${tokens.size}")
        if (sshKeys.isNotEmpty()) add("SSH 密钥 ${sshKeys.size}")
        if (ai?.usable == true) add("AI 配置")
    }.joinToString(" · ").ifBlank { "空备份" }
}

/** 备份文件外壳：明文时 payload 直存；加密时 cipher = AES-GCM(payload JSON) */
@Serializable
data class BackupFile(
    val app: String = "GitCove",
    val format: Int = 1,
    val createdAt: Long,
    val encrypted: Boolean = false,
    val salt: String? = null,
    val iterations: Int? = null,
    val iv: String? = null,
    val payload: BackupPayload? = null,
    val cipher: String? = null
)

/** 还原结果统计（列表展示用） */
data class RestoreSummary(
    val settings: Boolean,
    val identities: Int,
    val tokens: Int,
    val sshKeys: Int,
    val ai: Boolean
) {
    fun text(): String = buildList {
        if (settings) add("设置")
        if (identities > 0) add("身份 $identities")
        if (tokens > 0) add("令牌 $tokens")
        if (sshKeys > 0) add("SSH 密钥 $sshKeys")
        if (ai) add("AI 配置")
    }.joinToString(" · ").ifBlank { "无变化" }
}

/** 备份文件格式 / 内容无效 */
class BackupFormatException : Exception("不是有效的 GitCove 备份文件")

/** 备份已加密，需要提供密码才能解析 */
class BackupPasswordNeededException : Exception("备份已加密")

/** 密码错误或密文损坏（GCM 校验失败） */
class BackupWrongPasswordException : Exception("密码错误或文件损坏")

/**
 * 备份与还原（v0.5.2）：备份「除仓库以外的所有数据」——
 * 应用设置 / Git 身份 / 访问令牌 / SSH 密钥（含私钥）/ AI 配置。
 *
 * - 文件格式：JSON（明文）；填了密码则整包 AES-256-GCM 加密（PBKDF2-HMAC-SHA256 派生密钥）。
 * - 导入两种模式：
 *   合并（默认）：只补本机缺的项——身份按 name+email 去重、令牌按 host 去重、
 *   SSH 密钥按文件名去重、AI 配置仅在本机未配置时写入、设置仅在仍是默认值时写入；
 *   覆盖：先清空对应数据再导入，完全以备份文件为准（含界面设置）。
 */
class BackupManager(
    private val context: Context,
    private val auth: AuthStore,
    private val ai: AiConfigStore
) {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // ── 收集 ────────────────────────────────────────────────────────────────

    fun collect(): BackupPayload = BackupPayload(
        settings = BackupSettings(
            themeMode = auth.themeMode.value,
            appLanguage = auth.appLanguage.code,
            accent = auth.accent.value,
            shapeStyle = auth.shapeStyle.value,
            defaultRepoParent = auth.defaultRepoParent,
            autoSyncEnabled = auth.autoSyncEnabled,
            autoSyncIntervalMin = auth.autoSyncIntervalMin
        ),
        identities = auth.identities(),
        defaultIdentityId = auth.defaultIdentityId.takeIf { it >= 0 },
        githubAccount = auth.githubAccount,
        tokens = auth.tokens(),
        sshKeys = auth.sshKeys().map { key ->
            BackupSshKey(
                name = key.name,
                privateKey = runCatching { File(auth.sshDir, key.name).readText() }.getOrDefault(""),
                publicKey = key.publicKey,
                addedAt = key.addedAt
            )
        }.filter { it.privateKey.isNotBlank() },
        ai = ai.snapshot().let {
            BackupAi(baseUrl = it.baseUrl, apiKey = it.apiKey, model = it.model)
        }
    )

    // ── 序列化 / 解析 ───────────────────────────────────────────────────────

    /** payload → 备份文件 JSON 文本。password 留空则不加密 */
    fun serialize(payload: BackupPayload, password: String?): String {
        if (password.isNullOrBlank()) {
            return json.encodeToString(
                BackupFile(createdAt = System.currentTimeMillis(), encrypted = false, payload = payload)
            )
        }
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val cipher = BackupCrypto.encrypt(
            data = json.encodeToString(payload).toByteArray(Charsets.UTF_8),
            password = password, salt = salt, iv = iv, iterations = PBKDF2_ITERATIONS
        )
        return json.encodeToString(
            BackupFile(
                createdAt = System.currentTimeMillis(),
                encrypted = true,
                salt = Base64.encodeToString(salt, Base64.NO_WRAP),
                iterations = PBKDF2_ITERATIONS,
                iv = Base64.encodeToString(iv, Base64.NO_WRAP),
                cipher = Base64.encodeToString(cipher, Base64.NO_WRAP)
            )
        )
    }

    /** 只读外壳：文件是否合法 / 是否加密 / 创建时间（不解析 payload，不触发密码） */
    fun peek(text: String): BackupFile {
        val f = runCatching { json.decodeFromString<BackupFile>(text) }
            .getOrElse { throw BackupFormatException() }
        if (f.app != "GitCove" || f.format > FORMAT) throw BackupFormatException()
        if (!f.encrypted && f.payload == null) throw BackupFormatException()
        if (f.encrypted && (f.salt == null || f.iv == null || f.cipher == null)) throw BackupFormatException()
        return f
    }

    /**
     * 备份文件 JSON → payload。
     * 加密文件必须传密码：缺密码抛 [BackupPasswordNeededException]，
     * 密码错误抛 [BackupWrongPasswordException]，格式不对抛 [BackupFormatException]。
     */
    fun parse(text: String, password: String?): BackupPayload {
        val f = peek(text)
        if (!f.encrypted) return f.payload!!
        if (password.isNullOrBlank()) throw BackupPasswordNeededException()
        val plain = runCatching {
            BackupCrypto.decrypt(
                data = Base64.decode(f.cipher, Base64.NO_WRAP),
                password = password,
                salt = Base64.decode(f.salt, Base64.NO_WRAP),
                iv = Base64.decode(f.iv, Base64.NO_WRAP),
                iterations = f.iterations ?: PBKDF2_ITERATIONS
            )
        }.getOrElse { throw BackupWrongPasswordException() }
        return runCatching { json.decodeFromString<BackupPayload>(String(plain, Charsets.UTF_8)) }
            .getOrElse { throw BackupFormatException() }
    }

    // ── 还原 ────────────────────────────────────────────────────────────────

    /**
     * 把备份内容写入本机。overwrite = false 合并（只补缺），true 覆盖（清空重导）。
     * 只动「除仓库以外」的数据，仓库列表与仓库文件一概不碰。
     */
    fun restore(payload: BackupPayload, overwrite: Boolean): RestoreSummary {
        val settings = payload.settings
        var settingsApplied = false

        // ── 设置 ──
        if (settings != null) {
            if (overwrite) {
                settings.themeMode?.let { auth.themeMode = ThemeMode.from(it) }
                settings.appLanguage?.let { auth.appLanguage = AppLanguage.from(it) }
                settings.accent?.let { auth.accent = Md2Accent.from(it) }
                settings.shapeStyle?.let { auth.shapeStyle = ShapeStyle.from(it) }
                settings.defaultRepoParent?.let { auth.defaultRepoParent = it }
                settings.autoSyncEnabled?.let { auth.autoSyncEnabled = it }
                settings.autoSyncIntervalMin?.let { auth.autoSyncIntervalMin = it }
                settingsApplied = true
            } else {
                // 合并：仅本机仍是默认值（未自定义过）的项才采用备份
                var touched = false
                if (auth.themeMode == ThemeMode.SYSTEM && settings.themeMode != null) {
                    auth.themeMode = ThemeMode.from(settings.themeMode); touched = true
                }
                if (auth.appLanguage == AppLanguage.SYSTEM && settings.appLanguage != null) {
                    auth.appLanguage = AppLanguage.from(settings.appLanguage); touched = true
                }
                if (auth.accent.value == 0 && settings.accent != null) {
                    auth.accent = Md2Accent.from(settings.accent); touched = true
                }
                if (auth.shapeStyle.value == 0 && settings.shapeStyle != null) {
                    auth.shapeStyle = ShapeStyle.from(settings.shapeStyle); touched = true
                }
                if (auth.defaultRepoParent == null && settings.defaultRepoParent != null) {
                    auth.defaultRepoParent = settings.defaultRepoParent; touched = true
                }
                if (!auth.autoSyncEnabled && settings.autoSyncEnabled == true) {
                    auth.autoSyncEnabled = true; touched = true
                }
                if (auth.autoSyncIntervalMin == DEFAULT_SYNC_MIN && settings.autoSyncIntervalMin != null) {
                    auth.autoSyncIntervalMin = settings.autoSyncIntervalMin; touched = true
                }
                settingsApplied = touched
            }
        }

        // ── Git 身份 ──
        var identityCount = 0
        if (payload.identities.isNotEmpty()) {
            if (overwrite) {
                auth.identities().forEach { auth.deleteIdentity(it.id) }
                payload.identities.forEach { auth.saveIdentity(it) }
                identityCount = payload.identities.size
                val def = payload.defaultIdentityId
                if (def != null && payload.identities.any { it.id == def }) auth.setDefaultIdentity(def)
            } else {
                val existing = auth.identities()
                val keys = existing.map { it.name.lowercase() to it.email.lowercase() }.toSet()
                var nextId = (existing.maxOfOrNull { it.id } ?: 0L) + 1
                payload.identities.forEach { inc ->
                    val key = inc.name.lowercase() to inc.email.lowercase()
                    if (key !in keys) {
                        auth.saveIdentity(inc.copy(id = nextId++))
                        identityCount++
                    }
                }
                // 本机还没有默认身份，而备份里指定了 → 采纳
                if (identityCount > 0 && auth.defaultIdentity() == null) {
                    val defId = payload.defaultIdentityId
                        ?.let { old -> payload.identities.firstOrNull { it.id == old } }
                        ?.let { src -> auth.identities().firstOrNull {
                            it.name == src.name && it.email == src.email }
                        }?.id
                    if (defId != null) auth.setDefaultIdentity(defId)
                }
            }
        }

        // ── GitHub 账号摘要 ──
        if (payload.githubAccount != null && (overwrite || auth.githubAccount == null)) {
            auth.saveGithubAccount(payload.githubAccount)
        }

        // ── 访问令牌 ──
        var tokenCount = 0
        if (payload.tokens.isNotEmpty()) {
            if (overwrite) {
                auth.tokens().forEach { auth.removeToken(it.host) }
            }
            val knownHosts = auth.tokens().map { it.host }.toSet()
            payload.tokens.forEach { t ->
                if (t.host !in knownHosts || overwrite) {
                    auth.setToken(t.host, t.username, t.token)
                    tokenCount++
                }
            }
        }

        // ── SSH 密钥 ──
        var sshCount = 0
        if (payload.sshKeys.isNotEmpty()) {
            if (overwrite) {
                auth.sshDir.listFiles()?.forEach { it.delete() }
            }
            val importable = if (overwrite) payload.sshKeys else payload.sshKeys.filter { b ->
                !File(auth.sshDir, b.name).exists() && !File(auth.sshDir, b.name + ".pub").exists()
            }
            importable.forEach { b ->
                auth.saveSshKey(b.name, b.privateKey, b.publicKey)
                sshCount++
            }
        }

        // ── AI 配置 ──
        var aiApplied = false
        if (payload.ai != null && (overwrite || !ai.isConfigured)) {
            val target = payload.ai
            if (overwrite || target.usable) {
                ai.save(target.baseUrl, target.apiKey, target.model)
                aiApplied = target.usable
            }
        }

        // ── 自动同步计划跟随设置变化 ──
        rescheduleSync()

        return RestoreSummary(settingsApplied, identityCount, tokenCount, sshCount, aiApplied)
    }

    private fun rescheduleSync() {
        runCatching {
            if (auth.autoSyncEnabled) SyncWorker.schedule(context, auth.autoSyncIntervalMin)
            else SyncWorker.cancel(context)
        }
    }

    companion object {
        const val FORMAT = 1
        const val PBKDF2_ITERATIONS = 120_000
        const val SALT_LEN = 16
        const val IV_LEN = 12
        const val DEFAULT_SYNC_MIN = 30
    }
}

/** 备份文件加解密（PBKDF2-HMAC-SHA256 派生 256 位密钥 + AES-256-GCM） */
internal object BackupCrypto {

    fun encrypt(data: ByteArray, password: String, salt: ByteArray, iv: ByteArray, iterations: Int): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(128, iv))
        return cipher.doFinal(data)
    }

    fun decrypt(data: ByteArray, password: String, salt: ByteArray, iv: ByteArray, iterations: Int): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(128, iv))
        return cipher.doFinal(data)
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(key, "AES")
    }
}
