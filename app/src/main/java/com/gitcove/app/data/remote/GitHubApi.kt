package com.gitcove.app.data.remote

import com.gitcove.app.data.store.AuthStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** GitHub 仓库信息（用于"一键克隆"列表，功能 2） */
@Serializable
data class GitHubRepoInfo(
    val full_name: String,
    val clone_url: String,
    val ssh_url: String? = null,
    @SerialName("private") val isPrivate: Boolean = false,
    val description: String? = null,
    val default_branch: String = "main"
)

@Serializable
private data class PullRequestCreated(
    val html_url: String = "",
    val number: Int = 0
)

/**
 * GitHub REST API v3（功能 2 / 40 / 46）：
 * - 列出我的仓库
 * - 创建 Pull Request
 */
class GitHubApi(private val auth: AuthStore) {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun token(): String? = auth.tokenFor("github.com")

    /** 列出当前账号的仓库（需要已配置 github.com 令牌） */
    suspend fun listUserRepos(): Result<List<GitHubRepoInfo>> = withContext(Dispatchers.IO) {
        val token = token() ?: return@withContext Result.failure(IllegalStateException("请先在设置中配置 GitHub 访问令牌"))
        runCatching {
            val req = Request.Builder()
                .url("https://api.github.com/user/repos?per_page=100&sort=updated")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(req).execute().use { resp ->
                require(resp.isSuccessful) { "GitHub API HTTP ${resp.code}" }
                json.decodeFromString(resp.body?.string().orEmpty())
            }
        }
    }

    /** 功能 40：创建 Pull Request，返回 PR 页面 URL */
    suspend fun createPullRequest(
        owner: String,
        repo: String,
        head: String,
        base: String,
        title: String,
        body: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val token = token() ?: return@withContext Result.failure(IllegalStateException("请先在设置中配置 GitHub 访问令牌"))
        runCatching {
            val payload = json.encodeToString(
                kotlinx.serialization.json.JsonObject(mapOf(
                    "title" to kotlinx.serialization.json.JsonPrimitive(title),
                    "head" to kotlinx.serialization.json.JsonPrimitive(head),
                    "base" to kotlinx.serialization.json.JsonPrimitive(base),
                    "body" to kotlinx.serialization.json.JsonPrimitive(body)
                ))
            )
            val req = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/pulls")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                require(resp.isSuccessful) {
                    "创建 PR 失败 HTTP ${resp.code}: ${resp.body?.string()?.take(200)}"
                }
                json.decodeFromString<PullRequestCreated>(resp.body?.string().orEmpty()).html_url
            }
        }
    }

    companion object {
        /** 从远程 URL 解析 github owner/repo（支持 https 与 ssh 格式） */
        fun githubOwnerRepo(url: String?): Pair<String, String>? {
            if (url.isNullOrBlank()) return null
            val cleaned = url.trim()
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("git@github.com:")
                .removeSuffix(".git")
            if (!cleaned.startsWith("github.com/")) return null
            val parts = cleaned.removePrefix("github.com/").split('/')
            return if (parts.size >= 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                parts[0] to parts[1]
            } else null
        }
    }
}
