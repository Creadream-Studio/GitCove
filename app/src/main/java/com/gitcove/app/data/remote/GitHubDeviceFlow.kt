package com.gitcove.app.data.remote

import com.gitcove.app.BuildConfig
import com.gitcove.app.data.store.AuthStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * GitHub OAuth 设备授权流（Device Flow）：
 *
 * 1. start()   → 拿到 user_code 与验证网址，用户在浏览器输入设备码
 * 2. pollOnce()→ 轮询换取 access_token（authorization_pending / slow_down / expired_token）
 * 3. fetchUser()→ 拉取当前账号的用户名与邮箱，用于生成 Git 身份
 *
 * 仅申请 repo 权限：所有仓库的读写与列表。
 */
object GitHubDeviceFlow {

    /** OAuth App 的 Client ID（BuildConfig 注入，可经 gradle 属性覆盖） */
    val CLIENT_ID = BuildConfig.GITHUB_CLIENT_ID

    /** 申请的权限：repo（私有/公有仓库读写与列表） */
    const val SCOPE = "repo"

    private const val DEVICE_CODE_URL = "https://github.com/login/device/code"
    private const val TOKEN_URL = "https://github.com/login/oauth/access_token"

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 第一步返回：设备码与用户码 */
    @Serializable
    data class DeviceCode(
        val device_code: String,
        val user_code: String,
        val verification_uri: String,
        val verification_uri_complete: String? = null,
        val expires_in: Int = 900,
        val interval: Int = 5
    ) {
        /** 打开浏览器用的完整链接（已带 user_code，免手动输入；缺失时退回手动页） */
        val openUrl: String get() = verification_uri_complete ?: verification_uri
    }

    /** 第二步返回：令牌或错误 */
    @Serializable
    data class TokenResponse(
        val access_token: String? = null,
        val token_type: String? = null,
        val error: String? = null,
        val error_description: String? = null,
        val interval: Int? = null
    )

    /** /user 返回的账号信息 */
    @Serializable
    data class GitHubUser(
        val login: String,
        val id: Long = 0,
        val name: String? = null,
        val email: String? = null,
        val avatar_url: String? = null
    )

    /** 账号摘要存入 AuthStore（头像解析用） */
    fun cacheAccount(auth: com.gitcove.app.data.store.AuthStore, user: GitHubUser) {
        auth.saveGithubAccount(
            com.gitcove.app.data.store.GitHubAccount(
                login = user.login,
                email = user.email?.takeIf { it.isNotBlank() },
                avatarUrl = user.avatar_url?.takeIf { it.isNotBlank() }
                    ?: "https://github.com/${user.login}.png"
            )
        )
    }

    /** 第一步：请求设备码 */
    suspend fun start(): Result<DeviceCode> = withContext(Dispatchers.IO) {
        runCatching {
            val body = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("scope", SCOPE)
                .build()
            val req = Request.Builder()
                .url(DEVICE_CODE_URL)
                .header("Accept", "application/json")
                .post(body)
                .build()
            client.newCall(req).execute().use { resp ->
                require(resp.isSuccessful) { "HTTP ${resp.code}" }
                json.decodeFromString<DeviceCode>(resp.body?.string().orEmpty())
            }
        }
    }

    /** 第二步：单次轮询（返回 pending / slow_down 时由调用方按 interval 继续轮询） */
    suspend fun pollOnce(deviceCode: String): TokenResponse = withContext(Dispatchers.IO) {
        runCatching {
            val body = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("device_code", deviceCode)
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .build()
            val req = Request.Builder()
                .url(TOKEN_URL)
                .header("Accept", "application/json")
                .post(body)
                .build()
            client.newCall(req).execute().use { resp ->
                json.decodeFromString<TokenResponse>(resp.body?.string().orEmpty())
            }
        }.getOrElse { TokenResponse(error = "network", error_description = it.message) }
    }

    /** 第三步：拉取当前账号信息（用户名 / 邮箱） */
    suspend fun fetchUser(token: String): Result<GitHubUser> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://api.github.com/user")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(req).execute().use { resp ->
                require(resp.isSuccessful) { "HTTP ${resp.code}" }
                json.decodeFromString<GitHubUser>(resp.body?.string().orEmpty())
            }
        }
    }

    /**
     * 授权完成后的落地：保存 github.com 令牌，并按 GitHub 账号生成一套 Git 身份
     * （邮箱缺失时使用 GitHub 官方 noreply 地址）。首个身份自动设为默认。
     * 返回：是否新建了 Git 身份。
     */
    fun saveAuth(auth: AuthStore, token: String, user: GitHubUser?): Boolean {
        auth.setToken("github.com", user?.login?.ifBlank { "git" } ?: "git", token)
        if (user == null) return false
        cacheAccount(auth, user)
        val email = user.email?.takeIf { it.isNotBlank() }
            ?: "${user.id}+${user.login}@users.noreply.github.com"
        val exists = auth.identities().any { it.name == user.login && it.email == email }
        if (exists) return false
        val identity = com.gitcove.app.data.store.GitIdentity(
            id = System.currentTimeMillis(),
            label = "GitHub",
            name = user.login,
            email = email
        )
        val wasEmpty = auth.identities().isEmpty()
        auth.saveIdentity(identity)
        if (wasEmpty) auth.setDefaultIdentity(identity.id)
        return true
    }
}
