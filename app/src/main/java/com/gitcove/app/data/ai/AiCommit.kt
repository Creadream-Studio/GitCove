package com.gitcove.app.data.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * AI 提交信息生成 · 纯逻辑（提示词构建 / 请求体组装 / 响应解析 / 输出清洗 / 本地规则回退）。
 *
 * 不依赖 Android 与 OkHttp —— 与 UI、网络完全解耦，
 * 可在 JVM 测试工程中原样复制验证（scripts/aicheck），并在 app 单测中回归。
 */
object AiCommit {

    /** 注入提示词的 diff 长度上限（字符）：过长会撑爆上下文、拖慢生成并增加费用 */
    const val MAX_DIFF_CHARS = 24_000

    /** Conventional Commits 一行格式（清洗时优先提取匹配行） */
    private val CC_LINE = Regex("^[A-Za-z]+(?:\\([^)]{0,30}\\))?!?:\\s*\\S.*$")

    private const val SYSTEM_PROMPT =
        "你是 Git 提交信息生成器。根据用户给出的代码变更，输出一条且仅一条 Conventional Commits " +
            "格式的提交信息（type(scope): description），不输出任何解释、前后缀、引号或 Markdown 代码块。"

    /** 归一化 Base URL：缺协议补 https://，去尾部斜杠 */
    fun normalizeBaseUrl(raw: String): String {
        var s = raw.trim()
        if (s.isNotEmpty() && !s.contains("://")) s = "https://$s"
        return s.trimEnd('/')
    }

    /** 用户提示词：变更文件清单（防刷屏截前 30 个）+ 截断 diff + 格式硬性要求 */
    fun buildUserPrompt(files: List<String>, diff: String): String = buildString {
        appendLine("根据以下 Git 变更生成一条提交信息。")
        appendLine()
        appendLine("变更文件（${files.size} 个）：")
        files.take(30).forEach { appendLine("- $it") }
        if (files.size > 30) appendLine("…等共 ${files.size} 个")
        appendLine()
        appendLine("Diff（可能已截断）：")
        appendLine(diff.take(MAX_DIFF_CHARS))
        appendLine()
        appendLine("要求：")
        appendLine("1. 严格输出 Conventional Commits 格式：type(scope): description；scope 不明确时省略")
        appendLine("2. type 从中选最贴切的一个：feat fix docs style refactor perf test build ci chore revert")
        appendLine("3. description 用简体中文概括改动本质，不超过 30 字，结尾不加标点")
        appendLine("4. 只输出提交信息这一行本身")
    }

    /**
     * 组装 OpenAI 兼容 /chat/completions 请求体。
     * 手工构建 JSON（而非 @Serializable）：免编译器插件，逻辑可在纯 JVM 工程直接测试。
     */
    fun buildRequestBody(model: String, userPrompt: String, maxTokens: Int = 256, temperature: Double = 0.2): String =
        buildJsonObject {
            put("model", model)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
            put("messages", kotlinx.serialization.json.buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", "system")
                        put("content", SYSTEM_PROMPT)
                    }
                )
                add(
                    buildJsonObject {
                        put("role", "user")
                        put("content", userPrompt)
                    }
                )
            })
        }.toString()

    /** 解析 /chat/completions 响应，取 choices[0].message.content；错误体转可读异常 */
    fun parseReply(body: String): String {
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }
            .getOrElse { throw IllegalStateException("AI 响应不是有效 JSON") }
        root["error"].objOrNull()?.let { err ->
            val detail = err["message"].strOrNull() ?: err["code"].strOrNull() ?: "未知错误"
            throw IllegalStateException("AI 服务返回错误：$detail")
        }
        val content = root["choices"].arrOrNull()?.firstOrNull()
            ?.objOrNull()?.get("message")?.objOrNull()?.get("content")?.strOrNull()
        return content ?: throw IllegalStateException("AI 响应缺少 choices[0].message.content")
    }

    /**
     * 清洗模型输出：去 Markdown 围栏 → 优先取符合 Conventional Commits 的行
     * （模型偶尔加"好的，这是提交信息："之类的客套）→ 去引号与前缀 → 兜底取首个非空行。
     */
    fun cleanOutput(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("```")) {
            s = s.removePrefix("```").substringBefore("```")
        }
        val lines = s.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val picked = lines.firstOrNull { CC_LINE.matches(it) } ?: lines.firstOrNull() ?: return ""
        var r = picked.trim('`', '"', '\'', '“', '”', '「', '」')
        for (prefix in listOf("提交信息：", "提交信息:", "Commit message:", "Commit Message:", "提交信息")) {
            if (r.startsWith(prefix)) {
                r = r.removePrefix(prefix).trim()
                break
            }
        }
        return r.trim()
    }

    /**
     * AI 不可用时的本地规则回退：按文件路径推断 type，
     * 拼「type: update xxx」式可编辑建议（对标 chore: update xxx）。
     */
    fun localFallback(files: List<String>): String {
        if (files.isEmpty()) return "chore: update"
        val type = when {
            files.all { it.endsWith(".md", true) || it.startsWith("docs/", true) } -> "docs"
            files.all { it.startsWith(".github/", true) } &&
                files.any { it.startsWith(".github/workflows/", true) } -> "ci"
            files.all { "build.gradle" in it || "settings.gradle" in it ||
                it.endsWith(".properties", true) || it.endsWith(".toml", true) } -> "build"
            files.all { it.contains("/test", true) || it.contains("/androidTest", true) ||
                "Test" in it.substringAfterLast('/') } -> "test"
            else -> "chore"
        }
        val shown = files.take(2).map { if (it.length > 48) "…" + it.takeLast(47) else it }
        val suffix = if (files.size > 2) " (+${files.size - 2} more)" else ""
        return "$type: update ${shown.joinToString(", ")}$suffix"
    }

    // ── JSON 安全取值（Kotlinx 内置的 jsonObject/jsonArray 是抛异常版，这里做 null 宽容版）──
    private fun JsonElement?.objOrNull(): JsonObject? = this as? JsonObject
    private fun JsonElement?.arrOrNull(): JsonArray? = this as? JsonArray
    private fun JsonElement?.strOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull
}
