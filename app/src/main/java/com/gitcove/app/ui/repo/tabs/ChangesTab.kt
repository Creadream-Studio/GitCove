package com.gitcove.app.ui.repo.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gitcove.app.domain.model.FileStatus
import com.gitcove.app.ui.components.ConfirmDialog
import com.gitcove.app.ui.components.EmptyView
import com.gitcove.app.ui.components.SectionHeader
import com.gitcove.app.ui.components.StatusLetterBadge
import com.gitcove.app.ui.nav.Routes
import com.gitcove.app.ui.repo.RepoViewModel
import com.gitcove.app.ui.theme.MonoFont

/**
 * 改动 Tab（文档 5.3）：暂存/取消暂存、冲突区、提交信息历史、Amend、提交并推送、Stash
 */
@Composable
fun ChangesTab(vm: RepoViewModel, nav: androidx.navigation.NavHostController) {
    var message by remember { mutableStateOf("") }
    var amend by remember { mutableStateOf(false) }
    var historyMenu by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    var stashListDialog by remember { mutableStateOf(false) }
    var discardDialog by remember { mutableStateOf(false) }

    val st = vm.status
    val conflicts = st?.conflicts.orEmpty()
    val staged = st?.staged.orEmpty()
    val unstaged = st?.unstaged.orEmpty()

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {

            // ── 冲突区（功能 35-38）──
            if (conflicts.isNotEmpty()) {
                item { SectionHeader("冲突 · 需要解决 (${conflicts.size})") }
                items(conflicts) { path ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { nav.navigate(Routes.diff(vm.repoId, path, cached = false)) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "⚔",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.width(24.dp)
                        )
                        Text(
                            path,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = MonoFont,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { nav.navigate(Routes.diff(vm.repoId, path, cached = false)) }) {
                            Text("解决")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }

            // ── 已暂存 ──
            if (staged.isNotEmpty()) {
                item {
                    SectionHeader("已暂存 (${staged.size})")
                }
                items(staged, key = { "s_" + it.path }) { f ->
                    ChangeRow(
                        file = f,
                        onClick = { nav.navigate(Routes.diff(vm.repoId, f.path, cached = true)) },
                        trailing = {
                            TextButton(onClick = { vm.unstage(listOf(f.path)) }) { Text("取消") }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }

            // ── 未暂存 ──
            if (unstaged.isNotEmpty()) {
                item {
                    SectionHeader("未暂存 (${unstaged.size})")
                }
                items(unstaged, key = { "u_" + it.path }) { f ->
                    ChangeRow(
                        file = f,
                        onClick = { nav.navigate(Routes.diff(vm.repoId, f.path, cached = false)) },
                        trailing = {
                            TextButton(onClick = { vm.stage(listOf(f.path)) }) { Text("暂存") }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }

            if (staged.isEmpty() && unstaged.isEmpty() && conflicts.isEmpty()) {
                item { EmptyView("工作区干净\n所有改动均已提交") }
            }
        }

        HorizontalDivider()

        // ── 提交区 ──
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                placeholder = { Text("提交信息…", style = MaterialTheme.typography.bodyMedium) },
                textStyle = MaterialTheme.typography.bodyMedium,
                minLines = 2,
                maxLines = 4,
                trailingIcon = {
                    Column {
                        IconButton(onClick = { historyMenu = true }, enabled = vm.recentMsgs.isNotEmpty()) {
                            Icon(Icons.Filled.History, contentDescription = "历史参考", modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = historyMenu, onDismissRequest = { historyMenu = false }) {
                            Text(
                                "提交信息历史参考",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                            vm.recentMsgs.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    onClick = { message = m; historyMenu = false }
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = amend,
                    onCheckedChange = { amend = it },
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                )
                Text("修改上次提交 (Amend)", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { overflow = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多操作", modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                    DropdownMenuItem(
                        text = { Text("暂存全部改动") },
                        onClick = {
                            overflow = false
                            vm.stage(vm.diffUnstaged.map { it.path } + (st?.unstaged?.map { it.path } ?: emptyList()))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("全部取消暂存") },
                        onClick = {
                            overflow = false
                            vm.unstage(st?.staged?.map { it.path } ?: emptyList())
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("还原未暂存改动…", color = MaterialTheme.colorScheme.error) },
                        onClick = { overflow = false; discardDialog = true }
                    )
                    DropdownMenuItem(
                        text = { Text("储藏 (Stash) 全部改动") },
                        onClick = { overflow = false; vm.stash() }
                    )
                    DropdownMenuItem(
                        text = { Text("查看储藏列表…") },
                        onClick = { overflow = false; stashListDialog = true }
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        vm.commit(message, amend, pushAfter = false)
                        message = ""
                        amend = false
                    },
                    enabled = staged.isNotEmpty() && message.isNotBlank() && !vm.busy,
                    modifier = Modifier.weight(1f)
                ) { Text("提交") }
                OutlinedButton(
                    onClick = {
                        vm.commit(message, amend, pushAfter = true)
                        message = ""
                        amend = false
                    },
                    enabled = staged.isNotEmpty() && message.isNotBlank() && !vm.busy && !st?.detached!!,
                    modifier = Modifier.weight(1f)
                ) { Text("提交并推送") }
            }
        }
    }

    // 丢弃确认
    if (discardDialog) {
        ConfirmDialog(
            title = "还原未暂存改动",
            text = "将丢弃 ${unstaged.size} 个文件的未暂存改动（未跟踪文件将被删除），此操作不可恢复！",
            confirmLabel = "还原",
            danger = true,
            onDismiss = { discardDialog = false },
            onConfirm = {
                discardDialog = false
                vm.discard(st?.unstaged?.map { it.path } ?: emptyList())
            }
        )
    }

    // Stash 列表
    if (stashListDialog) {
        AlertDialog(
            onDismissRequest = { stashListDialog = false },
            title = { Text("储藏列表", style = MaterialTheme.typography.titleMedium) },
            text = {
                if (vm.stashes.isEmpty()) Text("没有储藏记录")
                else Column {
                    vm.stashes.forEachIndexed { idx, s ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(s.message, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${s.shortHash} · ${com.gitcove.app.util.relativeTime(s.date)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { vm.stashApply(idx); stashListDialog = false }) { Text("恢复") }
                            TextButton(onClick = { vm.stashDrop(idx) }) { Text("删除") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { stashListDialog = false }) { Text("关闭") } }
        )
    }
}

@Composable
private fun ChangeRow(
    file: FileStatus,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusLetterBadge(file.status)
        Spacer(Modifier.width(8.dp))
        Text(
            file.path,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = MonoFont,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}
