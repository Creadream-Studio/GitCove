package com.gitcove.app.data.update

import android.content.Context
import android.content.Intent
import com.gitcove.app.data.log.OpLog
import com.gitcove.app.data.store.AuthStore
import com.gitcove.app.util.AppDirs
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.util.concurrent.TimeUnit

/** GitHub 项目仓库（Release 拉取源——公开只读接口不需要令牌） */
const val GITHUB_OWNER = "Creadream-Studio"
const val GITHUB_REPO = "GitCove"

/** Releases API 地址模板（%s/%s → owner / repo，解开时由 [UpdateChecker.releaseApiUrl] 提供绝对地址） */
const val RELEASES_URL = "https://api.github.com/repos/%s/%s/releases"

/** 发布条目（GitHub Releases API 的原始字段，只取正式版上的标签 / 说明） */
@Serializable
internal data class GitHubRelease(
    val tag_name: String,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val published_at: String? = null,
    val html_url: String? = null,
    val body: String? = null
)

/**
 * 版本更新检查状态（UI 通过 [UpdateChecker.state] 观察，无需轮询）。
 * - 只有 **正式版**（排除 Prerelease/Draft 的 Release）才会生成 [Available]。
 */
sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    object NoUpdate : UpdateState()            // 当前已是最新正式版
    data class Available(val release: Release) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

/** 发布摘要：只包含升级 UI 需要的信息 */
data class Release(
    val tagName: String,
    val publishedAt: Long? = null,
    val htmlUrl: String? = null,
    val description: String? = null
)

/**
 * 版本更新检查器：从 GitHub Releases 获取最新正式版，与本地 Version 比较。
 *
 * 使用场景：
 * - [checkOnLaunch]：App 启动时（设置 → 更新 开启「自动更新」后，每次启动触发）；
 * - [checkForUpdate]：设置页「检查更新」手动触发；
 * - 结果通过 [state] 暴露，[dismiss] 清除弹窗，避免重复提示。
 *
 * 所有网络调用在 IO 线程；调用方负责消费 state 并弹框。
 */
class UpdateChecker(
    context: Context,
    val auth: AuthStore? = null,
    private val owner: String = GITHUB_OWNER,
    private val repo: String = GITHUB_REPO,
    /** 供单元测试覆盖的 Release API 地址（内部用，不对外） */
    internal var releaseApiUrl: String = RELEASES_URL.format(owner, repo)
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val opLog = OpLog(AppDirs.logDir(context))
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val state: MutableStateFlow<UpdateState> = MutableStateFlow(UpdateState.Idle)

    /** 启动时自动检查（开关开启时触发一单） */
    fun checkOnLaunch() {
        if (auth?.autoUpdateEnabled != true) return
        scope.launch { checkForUpdate() }
    }

    /** 每次检查都有的入口（手动/启动时公用） */
    suspend fun checkForUpdate() {
        state.value = UpdateState.Checking
        val result = runCatching { fetchLatestStable() }
        result.onSuccess { state.value = it }
            .onFailure {
                opLog.append("update", "检查更新失败: ${it.message}")
                state.value = UpdateState.Error("检查更新失败: ${it.message}")
            }
    }

    /** 清掉弹窗（用户点了「以后再说」/返回） */
    fun dismiss() { state.value = UpdateState.Idle }

    /** 获取最新正式版 Release：无正式发布或全部是 Pre-Release/Draft，返回 NoUpdate */
    private suspend fun fetchLatestStable(): UpdateState = withContext(Dispatchers.IO) {
        val list = requireNotNull(fetchReleasesResult()?.getOrNull()) { "获取 Release 失败: 返回空数据" }
        val stable = stableReleases(list)
        val release = stable.maxByOrNull { it.publishedAt ?: 0L }
        if (release == null) UpdateState.NoUpdate
        else if (isVersion(newer = release.tagName, current = com.gitcove.app.BuildConfig.VERSION_NAME)) {
            UpdateState.Available(release)
        } else UpdateState.NoUpdate
    }

    private suspend fun fetchReleasesResult(): Result<List<GitHubRelease>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(releaseApiUrl).header("User-Agent", "GitCove/1.0").build()
            client.newCall(req).execute().use { resp ->
                require(resp.isSuccessful) { "GitHub Releases HTTP ${resp.code}" }
                json.decodeFromString<List<GitHubRelease>>(resp.body?.string().orEmpty())
            }
        }
    }
}

/** 语义比较：newer 是否比 current 新 */
fun isVersion(newer: String, current: String): Boolean = compareVersions(newer, current) > 0

/** 版本解析：strip "v" + 前半段，取正数数字段；不足长度按 0 补 */
fun parseVersion(tag: String): List<Long> {
    val clean = tag.replaceFirst("v".toRegex(), "").substringBefore('-')
    return runCatching {
        clean.split('.').mapNotNull { n -> n.toLong().takeIf { v -> v >= 0 } }
    }.getOrNull() ?: emptyList()
}

internal fun compareVersions(newer: String, current: String): Int {
    val a = parseVersion(newer)
    val b = parseVersion(current)
    repeat(maxOf(a.size, b.size)) { i ->
        val x = a.getOrNull(i) ?: 0L
        val y = b.getOrNull(i) ?: 0L
        if (x > y) return 1
        if (x < y) return -1
    }
    return 0
}

// ── 拼接 ──
private fun GitHubRelease.toRelease(): Release = Release(
    tagName = tag_name,
    publishedAt = published_at?.let { fs -> runCatching { Instant.parse(fs).toEpochMilli() }.getOrNull() },
    htmlUrl = html_url,
    description = body
)

/**
 * 从 Release 列表中筛选 **正式** 发布（排除 Prerelease / Draft），并转为 Release 摘要。
 *
 * 这是「只有正式版才会提示更新」的判定逻辑：一份列表里即使有更高的标签，只要是
 * Prerelease / Draft 都不会被返回，因此 [fetchLatestStable] 永远不会看到它。
 * 纯函数，方便单元测试。
 */
internal fun stableReleases(releases: List<GitHubRelease>): List<Release> =
    releases.filter { !it.prerelease && !it.draft }.map { it.toRelease() }

/** 在当前设备的浏览器中打开指定地址（下载页/项目页） */
fun openRelease(context: android.content.Context, url: String?) {
    val href = url ?: "https://github.com/Creadream-Studio/GitCove/releases"
    runCatching {
        context.startActivity(
            Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(href))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
