package com.gitcove.app.ui.repo.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gitcove.app.domain.model.Commit
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.components.Avatar
import com.gitcove.app.ui.components.EmptyView
import com.gitcove.app.ui.repo.RepoViewModel
import com.gitcove.app.ui.theme.MonoFont
import com.gitcove.app.util.relativeTime
import com.gitcove.app.ui.components.CoveAlertDialog
import com.gitcove.app.ui.components.CoveBottomSheet
import com.gitcove.app.ui.components.CoveDivider
import com.gitcove.app.ui.components.CoveTextField
import com.gitcove.app.ui.theme.CoveTheme
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.Icons

/**
 * 历史 Tab（功能 29/34）：提交列表 + 图形化节点连线 + 提交详情（变更文件 / Revert / 建分支 / 签出）
 */
@Composable
fun HistoryTab(vm: RepoViewModel, nav: androidx.navigation.NavHostController) {
    val S = LocalStrings.current
    var detailTarget by remember { mutableStateOf<Commit?>(null) }
    // GitHub 账号摘要（登录后作者头像可精准命中）
    var account by remember { mutableStateOf(vm.auth.githubAccount) }
    LaunchedEffect(Unit) {
        vm.refreshGithubAccount()
        account = vm.auth.githubAccount
    }

    if (vm.commits.isEmpty()) {
        EmptyView(S.noCommits, icon = Icons.Filled.History)
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(vm.commits, key = { it.hash }) { commit ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { detailTarget = commit }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 作者头像 + 连线（替代原提交节点圆点）
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(
                        email = commit.email,
                        name = commit.author,
                        size = 26.dp,
                        account = account
                    )
                    if (commit != vm.commits.last()) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(30.dp)
                                .background(CoveTheme.colors.outline)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        commit.message,
                        style = MaterialTheme.typography.subtitle2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row {
                        Text(
                            commit.shortHash,
                            style = MaterialTheme.typography.overline,
                            fontFamily = MonoFont,
                            color = CoveTheme.colors.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${commit.author} · ${relativeTime(commit.date, S)}",
                            style = MaterialTheme.typography.overline,
                            color = CoveTheme.colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    detailTarget?.let { target ->
        CommitDetailSheet(
            vm = vm,
            commit = target,
            onDismiss = { detailTarget = null }
        )
    }
}

/** 提交详情底部抽屉 */
@Composable
private fun CommitDetailSheet(vm: RepoViewModel, commit: Commit, onDismiss: () -> Unit) {
    val S = LocalStrings.current
    var branchName by remember { mutableStateOf("") }
    var confirmRevert by remember { mutableStateOf(false) }
    var confirmCheckout by remember { mutableStateOf(false) }

    // 加载变更文件
    androidx.compose.runtime.LaunchedEffect(commit.hash) { vm.loadCommitDetail(commit) }

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
                com.gitcove.app.util.fullTime(commit.date),
                style = MaterialTheme.typography.overline,
                color = CoveTheme.colors.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            CoveDivider()
            Text(
                S.changedFiles(vm.lastCommitDetail?.second?.size ?: 0),
                style = MaterialTheme.typography.button,
                color = CoveTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp)
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
                        modifier = Modifier.padding(vertical = 2.dp)
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
