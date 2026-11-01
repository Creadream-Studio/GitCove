package com.gitcove.app

import android.content.Intent
import android.os.Build
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
import com.gitcove.app.data.remote.GitHubBrowserFlow
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.nav.GitCoveNavHost
import com.gitcove.app.ui.theme.GitCoveTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // MD2：关闭边缘到边缘，状态栏/导航栏由主题着色。
        // 注：targetSdk 35 在 Android 15+ 上此调用被系统忽略（强制 edge-to-edge），
        // 避让改由 WindowInsets 自适应处理（见 CoveTopAppBar / MainActivity 的 imePadding）。
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)
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
                    // 键盘避让：Android 15+（SDK 35+）强制 edge-to-edge 后 adjustResize 失效
                    // （窗口不再随键盘缩小），必须用 IME insets；Android 14- 窗口本身会缩小、
                    // ime inset 为 0。若在 14- 上也叠加 imePadding，电视等设备的 ROM 会报告
                    // 异常 IME inset（窗口已缩小仍非零），双重避让在底部露出窗口底色白边——
                    // 因此仅在 35+ 应用。根容器铺主题背景色，任何 inset 过渡区域露出的
                    // 都是主题色而非启动底色。
                    val rootModifier = if (Build.VERSION.SDK_INT >= 35) {
                        Modifier.fillMaxSize().imePadding()
                    } else {
                        Modifier.fillMaxSize()
                    }
                    Box(rootModifier.background(MaterialTheme.colors.background)) {
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
