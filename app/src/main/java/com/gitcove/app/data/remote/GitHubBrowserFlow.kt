package com.gitcove.app.data.remote

import android.net.Uri
import android.util.Base64
import com.gitcove.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * GitHub OAuth 浏览器授权（Authorization Code + PKCE，Custom Scheme 回调）：
 *
 * 1. 打开系统浏览器进入 GitHub 授权页
 *    （redirect_uri = gitcove://callback，携带 state 与 S256 code_challenge）
 * 2. 用户授权后 GitHub 跳回 gitcove://callback?code=…&state=…，
 *    由 MainActivity 捕获深链并转发到 [deepLinks]
 * 3. 用 code + code_verifier 换取 access_token
 *    （GitHub OAuth App 换 token 必须携带 client_secret，已内置；
 *      PKCE S256 为强制路径：challenge / verifier 缺失直接报错，不发请求）
 *
 * 仅申请 repo 权限：所有仓库的读写与列表。
 */
object GitHubBrowserFlow {

    private const val AUTHORIZE_URL = "https://github.com/login/oauth/authorize"
    private const val TOKEN_URL = "https://github.com/login/oauth/access_token"
    private const val SCOPE = "repo"

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 授权回调深链（MainActivity 转发进来），调用方按 state 过滤消费 */
    val deepLinks = MutableSharedFlow<Uri>(extraBufferCapacity = 1)

    /** MainActivity 转发 gitcove:// 深链 */
    fun onDeepLink(uri: Uri?) {
        if (uri != null && uri.scheme.equals("gitcove", ignoreCase = true)) {
            deepLinks.tryEmit(uri)
        }
    }

    /** PKCE 参数：verifier 随机串，challenge = BASE64URL(SHA256(verifier)) */
    data class Pkce(val verifier: String, val challenge: String)

    fun newPkce(): Pkce {
        val verifier = base64Url(ByteArray(32).also { SecureRandom().nextBytes(it) })
        val challenge = base64Url(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        )
        return Pkce(verifier, challenge)
    }

    /** 防 CSRF 的 state 随机串 */
    fun newState(): String = base64Url(ByteArray(16).also { SecureRandom().nextBytes(it) })

    private fun base64Url(bytes: ByteArray): String = Base64.encodeToString(
        bytes,
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    )

    /** 生成 GitHub 授权页地址（强制 PKCE：无 challenge 直接报错） */
    fun authorizeUrl(pkce: Pkce, state: String): String {
        require(pkce.challenge.isNotBlank()) { "PKCE code_challenge 缺失，拒绝发起授权" }
        require(state.isNotBlank()) { "OAuth state 缺失，拒绝发起授权" }
        return Uri.parse(AUTHORIZE_URL).buildUpon()
            .appendQueryParameter("client_id", BuildConfig.GITHUB_CLIENT_ID)
            .appendQueryParameter("redirect_uri", BuildConfig.OAUTH_REDIRECT_URI)
            .appendQueryParameter("scope", SCOPE)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", pkce.challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
            .toString()
    }

    /** 用授权码换 access_token（PKCE S256 + client_secret） */
    suspend fun exchange(code: String, pkce: Pkce): Result<GitHubDeviceFlow.TokenResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(pkce.verifier.isNotBlank()) { "PKCE code_verifier 缺失，拒绝换 token" }
                val builder = FormBody.Builder()
                    .add("client_id", BuildConfig.GITHUB_CLIENT_ID)
                    .add("code", code)
                    .add("redirect_uri", BuildConfig.OAUTH_REDIRECT_URI)
                    .add("code_verifier", pkce.verifier)
                if (BuildConfig.GITHUB_CLIENT_SECRET.isNotBlank()) {
                    builder.add("client_secret", BuildConfig.GITHUB_CLIENT_SECRET)
                }
                val req = Request.Builder()
                    .url(TOKEN_URL)
                    .header("Accept", "application/json")
                    .post(builder.build())
                    .build()
                client.newCall(req).execute().use { resp ->
                    require(resp.isSuccessful) { "HTTP ${resp.code}" }
                    json.decodeFromString<GitHubDeviceFlow.TokenResponse>(resp.body?.string().orEmpty())
                }
            }
        }
}
