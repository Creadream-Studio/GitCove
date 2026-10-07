package com.gitcove.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.nav.GitCoveNavHost
import com.gitcove.app.ui.theme.GitCoveTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as GitCoveApp).container
        setContent {
            // 语言：读取 container.strings（内部为响应式状态），设置中切换后立即全局生效
            CompositionLocalProvider(LocalStrings provides container.strings) {
                GitCoveTheme(
                    themeMode = container.themeMode.value,
                    dynamicColor = container.dynamicColor.value,
                    shapeStyle = container.shapeStyle.value
                ) {
                    GitCoveNavHost(container)
                }
            }
        }
    }
}
