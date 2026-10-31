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

@Serializable
private data class ApiError(val message: String? = null)

/** 克隆前权限诊断结果（组织私有仓库 404/403 的可读化） */
sealed class RepoAccessIssue {
    abstract val repo: String
    abstract val detail: String
    data class TokenInvalid(override val repo: String) : RepoAccessIssue() { override val detail = "" }
    data class OrgSso(override val repo: String, override val detail: String) : RepoAccessIssue()
    data class OrgRestricted(override val repo: String, override val detail: String) : RepoAccessIssue()
    data class NoAccess(override val repo: String, override val detail: String) : RepoAccessIssue()
}

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

    /**
     * 拉取当前 GitHub 账号并缓存（用户名 / 邮箱 / 头像 URL），供头像解析使用。
     * 未配置令牌或网络失败时静默跳过。
     */
    suspend fun refreshAccountCache(): Result<Unit> = withContext(Dispatchers.IO) {
        val token = token()
            ?: return@withContext Result.failure(IllegalStateException("未配置 GitHub 令牌"))
        GitHubDeviceFlow.fetchUser(token).map { user ->
            GitHubDeviceFlow.cacheAccount(auth, user)
        }
    }

    /** 列出当前账号的仓库（含协作/组织仓库，自动翻页，需要已配置 github.com 令牌） */
    suspend fun listUserRepos(): Result<List<GitHubRepoInfo>> = withContext(Dispatchers.IO) {
        val token = token() ?: return@withContext Result.failure(IllegalStateException("请先在设置中配置 GitHub 访问令牌"))
        runCatching {
            val all = mutableListOf<GitHubRepoInfo>()
            var page = 1
            while (true) {
                val req = Request.Builder()
                    .url(
                        "https://api.github.com/user/repos" +
                            "?affiliation=owner,collaborator,organization_member&sort=updated&per_page=100&page=$page"
                    )
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/vnd.github+json")
                    .build()
                val list: List<GitHubRepoInfo> = client.newCall(req).execute().use { resp ->
                    require(resp.isSuccessful) { "GitHub API HTTP ${resp.code}" }
                    json.decodeFromString(resp.body?.string().orEmpty())
                }
                all += list
                if (list.size < 100 || page >= 10) break
                page++
            }
            all
        }
    }

    /**
     * 克隆前诊断 github.com HTTPS 仓库的访问权限。
     *
     * 组织未批准第三方应用 / 启用 SSO 时，GitHub 会把组织仓库对应用整体隐藏：
     * 列表接口不返回，git 克隆只报 404/403 的天书错误——这里用仓库 API
     * 提前探测并给出可读原因。返回 null 表示无障碍（或无令牌 / 非 https
     * github 地址，无法诊断，交给 git 自己报错）。
     */
    suspend fun diagnoseCloneAccess(url: String): RepoAccessIssue? = withContext(Dispatchers.IO) {
        if (!url.trim().startsWith("https://")) return@withContext null
        val (owner, repo) = githubOwnerRepo(url) ?: return@withContext null
        val token = token() ?: return@withContext null
        runCatching {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) return@use null
                val fullName = "$owner/$repo"
                val detail = runCatching {
                    json.decodeFromString<ApiError>(resp.body?.string().orEmpty()).message.orEmpty()
                }.getOrDefault("")
                when (resp.code) {
                    401 -> RepoAccessIssue.TokenInvalid(fullName)
                    403 -> if (detail.contains("SAML", ignoreCase = true)) {
                        RepoAccessIssue.OrgSso(fullName, detail)
                    } else {
                        RepoAccessIssue.OrgRestricted(fullName, detail)
                    }
                    else -> RepoAccessIssue.NoAccess(fullName, detail)   // 404 最常见：无权限 / 不存在
                }
            }
        }.getOrNull()
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
            val payload = org.json.JSONObject()
                .put("title", title)
                .put("head", head)
                .put("base", base)
                .put("body", body)
                .toString()
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
