package com.gitcove.app.data.store

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.gitcove.app.data.ai.AiCommit

/** AI 服务商快捷预设（均为 OpenAI 兼容 /chat/completions 接口） */
data class AiPreset(val label: String, val baseUrl: String, val model: String)

/** 已保存的 AI 配置快照 */
data class AiConfig(val baseUrl: String, val apiKey: String, val model: String) {
    val complete: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}

/**
 * AI 配置存储：Base URL / API Key / 模型名。
 *
 * EncryptedSharedPreferences（Keystore 主密钥 AES256-GCM 加密落盘）——
 * 与平台令牌的 Base64 混淆不同，API Key 不以明文出现在 /data/data 下，
 * 且密钥材料由 Android Keystore 硬件级保管，root 备份也拿不到原文。
 */
class AiConfigStore(context: Context) {

    /** 快捷预设：点按即回填 Base URL 与推荐模型（API Key 不动） */
    val presets = listOf(
        AiPreset("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
        AiPreset("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
        AiPreset("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash"),
        AiPreset("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
    )

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "gitcove_ai_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var baseUrl: String
        get() = prefs.getString(KEY_BASE, "").orEmpty()
        private set(v) { prefs.edit().putString(KEY_BASE, v).apply() }

    var apiKey: String
        get() = prefs.getString(KEY_KEY, "").orEmpty()
        private set(v) { prefs.edit().putString(KEY_KEY, v).apply() }

    var model: String
        get() = prefs.getString(KEY_MODEL, "").orEmpty()
        private set(v) { prefs.edit().putString(KEY_MODEL, v).apply() }

    /** 三项齐全才算已配置（生成按钮的提示依据） */
    val isConfigured: Boolean get() = snapshot().complete

    fun snapshot(): AiConfig = AiConfig(baseUrl, apiKey, model)

    /** 整体保存：Base URL 归一化（补协议 / 去尾斜杠）后落盘 */
    fun save(baseUrl: String, apiKey: String, model: String) {
        prefs.edit()
            .putString(KEY_BASE, AiCommit.normalizeBaseUrl(baseUrl))
            .putString(KEY_KEY, apiKey.trim())
            .putString(KEY_MODEL, model.trim())
            .apply()
    }

    companion object {
        private const val KEY_BASE = "ai_base_url"
        private const val KEY_KEY = "ai_api_key"
        private const val KEY_MODEL = "ai_model"
    }
}
