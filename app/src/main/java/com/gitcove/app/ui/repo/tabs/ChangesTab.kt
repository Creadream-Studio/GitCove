package com.gitcove.app.ui.repo.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.gitcove.app.ui.components.CoveChip
import com.gitcove.app.ui.components.CoveDivider
import com.gitcove.app.ui.components.CoveListRow
import com.gitcove.app.ui.components.CoveOutlinedButton
import com.gitcove.app.ui.components.CoveTextField
import com.gitcove.app.ui.theme.CoveTheme
import androidx.compose.material.icons.filled.CheckCircle

/**
 * 改动 Tab（文档 5.3）：暂存/取消暂存、冲突区、提交信息历史、Amend、提交并推送、Stash
 *
 * 勾选即暂存：勾选某个文件 = 立即暂存，取消勾选 = 立即取消暂存；
 * 区块头的全选框同理作用于整组文件，无额外按钮。
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
    // 待确认删除的储藏下标（删除不可恢复，与文件删除同级别需要确认）
    var dropStashIdx by remember { mutableStateOf<Int?>(null) }

    // ── 提交身份选择：默认跟随默认身份，可临时切换本次提交用哪套身份 ──
    // rememberSaveable：切 Tab/转屏不丢；退出仓库页重置回默认，不动设置里的默认身份
    var commitIdentityId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickIdentity by remember { mutableStateOf(false) }
    val identities = vm.auth.identities()
    val storeDefault = vm.auth.defaultIdentity()
    // 实际生效身份：显式选择优先，找不到（已删除）或未选则回退默认
    val chosenIdentity = identities.firstOrNull { it.id == commitIdentityId } ?: storeDefault

    // ── 冲突全部解决后自动带入合并提交信息（MERGE_MSG，用户可修改）──
    // 触发条件：冲突清零 + 存在挂起合并信息 + 输入框为空（不打扰已输入内容）；
    // 只回填一次（mergePrefilled），用户清空重写不会被再次覆盖
    var mergePrefilled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(vm.status?.conflicts?.size, vm.pendingMergeMsg) {
        val mergeMsg = vm.pendingMergeMsg
        if (!mergePrefilled && mergeMsg != null &&
            vm.status?.conflicts.isNullOrEmpty() && message.isBlank()
        ) {
            message = mergeMsg
            mergePrefilled = true
        }
    }

    val st = vm.status
    val conflicts = st?.conflicts.orEmpty()
    val staged = st?.staged.orEmpty()
    val unstaged = st?.unstaged.orEmpty()

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
                        TextButton(onClick = { nav.navigate(Routes.resolve(vm.repoId, path)) }) {
                            Text(S.resolve)
                        }
                    }
                    CoveDivider()
                }
            }

            // ── 已暂存：勾选态表示在暂存区，取消勾选立即取消暂存；区块头全选框 = 全部取消暂存 ──
            if (staged.isNotEmpty()) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(CoveTheme.colors.surfaceVariant)
                            .padding(start = 2.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = true,
                            onCheckedChange = { on ->
                                if (!on && !vm.busy) vm.unstage(staged.map { it.path })
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
                    }
                }
                items(staged, key = { "s_" + it.path }) { f ->
                    ChangeRow(
                        file = f,
                        checked = true,
                        onCheckedChange = { on ->
                            if (!on && !vm.busy) vm.unstage(listOf(f.path))
                        },
                        onClick = { nav.navigate(Routes.diff(vm.repoId, f.path, cached = true)) }
                    )
                    CoveDivider()
                }
            }

            // ── 未暂存：勾选立即暂存；区块头全选框 = 全部暂存 ──
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
                            checked = false,
                            onCheckedChange = { on ->
                                if (on && !vm.busy) vm.stage(unstaged.map { it.path })
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
                    }
                }
                items(unstaged, key = { "u_" + it.path }) { f ->
                    ChangeRow(
                        file = f,
                        checked = false,
                        onCheckedChange = { on ->
                            if (on && !vm.busy) vm.stage(listOf(f.path))
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
            // Conventional Commits 类型推荐：输入字母即时过滤（如 f → feat/fix），
            // 点击套用「type: 」前缀；已有前缀再点其它类型则替换，正文不丢
            val typed = message.trimStart()
            val recTypes = if (typed.isBlank() || typed.contains(':') || typed.contains(' ')) {
                CC_TYPES
            } else {
                CC_TYPES.filter { it.startsWith(typed, ignoreCase = true) }.ifEmpty { CC_TYPES }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
            ) {
                recTypes.forEach { t ->
                    CoveChip(
                        label = t,
                        selected = CC_PREFIX_RE.find(message)?.value?.trimEnd(':', ' ')?.startsWith(t, ignoreCase = true) == true,
                        onClick = { message = applyCcType(t, message) }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            CoveTextField(
                value = message,
                onValueChange = { message = it },
                placeholder = S.commitMsgPlaceholder,
                minLines = 2,
                // 星星：AI 生成提交信息（功能 4.6）。
                // 有暂存才可点（无暂存禁用）；生成中转圈防重复；未配置时点击提示去设置。
                leadingIcon = {
                    IconButton(
                        onClick = { vm.generateAiCommitMessage { message = it } },
                        enabled = staged.isNotEmpty() && !vm.aiGenerating
                    ) {
                        if (vm.aiGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = S.aiGenerate,
                                tint = if (staged.isNotEmpty()) CoveTheme.colors.primary
                                else CoveTheme.colors.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
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
            // ── 提交身份行：输入框下方，显示当前将使用的身份，点击切换 ──
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (identities.isEmpty()) nav.navigate(com.gitcove.app.ui.nav.Routes.SETTINGS_GIT)
                        else pickIdentity = true
                    }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = S.commitAsLabel,
                    tint = if (chosenIdentity != null) CoveTheme.colors.primary else CoveTheme.colors.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    if (chosenIdentity != null) {
                        Text(
                            "${S.commitAsLabel}：${chosenIdentity.displayName}" +
                                if (chosenIdentity.id == storeDefault?.id) " · ${S.defaultBadge}" else "",
                            style = MaterialTheme.typography.caption,
                            color = CoveTheme.colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${chosenIdentity.name} <${chosenIdentity.email}>",
                            style = MaterialTheme.typography.overline,
                            color = CoveTheme.colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            S.noIdentityYet,
                            style = MaterialTheme.typography.caption,
                            color = CoveTheme.colors.error
                        )
                    }
                }
                if (identities.isNotEmpty()) {
                    Text(
                        S.identityHint,
                        style = MaterialTheme.typography.overline,
                        color = CoveTheme.colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1.2f, fill = false)
                    )
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = CoveTheme.colors.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = amend,
                    onCheckedChange = { on ->
                        // git commit --amend 惯例：勾选即带出上次提交信息（仅输入为空时，不覆盖用户输入）
                        if (on) {
                            if (message.isBlank()) message = vm.commits.firstOrNull()?.message ?: ""
                        } else if (vm.commits.firstOrNull()?.message == message) {
                            // 取消勾选时若内容仍是自动回填那条，一并清空防误用旧信息
                            message = ""
                        }
                        amend = on
                    },
                    colors = CheckboxDefaults.colors(checkedColor = CoveTheme.colors.primary)
                )
                Text(S.amend, style = MaterialTheme.typography.caption)
                Spacer(Modifier.weight(1f))
                // 手动刷新：重新读取工作区状态（外部改动也能拉进来）
                IconButton(onClick = { vm.refreshAll() }, enabled = !vm.busy) {
                    Icon(Icons.Filled.Refresh, contentDescription = S.refresh, modifier = Modifier.size(18.dp))
                }
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
                    text = if (vm.committing) S.committing else S.commit,
                    onClick = {
                        // 成功才清空输入：失败（如未配置身份）时保留信息，改完直接重试
                        vm.commit(message, amend, pushAfter = false, identityId = commitIdentityId) { message = ""; amend = false }
                    },
                    enabled = staged.isNotEmpty() && message.isNotBlank() && !vm.busy,
                    modifier = Modifier.weight(1f)
                )
                CoveOutlinedButton(
                    text = if (vm.committing) S.committing else S.commitAndPush,
                    onClick = {
                        vm.commit(message, amend, pushAfter = true, identityId = commitIdentityId) { message = ""; amend = false }
                    },
                    // st?.detached != true：status 尚未加载时按可推送处理（detached 才禁用），
                    // 原写法 st?.detached!! 在 status 为 null 的首帧会 NPE
                    enabled = staged.isNotEmpty() && message.isNotBlank() && !vm.busy && st?.detached != true,
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
                            TextButton(onClick = { dropStashIdx = idx }) { Text(S.delete) }
                        }
                    }
                }
            },
            confirmLabel = S.close,
            onConfirm = { stashListDialog = false }
        )
    }

    // 储藏删除确认（不可恢复）
    if (dropStashIdx != null) {
        val idx = dropStashIdx!!
        ConfirmDialog(
            title = S.delete,
            text = S.dropStashConfirm,
            confirmLabel = S.delete,
            danger = true,
            onConfirm = {
                dropStashIdx = null
                vm.stashDrop(idx)
            },
            onDismiss = { dropStashIdx = null }
        )
    }

    // ── 提交身份选择：默认显示默认身份，点选切换本次提交身份 ──
    if (pickIdentity) {
        CoveAlertDialog(
            onDismiss = { pickIdentity = false },
            title = S.identityPickerTitle,
            confirmLabel = S.close,
            onConfirm = { pickIdentity = false },
            content = {
                // 注：不要在此处再包 verticalScroll——CoveAlertDialog 内容区自带滚动，
                // 同向嵌套滚动会在测量阶段直接抛 IllegalStateException（历史闪退根因）
                Column(Modifier.fillMaxWidth()) {
                    identities.forEach { idt ->
                        CoveListRow(
                            title = idt.displayName +
                                if (idt.id == storeDefault?.id) " · ${S.defaultBadge}" else "",
                            subtitle = "${idt.name} <${idt.email}>",
                            icon = {
                                Icon(
                                    Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = CoveTheme.colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailing = {
                                if (idt.id == chosenIdentity?.id) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = CoveTheme.colors.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            onClick = {
                                commitIdentityId = idt.id
                                pickIdentity = false
                            }
                        )
                    }
                }
            }
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

// ─────────────── Conventional Commits 类型推荐 ───────────────

/** 常用提交类型（Conventional Commits 规范） */
private val CC_TYPES = listOf(
    "feat", "fix", "docs", "style", "refactor", "perf", "test", "build", "ci", "chore", "revert"
)

/** 匹配消息开头的类型前缀：type: / type(scope): / type!: */
private val CC_PREFIX_RE = Regex("^[A-Za-z]+(?:\\([^)]*\\))?!?:\\s*")

/** 套用提交类型：已有前缀则替换（正文保留），否则在开头新建前缀 */
private fun applyCcType(type: String, msg: String): String {
    val body = if (CC_PREFIX_RE.containsMatchIn(msg)) msg.replace(CC_PREFIX_RE, "") else msg
    return if (body.isBlank()) "$type: " else "$type: $body"
}
