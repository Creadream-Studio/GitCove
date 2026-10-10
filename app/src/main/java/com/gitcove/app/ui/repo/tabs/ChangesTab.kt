package com.gitcove.app.ui.repo.tabs

import androidx.compose.foundation.background
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gitcove.app.domain.model.FileStatus
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.components.ConfirmDialog
import com.gitcove.app.ui.components.EmptyView
import com.gitcove.app.ui.components.SectionHeader
import com.gitcove.app.ui.components.StatusDot
import com.gitcove.app.ui.components.StatusLetterBadge
import com.gitcove.app.ui.nav.Routes
import com.gitcove.app.ui.repo.RepoViewModel
import com.gitcove.app.ui.theme.MonoFont

/**
 * 改动 Tab（文档 5.3）：暂存/取消暂存、冲突区、提交信息历史、Amend、提交并推送、Stash
 *
 * 已暂存（缓存）文件支持复选框多选：每行前有复选框，区块头有全选，可批量取消暂存。
 */
@Composable
fun ChangesTab(vm: RepoViewModel, nav: androidx.navigation.NavHostController) {
    val S = LocalStrings.current
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

    // ── 已暂存（缓存）文件多选：复选框 + 全选 + 批量取消暂存 ──
    var selectedStaged by remember { mutableStateOf(setOf<String>()) }
    val stagedPaths = remember(staged) { staged.map { it.path }.toSet() }
    // 只保留仍处于已暂存状态的路径，避免暂存区变化后残留旧选择
    val selectedInStaged = selectedStaged intersect stagedPaths
    val allStagedSelected = staged.isNotEmpty() && selectedInStaged.size == staged.size

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {

            // ── 冲突区（功能 35-38）──
            if (conflicts.isNotEmpty()) {
                item { SectionHeader(S.conflictsSection(conflicts.size)) }
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
                            Text(S.resolve)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }

            // ── 已暂存 ──
            if (staged.isNotEmpty()) {
                item {
                    // 区块头：全选复选框 + 标题 + 批量取消暂存按钮
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(start = 2.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = allStagedSelected,
                            onCheckedChange = { on ->
                                selectedStaged = if (on) stagedPaths else emptySet()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .semantics { contentDescription = S.selectAll }
                        )
                        Text(
                            S.stagedSection(staged.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        if (selectedInStaged.isNotEmpty()) {
                            TextButton(onClick = {
                                val targets = selectedInStaged.toList()
                                selectedStaged = emptySet()
                                vm.unstage(targets)
                            }) { Text(S.unstageSelected(selectedInStaged.size)) }
                        }
                    }
                }
                items(staged, key = { "s_" + it.path }) { f ->
                    ChangeRow(
                        file = f,
                        checked = f.path in selectedInStaged,
                        onCheckedChange = { on ->
                            selectedStaged = if (on) selectedInStaged + f.path else selectedInStaged - f.path
                        },
                        onClick = { nav.navigate(Routes.diff(vm.repoId, f.path, cached = true)) },
                        trailing = {
                            TextButton(onClick = {
                                selectedStaged = selectedInStaged - f.path
                                vm.unstage(listOf(f.path))
                            }) { Text(S.unstage) }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }

            // ── 未暂存 ──
            if (unstaged.isNotEmpty()) {
                item {
                    SectionHeader(S.unstagedSection(unstaged.size))
                }
                items(unstaged, key = { "u_" + it.path }) { f ->
                    ChangeRow(
                        file = f,
                        onClick = { nav.navigate(Routes.diff(vm.repoId, f.path, cached = false)) },
                        trailing = {
                            TextButton(onClick = { vm.stage(listOf(f.path)) }) { Text(S.stage) }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }

            if (staged.isEmpty() && unstaged.isEmpty() && conflicts.isEmpty()) {
                item { EmptyView(S.cleanTreeEmpty) }
            }
        }

        HorizontalDivider()

        // ── 提交区 ──
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                placeholder = { Text(S.commitMsgPlaceholder, style = MaterialTheme.typography.bodyMedium) },
                textStyle = MaterialTheme.typography.bodyMedium,
                minLines = 2,
                maxLines = 4,
                trailingIcon = {
                    Column {
                        IconButton(onClick = { historyMenu = true }, enabled = vm.recentMsgs.isNotEmpty()) {
                            Icon(Icons.Filled.History, contentDescription = S.msgHistoryRef, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = historyMenu, onDismissRequest = { historyMenu = false }) {
                            Text(
                                S.msgHistoryTitle,
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
                Text(S.amend, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { overflow = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = S.moreActions, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                    DropdownMenuItem(
                        text = { Text(S.stageAll) },
                        onClick = {
                            overflow = false
                            vm.stage(vm.diffUnstaged.map { it.path } + (st?.unstaged?.map { it.path } ?: emptyList()))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(S.unstageAll) },
                        onClick = {
                            overflow = false
                            vm.unstage(st?.staged?.map { it.path } ?: emptyList())
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(S.discardUnstagedMenu, color = MaterialTheme.colorScheme.error) },
                        onClick = { overflow = false; discardDialog = true }
                    )
                    DropdownMenuItem(
                        text = { Text(S.stashAll) },
                        onClick = { overflow = false; vm.stash() }
                    )
                    DropdownMenuItem(
                        text = { Text(S.viewStashes) },
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
                ) { Text(S.commit) }
                OutlinedButton(
                    onClick = {
                        vm.commit(message, amend, pushAfter = true)
                        message = ""
                        amend = false
                    },
                    enabled = staged.isNotEmpty() && message.isNotBlank() && !vm.busy && !st?.detached!!,
                    modifier = Modifier.weight(1f)
                ) { Text(S.commitAndPush) }
            }
        }
    }

    // 丢弃确认
    if (discardDialog) {
        ConfirmDialog(
            title = S.discardTitle,
            text = S.discardConfirm(unstaged.size),
            confirmLabel = S.discard,
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
            title = { Text(S.stashListTitle, style = MaterialTheme.typography.titleMedium) },
            text = {
                if (vm.stashes.isEmpty()) Text(S.noStashes)
                else Column {
                    vm.stashes.forEachIndexed { idx, s ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(s.message, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${s.shortHash} · ${com.gitcove.app.util.relativeTime(s.date, S)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { vm.stashApply(idx); stashListDialog = false }) { Text(S.apply) }
                            TextButton(onClick = { vm.stashDrop(idx) }) { Text(S.delete) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { stashListDialog = false }) { Text(S.close) } }
        )
    }
}

@Composable
private fun ChangeRow(
    file: FileStatus,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 可选的行首复选框（已暂存/缓存文件多选用）
        if (checked != null && onCheckedChange != null) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(2.dp))
        }
        StatusLetterBadge(file.status)
        Spacer(Modifier.width(8.dp))
        Text(
            file.path,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = MonoFont,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        // 路径后的状态圆点：新增绿 / 修改黄 / 删除红（提交后消失）
        file.status?.let {
            Spacer(Modifier.width(4.dp))
            StatusDot(it)
        }
        Spacer(Modifier.weight(1f))
        trailing()
    }
}
