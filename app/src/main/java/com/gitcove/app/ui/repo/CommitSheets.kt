package com.gitcove.app.ui.repo

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gitcove.app.domain.model.Commit
import com.gitcove.app.domain.model.ResetMode
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.components.CoveAlertDialog
import com.gitcove.app.ui.components.CoveBottomSheet
import com.gitcove.app.ui.components.CoveDivider
import com.gitcove.app.ui.components.CoveListRow
import com.gitcove.app.ui.components.CoveTextField
import com.gitcove.app.ui.theme.CoveTheme
import com.gitcove.app.ui.theme.MonoFont
import com.gitcove.app.util.fullTime
import com.gitcove.app.ui.nav.Routes
import kotlinx.coroutines.launch

/**
 * 提交历史抽屉组件（历史 Tab 与提交图谱页共用，4.5 / 4.4）：
 * - [CommitDetailSheet]：点按提交 → 详情（变更文件可点入差异页 / 建分支 / 打标签）
 * - [CommitActionsSheet]：长按 / 双击 → 操作菜单（深度操作 + 复制、邮件、日历、分享补丁）
 *
 * 两处入口此前各自维护一份几乎相同的实现，历史深度操作（cherry-pick / 重置 /
 * 删除提交 / 改写消息 / 打标签）只写在本文件，两个入口同时受益。
 */

/** 长按 / 双击操作菜单（4.5：复制、邮件、日历、分享补丁；4.4：深度操作） */
@Composable
fun CommitActionsSheet(vm: RepoViewModel, commit: Commit, onDismiss: () -> Unit) {
    val S = LocalStrings.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var confirmCherry by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var confirmHardReset by remember { mutableStateOf(false) }

    fun copy(text: String) {
        clipboard.setText(AnnotatedString(text))
        vm.toast(S.copiedToClipboard)
        onDismiss()
    }

    CoveBottomSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                commit.message,
                style = MaterialTheme.typography.subtitle2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
            // ── 深度操作（历史重写）──
            Text(
                S.deepOpsSection,
                style = MaterialTheme.typography.caption,
                color = CoveTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
            CoveListRow(
                S.cherryPickBtn,
                subtitle = "${commit.shortHash} · ${commit.author}",
                icon = { Icon(Icons.Filled.PostAdd, contentDescription = null, modifier = Modifier.size(20.dp)) },
                onClick = { confirmCherry = true }
            )
            CoveListRow(
                S.resetHereBtn,
                subtitle = commit.shortHash,
                icon = { Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(20.dp)) },
                onClick = { showResetDialog = true }
            )
            // ── 快捷操作 ──
            Text(
                S.quickActionsSection,
                style = MaterialTheme.typography.caption,
                color = CoveTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
            CoveListRow(S.copyHash, onClick = { copy(commit.hash) })
            CoveListRow(
                S.copyMessage,
                onClick = { copy(commit.fullMessage.ifBlank { commit.message }) }
            )
            CoveListRow(S.copyEmail, subtitle = commit.email, onClick = { copy(commit.email) })
            CoveListRow(S.emailAuthor, onClick = {
                val intent = Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("mailto:${commit.email}") }
                runCatching { context.startActivity(intent) }
                    .onFailure { vm.toast(S.noMailApp) }
                onDismiss()
            })
            CoveListRow(S.viewInCalendar, subtitle = fullTime(commit.date), onClick = {
                val uri = CalendarContract.CONTENT_URI
                    .buildUpon().appendPath("time").appendPath(commit.date.toString()).build()
                val intent = Intent(Intent.ACTION_VIEW).apply { data = uri }
                runCatching { context.startActivity(intent) }
                    .onFailure { vm.toast(S.noCalendarApp) }
                onDismiss()
            })
            CoveListRow(S.sharePatch, onClick = {
                scope.launch {
                    val patch = vm.commitPatch(commit.hash)
                    if (patch.isNullOrBlank()) {
                        vm.toast(S.noPatch)
                    } else {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, commit.message)
                            putExtra(Intent.EXTRA_TEXT, patch)
                        }
                        runCatching { context.startActivity(Intent.createChooser(send, S.sharePatch)) }
                            .onFailure { vm.toast(S.noPatch) }
                    }
                    onDismiss()
                }
            })
            Spacer(Modifier.height(12.dp))
        }
    }

    if (confirmCherry) {
        CoveAlertDialog(
            onDismiss = { confirmCherry = false },
            title = S.cherryPickBtn,
            content = { Text(S.cherryPickConfirm(commit.shortHash), style = MaterialTheme.typography.body2) },
            confirmLabel = S.confirm,
            onConfirm = { confirmCherry = false; vm.cherryPick(commit.hash); onDismiss() },
            dismissLabel = S.cancel
        )
    }

    if (showResetDialog) {
        var mode by remember { mutableStateOf(ResetMode.MIXED) }
        CoveAlertDialog(
            onDismiss = { showResetDialog = false },
            title = S.resetModeTitle,
            confirmLabel = S.resetAction,
            danger = mode == ResetMode.HARD,
            onConfirm = {
                showResetDialog = false
                if (mode == ResetMode.HARD) confirmHardReset = true
                else { vm.resetTo(commit.hash, mode); onDismiss() }
            },
            dismissLabel = S.cancel,
            content = {
                Column {
                    ResetModeRow(S.resetSoft, S.resetSoftDesc, mode == ResetMode.SOFT) { mode = ResetMode.SOFT }
                    ResetModeRow(S.resetMixed, S.resetMixedDesc, mode == ResetMode.MIXED) { mode = ResetMode.MIXED }
                    ResetModeRow(S.resetHard, S.resetHardDesc, mode == ResetMode.HARD, danger = true) { mode = ResetMode.HARD }
                }
            }
        )
    }
    if (confirmHardReset) {
        CoveAlertDialog(
            onDismiss = { confirmHardReset = false },
            title = S.resetHardConfirmTitle,
            content = { Text(S.resetHardWarning, style = MaterialTheme.typography.body2) },
            confirmLabel = S.resetAction,
            danger = true,
            onConfirm = { confirmHardReset = false; vm.resetTo(commit.hash, ResetMode.HARD); onDismiss() },
            dismissLabel = S.cancel
        )
    }
}

/** 重置模式单选行 */
@Composable
private fun ResetModeRow(
    title: String,
    desc: String,
    selected: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(
                title,
                style = MaterialTheme.typography.body2,
                color = if (danger) CoveTheme.colors.error else CoveTheme.colors.onSurface
            )
            Text(
                desc,
                style = MaterialTheme.typography.caption,
                color = CoveTheme.colors.onSurfaceVariant
            )
        }
    }
}

/** 提交详情底部抽屉（变更文件可点入差异页 / Revert / 签出 / 建分支） */
@Composable
fun CommitDetailSheet(
    vm: RepoViewModel,
    nav: androidx.navigation.NavHostController,
    commit: Commit,
    onDismiss: () -> Unit
) {
    val S = LocalStrings.current
    var branchName by remember { mutableStateOf("") }
    var confirmRevert by remember { mutableStateOf(false) }
    var confirmCheckout by remember { mutableStateOf(false) }

    // 加载变更文件
    LaunchedEffect(commit.hash) { vm.loadCommitDetail(commit) }

    CoveBottomSheet(onDismiss = { vm.clearCommitDetail(); onDismiss() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // 超长提交信息限行：抽屉不整体滚动，无限高的文本会把按钮顶出屏幕
            Text(
                commit.message,
                style = MaterialTheme.typography.subtitle1,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${commit.shortHash} · ${commit.author} <${commit.email}>",
                style = MaterialTheme.typography.overline,
                fontFamily = MonoFont,
                color = CoveTheme.colors.primary
            )
            Text(
                fullTime(commit.date),
                style = MaterialTheme.typography.overline,
                color = CoveTheme.colors.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            CoveDivider()
            Text(
                S.changedFiles(vm.lastCommitDetail?.second?.size ?: 0),
                style = MaterialTheme.typography.button,
                color = CoveTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                S.tapFileDiffHint,
                style = MaterialTheme.typography.caption,
                color = CoveTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            val files = vm.lastCommitDetail?.second.orEmpty()
            LazyColumn(Modifier.height(if (files.isEmpty()) 40.dp else 180.dp)) {
                items(files) { f ->
                    Text(
                        "[${f.changeType.take(1)}] ${f.path}",
                        style = MaterialTheme.typography.caption,
                        fontFamily = MonoFont,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = CoveTheme.colors.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.clearCommitDetail()
                                onDismiss()
                                nav.navigate(Routes.diff(vm.repoId, f.path, cached = false, rev = commit.hash))
                            }
                            .padding(vertical = 4.dp)
                    )
                }
            }
            CoveDivider()
            Row(Modifier.padding(vertical = 8.dp)) {
                TextButton(onClick = { confirmRevert = true }) { Text(S.revertBtn) }
                TextButton(onClick = { confirmCheckout = true }) { Text(S.checkoutVersion) }
            }
            Row(Modifier.padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                CoveTextField(
                    value = branchName,
                    onValueChange = { branchName = it },
                    label = S.branchOnCommit,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        vm.createBranch(branchName, commit.hash)
                        onDismiss()
                    },
                    enabled = branchName.isNotBlank()
                ) { Text(S.create) }
            }
        }
    }

    if (confirmRevert) {
        CoveAlertDialog(
            onDismiss = { confirmRevert = false },
            title = S.revertTitle,
            content = { Text(S.revertConfirm(commit.shortHash), style = MaterialTheme.typography.body2) },
            confirmLabel = S.revertAction,
            onConfirm = { confirmRevert = false; vm.revert(commit.hash); onDismiss() },
            dismissLabel = S.cancel
        )
    }
    if (confirmCheckout) {
        CoveAlertDialog(
            onDismiss = { confirmCheckout = false },
            title = S.checkoutOldTitle,
            content = { Text(S.checkoutConfirmText, style = MaterialTheme.typography.body2) },
            confirmLabel = S.checkout,
            onConfirm = { confirmCheckout = false; vm.checkoutDetached(commit.hash); onDismiss() },
            dismissLabel = S.cancel
        )
    }
}
