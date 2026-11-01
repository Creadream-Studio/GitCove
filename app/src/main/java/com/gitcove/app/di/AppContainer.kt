package com.gitcove.app.di

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.gitcove.app.data.git.GitCore
import com.gitcove.app.data.ai.AiChatClient
import com.gitcove.app.data.log.OpLog
import com.gitcove.app.data.remote.GitHubApi
import com.gitcove.app.data.store.AiConfigStore
import com.gitcove.app.data.store.AuthStore
import com.gitcove.app.data.store.RepoStore
import com.gitcove.app.i18n.AppLanguage
import com.gitcove.app.i18n.Strings
import com.gitcove.app.i18n.resolveStrings
import com.gitcove.app.ui.theme.Md2Accent
import com.gitcove.app.ui.theme.ShapeStyle
import com.gitcove.app.ui.theme.ThemeMode
import com.gitcove.app.util.AppDirs
import java.io.File

/**
 * 轻量手动依赖容器（AppContainer 模式）。
 * 文档推荐 Hilt，为控制 MVP 构建复杂度先使用手动注入，接口边界与 Hilt 一致，后续可平滑迁移。
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val auth = AuthStore(appContext)
    // 操作日志：外部应用专属目录（/storage/emulated/0/Android/data/<包名>/files/log），
    // 用户可直接用文件管理器取走排查；令牌/SSH/AI 配置等敏感数据仍留内部私有目录
    val opLog = OpLog(AppDirs.logDir(appContext))
    val repoStore = RepoStore(appContext)
    val gitCore = GitCore(auth, opLog)
    val githubApi = GitHubApi(auth)

    /** AI 生成提交信息（功能 4.6）：加密配置 + OpenAI 兼容客户端 */
    val aiConfig = AiConfigStore(appContext)
    val aiChat = AiChatClient(aiConfig)

    /** 内部仓库根目录已废弃：v0.4.8 起默认仓库根在外部应用专属目录
     *  /storage/emulated/0/Android/data/<包名>/files/repos/（免权限、用户可见可导出） */
    val repoParent: File = AppDirs.reposRoot(appContext).apply { mkdirs() }

    /** 新建 / 克隆默认父目录（设置页可改；未设置或目录失效时回退到应用默认仓库根） */
    val defaultRepoParent = mutableStateOf(
        auth.defaultRepoParent?.let { File(it) }?.takeIf { it.isDirectory } ?: repoParent
    )

    fun setDefaultRepoParent(dir: File) {
        auth.defaultRepoParent = dir.absolutePath
        defaultRepoParent.value = dir
    }

    /** 主题模式（响应式 + 持久化） */
    val themeMode = mutableStateOf(auth.themeMode)
    fun setThemeMode(mode: ThemeMode) {
        auth.themeMode = mode
        themeMode.value = mode
    }

    /** 应用语言（响应式 + 持久化；SYSTEM 跟随设备语言，首次启动默认） */
    val appLanguage = mutableStateOf(auth.appLanguage)
    fun setAppLanguage(lang: AppLanguage) {
        auth.appLanguage = lang
        appLanguage.value = lang
    }

    /** MD2 强调色（响应式 + 持久化） */
    val accent = mutableStateOf(auth.accent)
    fun setAccent(value: Md2Accent) {
        auth.accent = value
        accent.value = value
    }

    /** 圆角风格（响应式 + 持久化） */
    val shapeStyle = mutableStateOf(auth.shapeStyle)
    fun setShapeStyle(style: ShapeStyle) {
        auth.shapeStyle = style
        shapeStyle.value = style
    }

    /** 当前生效文案（响应式：读取 appLanguage 状态，切换语言立即全局生效；ViewModel 亦可在任意线程读取） */
    val strings: Strings get() = resolveStrings(appLanguage.value)
}
