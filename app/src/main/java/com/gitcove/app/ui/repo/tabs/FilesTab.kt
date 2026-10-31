package com.gitcove.app.ui.repo.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Surface
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitcove.app.domain.model.FileNode
import com.gitcove.app.domain.model.Status
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.i18n.Strings
import com.gitcove.app.ui.components.CoveAlertDialog
import com.gitcove.app.ui.components.CoveBottomSheet
import com.gitcove.app.ui.components.CoveChip
import com.gitcove.app.ui.components.CoveDivider
import com.gitcove.app.ui.components.CoveKeyValueRow
import com.gitcove.app.ui.components.CoveSegmentedRow
import com.gitcove.app.ui.components.CoveTextField
import com.gitcove.app.ui.components.EmptyView
import com.gitcove.app.ui.components.StatusLetterBadge
import com.gitcove.app.ui.nav.Routes
import com.gitcove.app.ui.repo.RepoViewModel
import com.gitcove.app.ui.theme.CoveTheme
import com.gitcove.app.ui.theme.MonoFont
import com.gitcove.app.ui.theme.StatusAddedColor
import com.gitcove.app.ui.theme.StatusConflictColor
import com.gitcove.app.ui.theme.StatusDeletedColor
import com.gitcove.app.ui.theme.StatusModifiedColor
import com.gitcove.app.ui.theme.StatusUntrackedColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文件 Tab（文档 5.3）：列表样式与文件操作移植自开源文件管理器
 * MP Manager（MT 管理器开源衍生版，github.com/AbdurazaaqMohammed/MP-Manager），
 * 按需求改为单列显示：
 *
 * - 行结构沿用其 list_file.xml：30dp 类型图标 + 粗体文件名 + 下方 10sp 灰色副行
 *   （文件＝修改时间 + 大小；文件夹＝修改时间 + 子项数）；子目录顶部保留「..」上级行
 * - 长按弹出其底部抽屉操作菜单（dialog_file_menu：抓握条 + 文件名标题 + 网格卡片）
 *   操作项：编辑器打开 / 粘贴到此处 / 复制 / 剪切 / 重命名 / 删除 / 属性 / 复制路径 / 多选
 * - 多选模式：行背景染 secondaryContainer（同 MP），面包屑栏变「已选 N + 全选/反选」，
 *   底部操作条：复制 / 剪切 / 重命名（单选）/ 删除；剪贴板在面包屑栏出现「粘贴(N)」
 * - Git 状态彩色沿用本应用原有方案：名称按状态着色（新增绿 / 修改黄 / 删除红 /
 *   冲突品红），行尾保留字母徽章（A/M/D/U/C）
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun FilesTab(vm: RepoViewModel, nav: androidx.navigation.NavHostController) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    val clipboard = LocalClipboardManager.current
    var searchOpen by remember { mutableStateOf(false) }
    var keyword by remember { mutableStateOf("") }
    var contentMode by remember { mutableStateOf(false) }
    var createDialog by remember { mutableStateOf(false) }
    var menuTarget by remember { mutableStateOf<FileNode?>(null) }
    var renameTarget by remember { mutableStateOf<FileNode?>(null) }
    var deleteTargets by remember { mutableStateOf<List<FileNode>?>(null) }
    var propsTarget by remember { mutableStateOf<FileNode?>(null) }
    // 多选模式（MP 管理器）：路径集合，行背景染色标识选中
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }

    fun exitSelection() { selectionMode = false; selected = emptySet() }

    Column(Modifier.fillMaxSize()) {

        // ── 顶栏：普通模式=面包屑+粘贴+搜索+新建；多选模式=已选N+全选+反选+退出 ──
        if (!selectionMode) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(c.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val parts = vm.currentPath.split("/").filter { it.isNotBlank() }
                Text(
                    S.rootDir,
                    style = MaterialTheme.typography.caption,
                    color = c.primary,
                    modifier = Modifier.clickable { vm.navigateTo("") }
                )
                parts.forEachIndexed { idx, part ->
                    Text(" / ", style = MaterialTheme.typography.caption, color = c.onSurfaceVariant)
                    Text(
                        part,
                        style = MaterialTheme.typography.caption,
                        color = if (idx == parts.lastIndex) c.onBackground else c.primary,
                        fontWeight = if (idx == parts.lastIndex) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier.clickable { vm.navigateTo(parts.take(idx + 1).joinToString("/")) }
                    )
                }
                Spacer(Modifier.weight(1f))
                // 文件剪贴板非空：MP 式「粘贴(N)」直达按钮
                vm.fileClip?.let { clip ->
                    CoveChip(
                        label = S.pasteCount(clip.paths.size),
                        selected = clip.cut,
                        onClick = { vm.clipPaste() }
                    )
                    Spacer(Modifier.width(6.dp))
                }
                IconButton(
                    onClick = { searchOpen = !searchOpen; if (!searchOpen) { keyword = ""; vm.navigateTo(vm.currentPath) } },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Filled.Search, contentDescription = S.search, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { createDialog = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = S.newFileOrDir, modifier = Modifier.size(18.dp))
                }
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(c.surfaceVariant)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { exitSelection() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = S.cancel, modifier = Modifier.size(18.dp))
                }
                Text(
                    S.selectedN(selected.size),
                    style = MaterialTheme.typography.subtitle2,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { selected = vm.files.map { it.path }.toSet() }) {
                    Text(S.selectAll, style = MaterialTheme.typography.button)
                }
                TextButton(onClick = { selected = vm.files.map { it.path }.toSet() - selected }) {
                    Text(S.invertSelection, style = MaterialTheme.typography.button)
                }
            }
        }

        if (searchOpen && !selectionMode) {
            Column(Modifier.padding(horizontal = 12.dp)) {
                CoveTextField(
                    value = keyword,
                    onValueChange = {
                        keyword = it
                        if (it.isNotBlank()) {
                            if (contentMode) vm.searchContent(it) else vm.searchFiles(it)
                        } else vm.navigateTo(vm.currentPath)
                    },
                    placeholder = if (contentMode) S.searchContentPh else S.searchNamePh,
                    singleLine = true,
                    trailingIcon = {
                        if (keyword.isNotBlank()) {
                            IconButton(onClick = { keyword = ""; vm.navigateTo(vm.currentPath) }) {
                                Icon(Icons.Filled.Close, contentDescription = S.clear, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.padding(vertical = 6.dp)) {
                    CoveChip(
                        label = S.byName,
                        selected = !contentMode,
                        onClick = {
                            contentMode = false
                            if (keyword.isNotBlank()) vm.searchFiles(keyword)
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    CoveChip(
                        label = S.byContent,
                        selected = contentMode,
                        onClick = {
                            contentMode = true
                            if (keyword.isNotBlank()) vm.searchContent(keyword)
                        }
                    )
                }
            }
        }

        // ── 内容区 ──
        Box(Modifier.weight(1f)) {
            val results = vm.searchResults
            when {
                results != null -> {
                    if (results.isEmpty()) {
                        EmptyView(S.noMatches, icon = Icons.Filled.SearchOff)
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(results, key = { it.path + ":" + it.line }) { hit ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            vm.openRecent(hit.path)
                                            nav.navigate(Routes.file(vm.repoId, hit.path))
                                        }
                                        .padding(horizontal = 12.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        hit.path,
                                        style = MaterialTheme.typography.caption,
                                        fontFamily = MonoFont,
                                        color = c.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (hit.text.isNotBlank()) {
                                        Text(
                                            "${hit.line}: ${hit.text}",
                                            style = MaterialTheme.typography.caption,
                                            fontFamily = MonoFont,
                                            color = c.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                CoveDivider()
                            }
                        }
                    }
                }

                else -> {
                    val nodes = vm.files
                    if (nodes.isEmpty()) {
                        EmptyView(S.emptyDir, icon = Icons.Outlined.FolderOpen)
                    } else {
                        val list = buildList {
                            // MP 管理器：子目录顶部显示「..」上级行（不参与选择）
                            if (vm.currentPath.isNotBlank()) {
                                add(FileNode("..", vm.currentPath.substringBeforeLast('/', ""), true, null))
                            }
                            addAll(nodes)
                        }
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(list, key = { "\u0000up\u0000" + it.path }) { node ->
                                if (node.name == "..") {
                                    ParentRow(onClick = { vm.navigateTo(node.path) })
                                } else {
                                    FileRow(
                                        node = node,
                                        selected = node.path in selected,
                                        selectMode = selectionMode,
                                        onClick = {
                                            if (selectionMode) {
                                                selected = if (node.path in selected) selected - node.path else selected + node.path
                                            } else if (node.isDir) {
                                                vm.navigateTo(node.path)
                                            } else {
                                                vm.openRecent(node.path)
                                                // 点击文件进入查看页（内容/差异/历史三标签）
                                                nav.navigate(Routes.file(vm.repoId, node.path))
                                            }
                                        },
                                        onLongClick = { menuTarget = node }
                                    )
                                }
                                CoveDivider()
                            }
                        }
                    }
                }
            }
        }

        // ── 多选底部操作条（MP 管理器多选模式） ──
        if (selectionMode) {
            CoveDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .padding(vertical = 2.dp)
            ) {
                SelectAction(Icons.Filled.ContentCopy, S.copy, selected.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    vm.clipCopy(selected.toList()); exitSelection()
                }
                SelectAction(Icons.Filled.ContentCut, S.cut, selected.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    vm.clipCut(selected.toList()); exitSelection()
                }
                SelectAction(
                    Icons.Filled.DriveFileRenameOutline, S.rename,
                    enabled = selected.size == 1,
                    modifier = Modifier.weight(1f),
                ) {
                    vm.files.firstOrNull { it.path == selected.first() }?.let { renameTarget = it }
                    exitSelection()
                }
                SelectAction(Icons.Filled.Delete, S.delete, selected.isNotEmpty(), modifier = Modifier.weight(1f), tint = c.error) {
                    // 选中的路径可能来自其他目录（经「..」换目录），列表里找不到时用伪节点兜底
                    val listed = vm.files.associateBy { it.path }
                    deleteTargets = selected.map { p ->
                        listed[p] ?: FileNode(p.substringAfterLast('/', p), p, false)
                    }
                }
            }
        }
    }

    // ── 新建文件/文件夹 ──
    if (createDialog) {
        var newName by remember { mutableStateOf("") }
        // 类型选择：false = 文件，true = 文件夹
        var isDirType by remember { mutableStateOf(false) }
        // 名称校验：不允许包含路径分隔符，不支持通过 / 一次创建多级目录
        val trimmedName = newName.trim()
        val nameInvalid = trimmedName.contains('/') || trimmedName.contains('\\') ||
            trimmedName == ".." || trimmedName == "."
        CoveAlertDialog(
            onDismiss = { createDialog = false },
            title = S.newTitle,
            content = {
                // 类型选择（文件 / 文件夹）
                CoveSegmentedRow(
                    options = listOf(S.file, S.folder),
                    selectedIndex = if (isDirType) 1 else 0,
                    onSelect = { isDirType = it == 1 }
                )
                Spacer(Modifier.height(12.dp))
                CoveTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = S.nameRel,
                    placeholder = if (isDirType) S.placeholderNameDocs else S.placeholderNameReadme,
                    isError = nameInvalid,
                    supporting = when {
                        nameInvalid -> S.nameNoSlash
                        isDirType -> S.willCreateFolder(vm.currentPath.ifBlank { S.rootDir })
                        else -> S.willCreateFile(vm.currentPath.ifBlank { S.rootDir })
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmLabel = S.create,
            onConfirm = {
                val base = vm.currentPath
                val full = if (base.isBlank()) trimmedName else "$base/$trimmedName"
                if (isDirType) vm.createDirectory(full) else vm.createFile(full)
                createDialog = false
            },
            dismissLabel = S.cancel
        )
    }

    // ── 长按操作菜单（MP 管理器 dialog_file_menu 底部抽屉） ──
    menuTarget?.let { target ->
        CoveBottomSheet(onDismiss = { menuTarget = null }) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (mi, mt) = fileIcon(target, c.onSurfaceVariant)
                Icon(mi, contentDescription = null, tint = mt, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    target.name,
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val actions = buildList {
                if (!target.isDir) {
                    add(MenuAction(Icons.Filled.OpenInNew, S.viewerOpen) {
                        menuTarget = null
                        nav.navigate(Routes.file(vm.repoId, target.path))
                    })
                }
                if (target.isDir && vm.fileClip != null) {
                    add(MenuAction(Icons.Filled.ContentPaste, S.pasteHere) {
                        menuTarget = null
                        vm.clipPaste(target.path)
                    })
                }
                add(MenuAction(Icons.Filled.ContentCopy, S.copy) { menuTarget = null; vm.clipCopy(listOf(target.path)) })
                add(MenuAction(Icons.Filled.ContentCut, S.cut) { menuTarget = null; vm.clipCut(listOf(target.path)) })
                add(MenuAction(Icons.Filled.DriveFileRenameOutline, S.rename) { renameTarget = target; menuTarget = null })
                add(MenuAction(Icons.Filled.Delete, S.delete) { deleteTargets = listOf(target); menuTarget = null })
                add(MenuAction(Icons.Filled.Info, S.properties) { propsTarget = target; menuTarget = null })
                add(MenuAction(Icons.Filled.Link, S.copyPath) {
                    clipboard.setText(AnnotatedString(target.path))
                    vm.toast(S.pathCopied)
                    menuTarget = null
                })
                add(MenuAction(Icons.Filled.DoneAll, S.multiSelect) { selectionMode = true; selected = setOf(target.path); menuTarget = null })
            }
            // MP 式网格卡片：图标在上、文字在下，固定卡片高度
            // （不可用 fillMaxSize 撑开：Dialog 有界高度会被它整个吃掉，只显示一行且按钮拉成竖长条）
            actions.chunked(3).forEach { rowActions ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                    rowActions.forEach { action ->
                        Surface(
                            onClick = action.onClick,
                            shape = RoundedCornerShape(10.dp),
                            color = c.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .padding(5.dp)
                                .height(72.dp)
                        ) {
                            Column(
                                Modifier.fillMaxSize().padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    action.icon,
                                    contentDescription = action.label,
                                    tint = c.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    action.label,
                                    style = MaterialTheme.typography.caption.copy(fontSize = 11.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    repeat(3 - rowActions.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }

    // ── 重命名 ──
    renameTarget?.let { target ->
        var newName by remember(target.path) { mutableStateOf(target.name) }
        val trimmed = newName.trim()
        val invalid = trimmed.isBlank() || trimmed.contains('/') || trimmed.contains('\\') ||
            trimmed == "." || trimmed == ".."
        CoveAlertDialog(
            onDismiss = { renameTarget = null },
            title = S.rename,
            content = {
                CoveTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = S.newNameLabel,
                    isError = invalid,
                    supporting = if (invalid) S.nameNoSlash else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmLabel = S.rename,
            onConfirm = {
                if (!invalid && trimmed != target.name) vm.renameEntry(target.path, trimmed)
                renameTarget = null
                exitSelection()
            },
            dismissLabel = S.cancel
        )
    }

    // ── 删除（单个 / 多选批量共用） ──
    deleteTargets?.let { targets ->
        CoveAlertDialog(
            onDismiss = { deleteTargets = null },
            title = if (targets.size == 1) {
                if (targets[0].isDir) S.deleteFolder else S.deleteFile
            } else S.delete,
            content = {
                Text(
                    if (targets.size == 1) {
                        if (targets[0].isDir) S.deleteFolderConfirm(targets[0].path)
                        else S.deleteFileConfirm(targets[0].path)
                    } else S.multiDeleteConfirm(targets.size),
                    style = MaterialTheme.typography.body2
                )
            },
            confirmLabel = S.delete,
            onConfirm = {
                vm.deleteFiles(targets.map { it.path })
                deleteTargets = null
                exitSelection()
            },
            dismissLabel = S.cancel
        )
    }

    // ── 属性 ──
    propsTarget?.let { target ->
        var dirBytes by remember(target.path) { mutableStateOf<Long?>(null) }
        LaunchedEffect(target.path) {
            if (target.isDir) dirBytes = vm.dirSize(target.path)
        }
        CoveAlertDialog(
            onDismiss = { propsTarget = null },
            title = S.properties,
            content = {
                Column(Modifier.fillMaxWidth()) {
                    CoveKeyValueRow(S.propName, target.name)
                    CoveKeyValueRow(S.propType, if (target.isDir) S.folder else S.file)
                    CoveKeyValueRow(S.propPath, target.path.ifBlank { S.rootDir })
                    CoveKeyValueRow(S.propModified, formatDate(target.lastModified))
                    if (target.isDir) {
                        CoveKeyValueRow(S.propContains, S.itemCount(target.childCount))
                        CoveKeyValueRow(
                            S.propSize,
                            dirBytes?.let { humanSize(it) } ?: "…"
                        )
                    } else {
                        CoveKeyValueRow(S.propSize, humanSize(target.size))
                    }
                    CoveKeyValueRow(S.propGitStatus, statusText(target.status, S))
                }
            },
            dismissLabel = S.confirm
        )
    }
}

/** 长按菜单操作项（MP 管理器 dialog_file_menu：图标 + 文字网格卡片） */
private class MenuAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/** 「..」上级行（MP 管理器风格：固定导航，不参与选择） */
@Composable
private fun ParentRow(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.ArrowUpward,
            contentDescription = null,
            tint = CoveTheme.colors.secondary,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text("..", style = MaterialTheme.typography.body2, fontWeight = FontWeight.Bold)
    }
}

/** MP 管理器行：30dp 类型图标 + 粗体名称（按 Git 状态着色）+ 灰色副行（时间/大小） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    node: FileNode,
    selected: Boolean,
    selectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    val (icon, tint) = fileIcon(node, c.onSurfaceVariant)
    val nameColor = statusColor(node.status) ?: c.onBackground
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) c.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                node.name,
                style = MaterialTheme.typography.body2,
                fontWeight = FontWeight.Bold,
                color = nameColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (node.isDir) {
                    formatDate(node.lastModified) + " · " + S.itemCount(node.childCount)
                } else {
                    formatDate(node.lastModified) + " · " + humanSize(node.size)
                },
                style = MaterialTheme.typography.caption.copy(fontSize = 10.sp),
                color = c.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        node.status?.let {
            Spacer(Modifier.width(8.dp))
            StatusLetterBadge(it)
        }
    }
}

/** 多选底部操作条按钮（图标 + 10sp 小标签，等宽分布） */
@Composable
private fun SelectAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = CoveTheme.colors.onSurface,
    onClick: () -> Unit
) {
    val c = CoveTheme.colors
    // 可用项用主色（明确的「可点」暗示），禁用项低灰——之前可用/禁用同为黑灰色系，
    // 小图标小文字下几乎无法区分，用户以为按钮不可点
    val color = if (enabled) c.primary else c.onSurface.copy(alpha = 0.38f)
    Column(
        modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = if (tint == c.onSurface) color else tint.copy(alpha = if (enabled) 1f else 0.38f), modifier = Modifier.size(20.dp))
        Text(
            label,
            style = MaterialTheme.typography.caption.copy(fontSize = 10.sp),
            color = if (tint == c.onSurface) color else tint.copy(alpha = if (enabled) 1f else 0.38f)
        )
    }
}

// ─────────────── 文件类型图标（MP 管理器按扩展名分类的思路） ───────────────

private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "ico", "heic")
private val VIDEO_EXTS = setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "m4v")
private val AUDIO_EXTS = setOf("mp3", "wav", "flac", "ogg", "m4a", "aac", "opus")
private val ARCHIVE_EXTS = setOf("zip", "apk", "jar", "aar", "xapk", "apks", "7z", "rar", "tar", "gz", "tgz", "bz2")
private val CODE_EXTS = setOf(
    "kt", "kts", "java", "xml", "gradle", "py", "js", "ts", "c", "cpp", "h", "hpp", "sh",
    "json", "yml", "yaml", "html", "css", "go", "rs", "rb", "php", "sql", "toml", "bat",
    "lua", "swift", "m", "dart", "pro", "properties"
)
private val DOC_EXTS = setOf("txt", "md", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv", "log", "rtf")

private fun fileIcon(node: FileNode, defaultTint: Color): Pair<ImageVector, Color> {
    if (node.isDir) return Icons.Filled.Folder to Color(0xFFF3B04A)
    val ext = node.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when (ext) {
        in IMAGE_EXTS -> Icons.Filled.Image to Color(0xFF9C6ADE)
        in VIDEO_EXTS -> Icons.Filled.Movie to Color(0xFFE57373)
        in AUDIO_EXTS -> Icons.Filled.MusicNote to Color(0xFF4DB6AC)
        in ARCHIVE_EXTS -> Icons.Filled.FolderZip to Color(0xFFE69A3C)
        "pdf" -> Icons.Filled.PictureAsPdf to Color(0xFFE53935)
        in CODE_EXTS -> Icons.Filled.Code to Color(0xFF7986CB)
        in DOC_EXTS -> Icons.Filled.Description to Color(0xFF64B5F6)
        else -> Icons.Filled.InsertDriveFile to defaultTint
    }
}

// ─────────────── Git 状态彩色（沿用原有配色，兼容移植后的列表） ───────────────

/** 名称着色：新增绿 / 修改黄 / 删除红 / 冲突品红 / 无状态用默认色 */
private fun statusColor(status: Status?): Color? = when (status) {
    Status.ADDED, Status.UNTRACKED -> StatusAddedColor
    Status.MODIFIED -> StatusModifiedColor
    Status.DELETED -> StatusDeletedColor
    Status.CONFLICT -> StatusConflictColor
    null -> null
}

private fun statusText(status: Status?, S: Strings): String = when (status) {
    Status.ADDED -> S.statusAddedText
    Status.MODIFIED -> S.statusModifiedText
    Status.DELETED -> S.statusDeletedText
    Status.UNTRACKED -> S.statusUntrackedText
    Status.CONFLICT -> S.statusConflictText
    null -> S.statusClean
}

// ─────────────── 副行格式化 ───────────────

private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

private fun formatDate(mtime: Long): String =
    if (mtime <= 0) "" else DATE_FMT.format(Date(mtime))

/** 人类可读大小（MP 管理器同款：B/KB/MB/GB，一位小数） */
private fun humanSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.1f GB", mb / 1024.0)
}
