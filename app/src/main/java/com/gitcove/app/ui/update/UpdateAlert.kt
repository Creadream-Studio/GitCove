package com.gitcove.app.ui.update

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.gitcove.app.BuildConfig
import com.gitcove.app.data.update.Release
import com.gitcove.app.data.update.UpdateState
import com.gitcove.app.data.update.openRelease
import com.gitcove.app.di.AppContainer
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.components.CoveAlertDialog

/**
 * 版本更新弹窗：当前屏幕顶层显示，仅发现全新正式版时出现。
 *
 * 由会话级的 UpdateChecker.state 驱动，在 [com.gitcove.app.MainActivity] 中
 * 与导航层同时存在，弹窗不会抢占/打断页面的正常滚动。
 * 关掉弹窗 → [UpdateChecker.dismiss] 置位，本轮不再重复弹出。
 */
@Composable
fun UpdateAlert(container: AppContainer) {
    val S = LocalStrings.current
    val state by container.updateChecker.state.collectAsState()
    val context = LocalContext.current

    val release = (state as? UpdateState.Available)?.release
    if (release != null) {
        CoveAlertDialog(
            onDismiss = { container.updateChecker.dismiss() },
            title = "${S.updateNewerTitle} ${release.tagName}",
            content = {
                Text(
                    S.updateNewerDesc(release.tagName, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.body2
                )
            },
            confirmLabel = S.updateNow,
            onConfirm = {
                openRelease(context, release.htmlUrl)
                container.updateChecker.dismiss()
            },
            dismissLabel = S.updateLater,
            onDismissClick = { container.updateChecker.dismiss() }
        )
    }
}
