package com.gitcove.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gitcove.app.BuildConfig
import com.gitcove.app.di.AppContainer
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.components.CoveBackButton
import com.gitcove.app.ui.components.CoveButton
import com.gitcove.app.ui.components.CoveDivider
import com.gitcove.app.ui.components.CoveOutlinedButton
import com.gitcove.app.ui.components.CoveSectionTitle
import com.gitcove.app.ui.components.CoveTopAppBar
import com.gitcove.app.ui.theme.CoveTheme
import com.gitcove.app.data.update.Release
import com.gitcove.app.data.update.UpdateState
import com.gitcove.app.data.update.openRelease
import kotlinx.coroutines.launch

/** 更新（二级页面）：自动更新开关 + 手动检查更新 */
@Composable
fun UpdateScreen(
    container: AppContainer,
    nav: androidx.navigation.NavHostController
) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    val state by container.updateChecker.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            CoveTopAppBar(
                title = S.navUpdate,
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
            // ── 自动更新开关 ──
            CoveSectionTitle(S.updateAutoLabel)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(S.updateAutoDesc, style = MaterialTheme.typography.body2)
                }
                Switch(
                    checked = container.auth.autoUpdateEnabled,
                    onCheckedChange = { enabled ->
                        container.auth.autoUpdateEnabled = enabled
                        if (enabled) scope.launch { container.updateChecker.checkForUpdate() }
                    }
                )
            }
            CoveDivider()

            // ── 发现正式最新版的落地提示 ──
            if (state is UpdateState.Available) {
                val release = (state as UpdateState.Available).release
                UpdateBanner(
                    release = release,
                    currentVersion = BuildConfig.VERSION_NAME,
                    onDownload = {
                        container.updateChecker.dismiss()
                        openRelease(context, release.htmlUrl)
                    },
                    onDismiss = { container.updateChecker.dismiss() }
                )
            }

            // ── 检查状态 + 手动检查 ──
            Text(
                when (state) {
                    is UpdateState.Checking -> S.checkingUpdate
                    is UpdateState.NoUpdate -> S.updateNoNewer
                    is UpdateState.Error -> S.updateCheckFailed
                    else -> ""
                },
                style = MaterialTheme.typography.caption,
                color = c.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                CoveButton(
                    text = S.checkUpdate,
                    onClick = { scope.launch { container.updateChecker.checkForUpdate() } },
                    enabled = state !is UpdateState.Checking
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 发现最新版本时的横条（闪色底 + 箭头按钮） */
@Composable
private fun UpdateBanner(
    release: Release,
    currentVersion: String,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .padding(12.dp)
                .background(c.primary.copy(alpha = 0.10f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.SystemUpdate,
                contentDescription = null,
                tint = c.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${S.updateNewerTitle} ${release.tagName}",
                    style = MaterialTheme.typography.subtitle2,
                    color = c.primary
                )
                Text(
                    S.updateNewerDesc(release.tagName, currentVersion),
                    style = MaterialTheme.typography.caption,
                    color = c.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 12.dp)) {
            CoveButton(text = S.updateNow, onClick = onDownload)
            Spacer(Modifier.width(8.dp))
            CoveOutlinedButton(text = S.updateLater, onClick = onDismiss)
        }
    }
}

