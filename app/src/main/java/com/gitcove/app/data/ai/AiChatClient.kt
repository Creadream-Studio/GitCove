package com.gitcove.app.data.ai

import com.gitcove.app.data.store.AiConfigStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容 /chat/completions 客户端（功能 4.6）。
 * 复用项目已有 OkHttp 依赖，不引入新网络库；生成类请求耗时明显长于
 * GitHub API，故独立建客户端实例（读超时 90s，GitHub API 为 30s）。
 */
class AiChatClient(private val config: AiConfigStore) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * 测试连通性：用界面上尚未保存的配置发一条极小请求，返回模型回话。
     * 401/404/429 等 HTTP 错误会转成带响应详情的可读异常。
     */
    suspend fun testConnection(baseUrl: String, apiKey: String, model: String): String =
        chat(baseUrl, apiKey, model, "Reply with exactly: OK", maxTokens = 16)

    /**
     * 生成提交信息（走已保存配置）：暂存 diff → 提示词 → /chat/completions → 清洗。
     * 返回空串视为失败（模型没有产出可用信息）。
     */
    suspend fun generateCommitMessage(files: List<String>, diff: String): String {
        val cfg = config.snapshot()
        if (!cfg.complete) throw IllegalStateException("AI 未配置")
        val reply = chat(cfg.baseUrl, cfg.apiKey, cfg.model, AiCommit.buildUserPrompt(files, diff))
        val cleaned = AiCommit.cleanOutput(reply)
        if (cleaned.isBlank()) throw IllegalStateException("AI 返回了空内容")
        return cleaned
    }

    private suspend fun chat(
        baseUrl: String,
        apiKey: String,
        model: String,
        userPrompt: String,
        maxTokens: Int = 256
    ): String = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
            throw IllegalStateException("Base URL / API Key / 模型名称不完整")
        }
        val url = AiCommit.normalizeBaseUrl(baseUrl) + "/chat/completions"
        val body = AiCommit.buildRequestBody(model, userPrompt, maxTokens)
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body)
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                // 错误体多为 {"error":{"message":...}}：解析出可读信息；非 JSON 兜底原文前 200 字
                val detail = runCatching { AiCommit.parseReply(text) }.getOrElse { text.take(200) }
                throw IllegalStateException("HTTP ${resp.code}：$detail")
            }
            AiCommit.parseReply(text)
        }
    }
}
