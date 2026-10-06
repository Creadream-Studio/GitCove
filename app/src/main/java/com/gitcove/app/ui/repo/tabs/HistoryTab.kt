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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gitcove.app.domain.model.Commit
import com.gitcove.app.ui.components.EmptyView
import com.gitcove.app.ui.repo.RepoViewModel
import com.gitcove.app.ui.theme.MonoFont
import com.gitcove.app.util.relativeTime

/**
 * 历史 Tab（功能 29/34）：提交列表 + 图形化节点连线 + 提交详情（变更文件 / Revert / 建分支 / 签出）
 */
@Composable
fun HistoryTab(vm: RepoViewModel, nav: androidx.navigation.NavHostController) {
    var detailTarget by remember { mutableStateOf<Commit?>(null) }

    if (vm.commits.isEmpty()) {
        EmptyView("还没有提交记录\n在「改动」页完成第一次提交吧")
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
                // 提交图节点 + 连线（简化单泳道）
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(nodeColor(commit.hash), CircleShape)
                    )
                    if (commit != vm.commits.last()) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(34.dp)
                                .background(MaterialTheme.colorScheme.outline)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        commit.message,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row {
                        Text(
                            commit.shortHash,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = MonoFont,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${commit.author} · ${relativeTime(commit.date)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/** 由哈希生成稳定节点色 */
private fun nodeColor(hash: String) = androidx.compose.ui.graphics.Color(
    red = 0x30 + (hash.take(2).toInt(16) % 0x80),
    green = 0xA0 + (hash.drop(2).take(2).toInt(16) % 0x50),
    blue = 0x90 + (hash.drop(4).take(2).toInt(16) % 0x60),
    alpha = 0xFF
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommitDetailSheet(vm: RepoViewModel, commit: Commit, onDismiss: () -> Unit) {
    var branchName by remember { mutableStateOf("") }
    var confirmRevert by remember { mutableStateOf(false) }
    var confirmCheckout by remember { mutableStateOf(false) }

    // 加载变更文件
    androidx.compose.runtime.LaunchedEffect(commit.hash) { vm.loadCommitDetail(commit) }

    ModalBottomSheet(onDismissRequest = { vm.clearCommitDetail(); onDismiss() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(commit.message, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "${commit.shortHash} · ${commit.author} <${commit.email}>",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = MonoFont,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                com.gitcove.app.util.fullTime(commit.date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Text(
                "变更文件 (${vm.lastCommitDetail?.second?.size ?: 0})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp)
            )
            val files = vm.lastCommitDetail?.second.orEmpty()
            LazyColumn(Modifier.height(if (files.isEmpty()) 40.dp else 180.dp)) {
                items(files) { f ->
                    Text(
                        "[${f.changeType.take(1)}] ${f.path}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = MonoFont,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
            HorizontalDivider()
            Row(Modifier.padding(vertical = 8.dp)) {
                TextButton(onClick = { confirmRevert = true }) { Text("Revert 撤销") }
                TextButton(onClick = { confirmCheckout = true }) { Text("签出此版本") }
            }
            Row(Modifier.padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = branchName,
                    onValueChange = { branchName = it },
                    label = { Text("在此提交上创建分支") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        vm.createBranch(branchName, commit.hash)
                        onDismiss()
                    },
                    enabled = branchName.isNotBlank()
                ) { Text("创建") }
            }
        }
    }

    if (confirmRevert) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRevert = false },
            title = { Text("Revert 撤销提交") },
            text = { Text("将创建一个反向提交来撤销 ${commit.shortHash}，原历史保留。继续吗？") },
            confirmButton = {
                TextButton(onClick = { confirmRevert = false; vm.revert(commit.hash); onDismiss() }) { Text("撤销") }
            },
            dismissButton = { TextButton(onClick = { confirmRevert = false }) { Text("取消") } }
        )
    }
    if (confirmCheckout) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmCheckout = false },
            title = { Text("签出旧版本") },
            text = { Text("将进入分离头指针状态（detached HEAD），可以随时切回分支。继续吗？") },
            confirmButton = {
                TextButton(onClick = { confirmCheckout = false; vm.checkoutDetached(commit.hash); onDismiss() }) { Text("签出") }
            },
            dismissButton = { TextButton(onClick = { confirmCheckout = false }) { Text("取消") } }
        )
    }
}
