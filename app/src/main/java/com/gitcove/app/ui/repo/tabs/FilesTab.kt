package com.gitcove.app.ui.repo.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gitcove.app.domain.model.FileNode
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.components.EmptyView
import com.gitcove.app.ui.components.StatusDot
import com.gitcove.app.ui.components.StatusLetterBadge
import com.gitcove.app.ui.nav.Routes
import com.gitcove.app.ui.repo.RepoViewModel
import com.gitcove.app.ui.theme.MonoFont
import com.gitcove.app.ui.components.CoveAlertDialog
import com.gitcove.app.ui.components.CoveButton
import com.gitcove.app.ui.components.CoveChip
import com.gitcove.app.ui.components.CoveDivider
import com.gitcove.app.ui.components.CoveEmptyState
import com.gitcove.app.ui.components.CoveSectionTitle
import com.gitcove.app.ui.components.CoveSegmentedRow
import com.gitcove.app.ui.components.CoveTextField
import com.gitcove.app.ui.theme.CoveTheme

/**
 * 文件 Tab（文档 5.3）：面包屑导航 + 文件名/内容搜索 + 新建文件/目录（功能 58/60/61/63）
 *
 * 文件列表始终单列显示；名称后按 Git 状态追加彩色圆点标记：
 * 新增（含未跟踪）绿 ⬤、修改黄 ⬤、删除红 ⬤（提交后消失）。
 */
@Composable
fun FilesTab(vm: RepoViewModel, nav: androidx.navigation.NavHostController) {
    val S = LocalStrings.current
    var searchOpen by remember { mutableStateOf(false) }
    var keyword by remember { mutableStateOf("") }
    var contentMode by remember { mutableStateOf(false) }
    var createDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<FileNode?>(null) }
    var menuTarget by remember { mutableStateOf<FileNode?>(null) }

    Column(Modifier.fillMaxSize()) {

        // 面包屑（MD2 工具条：surfaceVariant 底 + 主色可点路径）
        Row(
            Modifier
                .fillMaxWidth()
                .background(CoveTheme.colors.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val parts = vm.currentPath.split("/").filter { it.isNotBlank() }
            Text(
                S.rootDir,
                style = MaterialTheme.typography.caption,
                color = CoveTheme.colors.primary,
                modifier = Modifier.clickable { vm.navigateTo("") }
            )
            parts.forEachIndexed { idx, part ->
                Text(" / ", style = MaterialTheme.typography.caption, color = CoveTheme.colors.onSurfaceVariant)
                Text(
                    part,
                    style = MaterialTheme.typography.caption,
                    color = if (idx == parts.lastIndex) CoveTheme.colors.onBackground else CoveTheme.colors.primary,
                    fontWeight = if (idx == parts.lastIndex) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.clickable { vm.navigateTo(parts.take(idx + 1).joinToString("/")) }
                )
            }
            Spacer(Modifier.weight(1f))
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

        if (searchOpen) {
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

        val results = vm.searchResults
        when {
            results != null -> {
                if (results.isEmpty()) {
                    EmptyView(S.noMatches)
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(results, key = { it.path + ":" + it.line }) { hit ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.openRecent(hit.path)
                                        if (vm.isGitRepo) {
                                            nav.navigate(Routes.diff(vm.repoId, hit.path, cached = false))
                                        } else {
                                            nav.navigate(Routes.editor(vm.repoId, hit.path))
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    hit.path,
                                    style = MaterialTheme.typography.caption,
                                    fontFamily = MonoFont,
                                    color = CoveTheme.colors.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (hit.text.isNotBlank()) {
                                    Text(
                                        "${hit.line}: ${hit.text}",
                                        style = MaterialTheme.typography.caption,
                                        fontFamily = MonoFont,
                                        color = CoveTheme.colors.onSurfaceVariant,
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
                    EmptyView(S.emptyDir)
                } else {
                    // 始终单列显示
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(nodes, key = { it.path }) { node ->
                            FileRow(
                                node = node,
                                onClick = {
                                    if (node.isDir) vm.navigateTo(node.path)
                                    else {
                                        vm.openRecent(node.path)
                                        // 非 Git 仓库无 diff 可看，直接进编辑器
                                        if (vm.isGitRepo) {
                                            nav.navigate(Routes.diff(vm.repoId, node.path, cached = false))
                                        } else {
                                            nav.navigate(Routes.editor(vm.repoId, node.path))
                                        }
                                    }
                                },
                                onLongClick = { menuTarget = node }
                            )
                            CoveDivider()
                        }
                    }
                }
            }
        }
    }

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

    // 长按菜单：编辑 / 删除
    menuTarget?.let { target ->
        DropdownMenu(expanded = true, onDismissRequest = { menuTarget = null }) {
            DropdownMenuItem(onClick = {
                menuTarget = null
                nav.navigate(Routes.editor(vm.repoId, target.path))
            }) { Text(S.openInEditor, style = MaterialTheme.typography.body2) }
            DropdownMenuItem(onClick = { menuTarget = null; deleteTarget = target }) {
                Text(S.delete, style = MaterialTheme.typography.body2, color = CoveTheme.colors.error)
            }
        }
    }

    deleteTarget?.let { target ->
        CoveAlertDialog(
            onDismiss = { deleteTarget = null },
            title = if (target.isDir) S.deleteFolder else S.deleteFile,
            content = {
                Text(
                    if (target.isDir) S.deleteFolderConfirm(target.path)
                    else S.deleteFileConfirm(target.path),
                    style = MaterialTheme.typography.body2
                )
            },
            confirmLabel = S.delete,
            onConfirm = { vm.deleteFile(target.path); deleteTarget = null },
            dismissLabel = S.cancel
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(node: FileNode, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (node.isDir) Icons.Outlined.Folder else Icons.Outlined.InsertDriveFile,
            contentDescription = null,
            tint = if (node.isDir) CoveTheme.colors.secondary else CoveTheme.colors.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            node.name,
            style = MaterialTheme.typography.body2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        // 名称后的状态圆点：新增/未跟踪绿、修改黄、删除红
        node.status?.let {
            Spacer(Modifier.width(4.dp))
            StatusDot(it)
        }
        Spacer(Modifier.weight(1f))
        node.status?.let { StatusLetterBadge(it) }
    }
}
