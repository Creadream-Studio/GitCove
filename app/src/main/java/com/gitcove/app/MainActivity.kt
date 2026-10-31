package com.gitcove.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.nav.GitCoveNavHost
import com.gitcove.app.ui.theme.GitCoveTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // MD2：关闭边缘到边缘，状态栏/导航栏由主题着色
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)
        val container = (application as GitCoveApp).container
        setContent {
            // 语言：读取 container.strings（内部为响应式状态），设置中切换后立即全局生效
            CompositionLocalProvider(LocalStrings provides container.strings) {
                GitCoveTheme(
                    themeMode = container.themeMode.value,
                    accent = container.accent.value,
                    shapeStyle = container.shapeStyle.value
                ) {
                    GitCoveNavHost(container)
                }
            }
        }
    }
}
