package com.gitcove.app

import com.gitcove.app.data.ai.AiCommit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * AI 提交信息生成纯逻辑回归（功能 4.6）：
 * 提示词 / 请求体 / 响应解析 / 输出清洗 / 本地规则回退 / URL 归一化。
 * 与 scripts/aicheck 的 JVM 语义验证共用断言；JGit 侧（aiDiffText）
 * 的语义验证见该工程。
 */
class AiCommitTest {

    @Test
    fun `normalizeBaseUrl 补协议去尾斜杠`() {
        assertEquals("https://api.deepseek.com/v1", AiCommit.normalizeBaseUrl("api.deepseek.com/v1/"))
        assertEquals("https://api.deepseek.com/v1", AiCommit.normalizeBaseUrl("https://api.deepseek.com/v1/"))
        assertEquals("http://localhost:8080/v1", AiCommit.normalizeBaseUrl("http://localhost:8080/v1"))
        assertEquals("", AiCommit.normalizeBaseUrl("   "))
    }

    @Test
    fun `buildUserPrompt 含文件清单与格式要求`() {
        val p = AiCommit.buildUserPrompt(listOf("a.kt", "b.kt"), "--- f\n+++ f\n+hello")
        assertTrue(p.contains("- a.kt"))
        assertTrue(p.contains("+hello"))
        assertTrue(p.contains("Conventional Commits"))
        assertTrue(p.contains("只输出提交信息这一行本身"))
    }

    @Test
    fun `buildRequestBody 字段与角色正确`() {
        val json = Json.parseToJsonElement(AiCommit.buildRequestBody("m1", "你好")).jsonObject
        assertEquals("m1", json["model"]!!.jsonPrimitive.content)
        assertEquals(256, json["max_tokens"]!!.jsonPrimitive.int)
        val msgs = json["messages"]!!.jsonArray
        assertEquals(2, msgs.size)
        assertEquals("system", msgs[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("user", msgs[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("你好", msgs[1].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `parseReply 正常响应取 content`() {
        val body = """
            {"id":"x","choices":[{"index":0,
            "message":{"role":"assistant","content":"feat: 新增导入功能"},
            "finish_reason":"stop"}],"usage":{"total_tokens":10}}
        """.trimIndent()
        assertEquals("feat: 新增导入功能", AiCommit.parseReply(body))
    }

    @Test
    fun `parseReply 错误响应转可读异常`() {
        try {
            AiCommit.parseReply("""{"error":{"message":"Incorrect API key provided"}}""")
            fail("应抛异常")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("Incorrect API key"))
        }
    }

    @Test
    fun `cleanOutput 去围栏引号并提取 CC 行`() {
        assertEquals("feat: x", AiCommit.cleanOutput("```\nfeat: x\n```"))
        assertEquals("feat: x", AiCommit.cleanOutput("\"feat: x\""))
        assertEquals("feat: x", AiCommit.cleanOutput("好的，这是为您生成的提交信息：\nfeat: x\n\n希望有帮助"))
        assertEquals("feat: x", AiCommit.cleanOutput("提交信息：feat: x"))
        assertEquals("", AiCommit.cleanOutput("   "))
    }

    @Test
    fun `localFallback 按路径推断类型`() {
        assertEquals("docs: update README.md", AiCommit.localFallback(listOf("README.md")))
        assertEquals("ci: update .github/workflows/ci.yml", AiCommit.localFallback(listOf(".github/workflows/ci.yml")))
        assertEquals(
            "build: update app/build.gradle.kts, gradle/libs.versions.toml",
            AiCommit.localFallback(listOf("app/build.gradle.kts", "gradle/libs.versions.toml"))
        )
        assertEquals(
            "chore: update a.kt, b.kt (+2 more)",
            AiCommit.localFallback(listOf("a.kt", "b.kt", "c.kt", "d.kt"))
        )
        assertEquals("chore: update", AiCommit.localFallback(emptyList()))
    }
}
