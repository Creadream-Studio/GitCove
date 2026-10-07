package com.gitcove.app.di

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.gitcove.app.data.git.GitCore
import com.gitcove.app.data.log.OpLog
import com.gitcove.app.data.remote.GitHubApi
import com.gitcove.app.data.store.AuthStore
import com.gitcove.app.data.store.RepoStore
import com.gitcove.app.i18n.AppLanguage
import com.gitcove.app.i18n.Strings
import com.gitcove.app.i18n.resolveStrings
import com.gitcove.app.ui.theme.ShapeStyle
import com.gitcove.app.ui.theme.ThemeMode
import java.io.File

/**
 * 轻量手动依赖容器（AppContainer 模式）。
 * 文档推荐 Hilt，为控制 MVP 构建复杂度先使用手动注入，接口边界与 Hilt 一致，后续可平滑迁移。
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val auth = AuthStore(appContext)
    val opLog = OpLog(File(appContext.filesDir, "log"))
    val repoStore = RepoStore(appContext)
    val gitCore = GitCore(auth, opLog)
    val githubApi = GitHubApi(auth)

    /** 内部仓库根目录：/data/data/com.gitcove.app/files/repos/（文档 7.1） */
    val repoParent: File = File(appContext.filesDir, "repos").apply { mkdirs() }

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

    /** MD3 动态取色（响应式 + 持久化） */
    val dynamicColor = mutableStateOf(auth.dynamicColor)
    fun setDynamicColor(enabled: Boolean) {
        auth.dynamicColor = enabled
        dynamicColor.value = enabled
    }

    /** MD3 圆角风格（响应式 + 持久化） */
    val shapeStyle = mutableStateOf(auth.shapeStyle)
    fun setShapeStyle(style: ShapeStyle) {
        auth.shapeStyle = style
        shapeStyle.value = style
    }

    /** 当前生效文案（响应式：读取 appLanguage 状态，切换语言立即全局生效；ViewModel 亦可在任意线程读取） */
    val strings: Strings get() = resolveStrings(appLanguage.value)
}
