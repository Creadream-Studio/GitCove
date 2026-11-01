package com.gitcove.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.gitcove.app.data.remote.GitHubBrowserFlow
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.nav.GitCoveNavHost
import com.gitcove.app.ui.theme.GitCoveTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ── 统一 edge-to-edge（v0.4.8）：全版本显式启用 ──────────────────────────
        // 此前依赖 setDecorFitsSystemWindows(true) 让系统避让状态栏，但 targetSdk 35 在
        // Android 15+ 上该调用被系统忽略（强制 edge-to-edge），而个别魔改 ROM 上又出现
        // "半生效"：窗口仍被系统避让的同时 insets 被清零，Compose 的 statusBarsPadding
        // 拿到 0 —— 表现为"部分系统上仓库页顶部工具栏被状态栏挡住"。
        //
        // 现在不再依赖 ROM 对该 API 的解释：全部版本主动 edge-to-edge（false），
        // 内容避让统一交给 WindowInsets（CoveTopAppBar 的 safeDrawing 顶部避让、
        // 根部 imePadding、M2 Scaffold 的 contentWindowInsets），行为一致可预期。
        //
        // 连带收益：edge-to-edge 下窗口永不随键盘缩小，adjustResize 语义失效是确定的，
        // 因此 imePadding 可全版本应用——v0.4.7 电视 ROM"窗口已缩小仍报非零 IME inset
        // 双重避让白边"的根因（setDecorFitsSystemWindows(true)+imePadding 并存）随之消失。
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // 冷启动直接由 gitcove://callback 拉起时，同样转发授权回调
        GitHubBrowserFlow.onDeepLink(intent?.data)
        val container = (application as GitCoveApp).container
        setContent {
            // 语言：读取 container.strings（内部为响应式状态），设置中切换后立即全局生效
            CompositionLocalProvider(LocalStrings provides container.strings) {
                GitCoveTheme(
                    themeMode = container.themeMode.value,
                    accent = container.accent.value,
                    shapeStyle = container.shapeStyle.value
                ) {
                    // 键盘避让：edge-to-edge（全版本）下窗口不随键盘缩小，必须用 IME insets；
                    // 根容器铺主题背景色，任何 inset 过渡区域露出的都是主题色而非启动底色。
                    Box(Modifier.fillMaxSize().imePadding().background(MaterialTheme.colors.background)) {
                        GitCoveNavHost(container)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 浏览器授权完成跳回 gitcove://callback：转发给等待中的登录对话框
        GitHubBrowserFlow.onDeepLink(intent.data)
    }
}
