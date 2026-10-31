package com.gitcove.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.gitcove.app.di.AppContainer
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.components.CoveBackButton
import com.gitcove.app.ui.components.CoveDivider
import com.gitcove.app.ui.components.CoveSectionTitle
import com.gitcove.app.ui.components.CoveTopAppBar
import com.gitcove.app.ui.nav.Routes
import com.gitcove.app.ui.theme.CoveTheme
import com.gitcove.app.ui.theme.Md2Accent

/**
 * 设置首页（一级页面）：不再把所有选项堆在一页，而是按「账号 / 应用 / 其他」分组，
 * 每项进入对应的二级子页面 —— Git 身份、账号与令牌、SSH 密钥、外观、同步、日志、关于。
 */
@Composable
fun SettingsScreen(
    container: AppContainer,
    nav: androidx.navigation.NavHostController
) {
    val S = LocalStrings.current
    val c = CoveTheme.colors

    // 各分类的副标题：直接反映当前配置状态
    val identitySub = container.auth.defaultIdentity()
        ?.let { "${it.name} <${it.email}>" } ?: S.noIdentitySet
    val tokenCount = container.auth.tokens().size
    val accountsSub = if (tokenCount > 0) S.tokensCount(tokenCount) else S.noTokensSet
    val sshCount = container.auth.sshKeys().size
    val sshSub = if (sshCount > 0) S.sshKeyCount(sshCount) else S.noSshKeySet
    val appearanceSub = "${themeLabel(S, container.themeMode.value)} · ${accentLabel(S, container.accent.value)}"
    val syncSub = if (container.auth.autoSyncEnabled) S.syncOnSub(container.auth.autoSyncIntervalMin) else S.syncOffSub

    Scaffold(
        topBar = {
            CoveTopAppBar(
                title = S.settings,
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
            // ── 账号 ──
            CoveSectionTitle(S.settingsSectionAccount)
            SettingsNavItem(
                icon = Icons.Outlined.Person,
                title = S.sectionGitIdentity,
                subtitle = identitySub,
                onClick = { nav.navigate(Routes.SETTINGS_GIT) }
            )
            CoveDivider(insetStart = 56)
            SettingsNavItem(
                icon = Icons.Outlined.VpnKey,
                title = S.navAccounts,
                subtitle = accountsSub,
                onClick = { nav.navigate(Routes.SETTINGS_AUTH) }
            )
            CoveDivider(insetStart = 56)
            SettingsNavItem(
                icon = Icons.Outlined.Key,
                title = S.sectionSsh,
                subtitle = sshSub,
                onClick = { nav.navigate(Routes.SETTINGS_SSH) }
            )

            // ── 应用 ──
            CoveSectionTitle(S.settingsSectionApp)
            SettingsNavItem(
                icon = Icons.Outlined.Palette,
                title = S.sectionAppearance,
                subtitle = appearanceSub,
                onClick = { nav.navigate(Routes.SETTINGS_APPEARANCE) }
            )
            CoveDivider(insetStart = 56)
            SettingsNavItem(
                icon = Icons.Outlined.Sync,
                title = S.navSync,
                subtitle = syncSub,
                onClick = { nav.navigate(Routes.SETTINGS_SYNC) }
            )

            // ── 其他 ──
            CoveSectionTitle(S.settingsSectionOther)
            SettingsNavItem(
                icon = Icons.Outlined.ReceiptLong,
                title = S.sectionOpLog,
                onClick = { nav.navigate(Routes.LOG) }
            )
            CoveDivider(insetStart = 56)
            SettingsNavItem(
                icon = Icons.Outlined.Info,
                title = S.sectionAbout,
                onClick = { nav.navigate(Routes.SETTINGS_ABOUT) }
            )
            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }
}

/** 主题中文名（外观副标题用） */
internal fun themeLabel(
    S: com.gitcove.app.i18n.Strings,
    mode: com.gitcove.app.ui.theme.ThemeMode
): String = when (mode) {
    com.gitcove.app.ui.theme.ThemeMode.SYSTEM -> S.themeSystem
    com.gitcove.app.ui.theme.ThemeMode.DARK -> S.themeDark
    com.gitcove.app.ui.theme.ThemeMode.LIGHT -> S.themeLight
}

/** 强调色中文名（外观副标题用） */
internal fun accentLabel(S: com.gitcove.app.i18n.Strings, accent: Md2Accent): String = when (accent) {
    Md2Accent.BLUE -> S.accentBlue
    Md2Accent.TEAL -> S.accentTeal
    Md2Accent.INDIGO -> S.accentIndigo
    Md2Accent.CORAL -> S.accentCoral
    Md2Accent.AMBER -> S.accentAmber
}

/** 设置首页的导航行：着色圆形图标 + 标题 + 状态副标题 + 右箭头 */
@Composable
private fun SettingsNavItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    val c = CoveTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(32.dp)
                .background(c.primary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = c.primary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.subtitle2)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.caption,
                    color = c.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = c.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
