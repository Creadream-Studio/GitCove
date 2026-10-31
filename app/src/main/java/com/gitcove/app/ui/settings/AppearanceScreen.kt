package com.gitcove.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.gitcove.app.i18n.AppLanguage
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.components.CoveAccentPicker
import com.gitcove.app.ui.components.CoveBackButton
import com.gitcove.app.ui.components.CoveChip
import com.gitcove.app.ui.components.CoveSectionTitle
import com.gitcove.app.ui.components.CoveTopAppBar
import com.gitcove.app.ui.theme.CoveTheme
import com.gitcove.app.ui.theme.Md2Accent
import com.gitcove.app.ui.theme.ShapeStyle
import com.gitcove.app.ui.theme.ThemeMode

/** 外观（二级页面）：主题模式 / 强调色 / 圆角风格 / 语言 */
@Composable
fun AppearanceScreen(
    container: AppContainer,
    nav: androidx.navigation.NavHostController
) {
    val S = LocalStrings.current
    val c = CoveTheme.colors

    Scaffold(
        topBar = {
            CoveTopAppBar(
                title = S.sectionAppearance,
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
            // ── 主题 ──
            CoveSectionTitle(S.sectionAppearance)
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    CoveChip(
                        label = themeLabel(S, mode),
                        selected = container.themeMode.value == mode,
                        onClick = { container.setThemeMode(mode) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            // ── 强调色 ──
            CoveSectionTitle(S.sectionAccent)
            CoveAccentPicker(
                selected = container.accent.value,
                onSelect = { container.setAccent(it) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
            Text(
                S.accentHint,
                style = MaterialTheme.typography.caption,
                color = c.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            // ── 圆角风格 ──
            CoveSectionTitle(S.shapeStyle)
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                ShapeStyle.entries.forEach { style ->
                    CoveChip(
                        label = when (style) {
                            ShapeStyle.STANDARD -> S.shapeStandard
                            ShapeStyle.ROUNDED -> S.shapeRounded
                            ShapeStyle.COMPACT -> S.shapeCompact
                        },
                        selected = container.shapeStyle.value == style,
                        onClick = { container.setShapeStyle(style) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            // ── 语言 ──
            CoveSectionTitle(S.sectionLanguage)
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                AppLanguage.entries.forEach { lang ->
                    CoveChip(
                        label = if (lang == AppLanguage.SYSTEM) S.languageSystem else lang.displayName,
                        selected = container.appLanguage.value == lang,
                        onClick = { container.setAppLanguage(lang) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            Text(
                S.languageHint,
                style = MaterialTheme.typography.overline,
                color = c.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
