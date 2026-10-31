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
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
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
import com.gitcove.app.ui.components.CoveAlertDialog
import com.gitcove.app.ui.components.CoveButton
import com.gitcove.app.ui.components.CoveDivider
import com.gitcove.app.ui.components.CoveOutlinedButton
import com.gitcove.app.ui.components.CoveTextField
import com.gitcove.app.ui.theme.CoveTheme
import androidx.compose.material.icons.filled.CheckCircle

/**
 * 改动 Tab（文档 5.3）：暂存/取消暂存、冲突区、提交信息历史、Amend、提交并推送、Stash
 *
 * 暂存/取消暂存全部通过复选框完成：每个文件行前有复选框，区块头有全选，
 * 选中后点区块头的批量按钮执行；另提供手动刷新按钮。
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

    // ── 未暂存文件多选：复选框 + 全选 + 批量暂存 ──
    var selectedUnstaged by remember { mutableStateOf(setOf<String>()) }
    val unstagedPaths = remember(unstaged) { unstaged.map { it.path }.toSet() }
    // 只保留仍未暂存的路径，文件被暂存后旧选择自动失效
    val selectedInUnstaged = selectedUnstaged intersect unstagedPaths
    val allUnstagedSelected = unstaged.isNotEmpty() && selectedInUnstaged.size == unstaged.size

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
                            color = CoveTheme.colors.error,
                            modifier = Modifier.width(24.dp)
                        )
                        Text(
                            path,
                            style = MaterialTheme.typography.body2,
                            fontFamily = MonoFont,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { nav.navigate(Routes.diff(vm.repoId, path, cached = false)) }) {
                            Text(S.resolve)
                        }
                    }
                    CoveDivider()
                }
            }

            // ── 已暂存 ──
            if (staged.isNotEmpty()) {
                item {
                    // 区块头：全选复选框 + 标题 + 批量取消暂存按钮
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(CoveTheme.colors.surfaceVariant)
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
                            style = MaterialTheme.typography.button,
                            color = CoveTheme.colors.primary,
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
                        onClick = { nav.navigate(Routes.diff(vm.repoId, f.path, cached = true)) }
                    )
                    CoveDivider()
                }
            }

            // ── 未暂存：区块头全选 + 复选框多选，勾选后批量暂存 ──
            if (unstaged.isNotEmpty()) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(CoveTheme.colors.surfaceVariant)
                            .padding(start = 2.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = allUnstagedSelected,
                            onCheckedChange = { on ->
                                selectedUnstaged = if (on) unstagedPaths else emptySet()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .semantics { contentDescription = S.selectAll }
                        )
                        Text(
                            S.unstagedSection(unstaged.size),
                            style = MaterialTheme.typography.button,
                            color = CoveTheme.colors.primary,
                            modifier = Modifier.weight(1f)
                        )
                        if (selectedInUnstaged.isNotEmpty()) {
                            TextButton(onClick = {
                                val targets = selectedInUnstaged.toList()
                                selectedUnstaged = emptySet()
                                vm.stage(targets)
                            }) { Text(S.stageSelected(selectedInUnstaged.size)) }
                        }
                    }
                }
                items(unstaged, key = { "u_" + it.path }) { f ->
                    ChangeRow(
                        file = f,
                        checked = f.path in selectedInUnstaged,
                        onCheckedChange = { on ->
                            selectedUnstaged = if (on) selectedInUnstaged + f.path else selectedInUnstaged - f.path
                        },
                        onClick = { nav.navigate(Routes.diff(vm.repoId, f.path, cached = false)) }
                    )
                    CoveDivider()
                }
            }

            if (staged.isEmpty() && unstaged.isEmpty() && conflicts.isEmpty()) {
                item {
                    EmptyView(
                        S.cleanTreeEmpty,
                        icon = Icons.Filled.CheckCircle
                    )
                }
            }
        }

        CoveDivider()

        // ── 提交区 ──
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            CoveTextField(
                value = message,
                onValueChange = { message = it },
                placeholder = S.commitMsgPlaceholder,
                minLines = 2,
                trailingIcon = {
                    Column {
                        IconButton(onClick = { historyMenu = true }, enabled = vm.recentMsgs.isNotEmpty()) {
                            Icon(Icons.Filled.History, contentDescription = S.msgHistoryRef, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = historyMenu, onDismissRequest = { historyMenu = false }) {
                            Text(
                                S.msgHistoryTitle,
                                style = MaterialTheme.typography.overline,
                                color = CoveTheme.colors.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                            vm.recentMsgs.forEach { m ->
                                DropdownMenuItem(onClick = { message = m; historyMenu = false }) {
                                    Text(m, style = MaterialTheme.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
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
                    colors = CheckboxDefaults.colors(checkedColor = CoveTheme.colors.primary)
                )
                Text(S.amend, style = MaterialTheme.typography.caption)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { overflow = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = S.moreActions, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                    DropdownMenuItem(onClick = { overflow = false; discardDialog = true }) {
                        Text(S.discardUnstagedMenu, style = MaterialTheme.typography.body2, color = CoveTheme.colors.error)
                    }
                    DropdownMenuItem(onClick = { overflow = false; vm.stash() }) {
                        Text(S.stashAll, style = MaterialTheme.typography.body2)
                    }
                    DropdownMenuItem(onClick = { overflow = false; stashListDialog = true }) {
                        Text(S.viewStashes, style = MaterialTheme.typography.body2)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                CoveButton(
                    text = S.commit,
                    onClick = {
                        vm.commit(message, amend, pushAfter = false)
                        message = ""
                        amend = false
                    },
                    enabled = staged.isNotEmpty() && message.isNotBlank() && !vm.busy,
                    modifier = Modifier.weight(1f)
                )
                CoveOutlinedButton(
                    text = S.commitAndPush,
                    onClick = {
                        vm.commit(message, amend, pushAfter = true)
                        message = ""
                        amend = false
                    },
                    enabled = staged.isNotEmpty() && message.isNotBlank() && !vm.busy && !st?.detached!!,
                    modifier = Modifier.weight(1f)
                )
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
        CoveAlertDialog(
            onDismiss = { stashListDialog = false },
            title = S.stashListTitle,
            content = {
                if (vm.stashes.isEmpty()) Text(S.noStashes)
                else Column {
                    vm.stashes.forEachIndexed { idx, s ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(s.message, style = MaterialTheme.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${s.shortHash} · ${com.gitcove.app.util.relativeTime(s.date, S)}",
                                    style = MaterialTheme.typography.overline,
                                    color = CoveTheme.colors.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { vm.stashApply(idx); stashListDialog = false }) { Text(S.apply) }
                            TextButton(onClick = { vm.stashDrop(idx) }) { Text(S.delete) }
                        }
                    }
                }
            },
            confirmLabel = S.close,
            onConfirm = { stashListDialog = false }
        )
    }
}

@Composable
private fun ChangeRow(
    file: FileStatus,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
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
            style = MaterialTheme.typography.body2,
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
