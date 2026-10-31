package com.gitcove.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gitcove.app.di.AppContainer
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.components.CoveBackButton
import com.gitcove.app.ui.components.CoveDivider
import com.gitcove.app.ui.components.CoveKeyValueRow
import com.gitcove.app.ui.components.CoveSectionTitle
import com.gitcove.app.ui.components.CoveTopAppBar
import com.gitcove.app.ui.theme.CoveTheme

/** 关于（二级页面）：版本、组件与开源信息 */
@Composable
fun AboutScreen(
    container: AppContainer,
    nav: androidx.navigation.NavHostController
) {
    val S = LocalStrings.current
    val c = CoveTheme.colors

    Scaffold(
        topBar = {
            CoveTopAppBar(
                title = S.sectionAbout,
                navigationIcon = { CoveBackButton { nav.popBackStack() } }
            )
        },
        backgroundColor = c.background
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            CoveSectionTitle(S.appName)
            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(
                    S.aboutDesc,
                    style = MaterialTheme.typography.body2,
                    color = c.onSurface
                )
            }
            CoveDivider()
            CoveKeyValueRow("Version", com.gitcove.app.BuildConfig.VERSION_NAME)
            CoveKeyValueRow("Core", "JGit 5.13")
            CoveKeyValueRow("UI", "Kotlin + Compose (MD2)")
            CoveKeyValueRow("License", "Apache 2.0")
            CoveDivider()
            Text(
                S.aboutLink,
                style = MaterialTheme.typography.caption,
                color = c.secondary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
