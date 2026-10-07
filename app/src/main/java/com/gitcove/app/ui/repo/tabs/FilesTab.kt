package com.gitcove.app.ui.repo.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.gitcove.app.ui.components.StatusLetterBadge
import com.gitcove.app.ui.nav.Routes
import com.gitcove.app.ui.repo.RepoViewModel
import com.gitcove.app.ui.theme.MonoFont

/**
 * 文件 Tab（文档 5.3）：面包屑导航 + 文件名/内容搜索 + 新建文件/目录（功能 58/60/61/63）
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

        // 面包屑
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val parts = vm.currentPath.split("/").filter { it.isNotBlank() }
            Text(
                S.rootDir,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { vm.navigateTo("") }
            )
            parts.forEachIndexed { idx, part ->
                Text(" / ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    part,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (idx == parts.lastIndex) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.primary,
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
                OutlinedTextField(
                    value = keyword,
                    onValueChange = {
                        keyword = it
                        if (it.isNotBlank()) {
                            if (contentMode) vm.searchContent(it) else vm.searchFiles(it)
                        } else vm.navigateTo(vm.currentPath)
                    },
                    placeholder = { Text(if (contentMode) S.searchContentPh else S.searchNamePh) },
                    singleLine = true,
                    trailingIcon = {
                        if (keyword.isNotBlank()) {
                            IconButton(onClick = { keyword = ""; vm.navigateTo(vm.currentPath) }) {
                                Icon(Icons.Filled.Close, contentDescription = S.clear, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                )
                Row(Modifier.padding(vertical = 4.dp)) {
                    FilterChip(
                        selected = !contentMode,
                        onClick = {
                            contentMode = false
                            if (keyword.isNotBlank()) vm.searchFiles(keyword)
                        },
                        label = { Text(S.byName) }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = contentMode,
                        onClick = {
                            contentMode = true
                            if (keyword.isNotBlank()) vm.searchContent(keyword)
                        },
                        label = { Text(S.byContent) }
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
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = MonoFont,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (hit.text.isNotBlank()) {
                                    Text(
                                        "${hit.line}: ${hit.text}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = MonoFont,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }

            else -> {
                val nodes = vm.files
                if (nodes.isEmpty()) {
                    EmptyView(S.emptyDir)
                } else {
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
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
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
        AlertDialog(
            onDismissRequest = { createDialog = false },
            title = { Text(S.newTitle, style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    // 类型选择（文件 / 文件夹）
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !isDirType,
                            onClick = { isDirType = false },
                            shape = SegmentedButtonDefaults.itemShape(0, 2)
                        ) { Text(S.file) }
                        SegmentedButton(
                            selected = isDirType,
                            onClick = { isDirType = true },
                            shape = SegmentedButtonDefaults.itemShape(1, 2)
                        ) { Text(S.folder) }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(S.nameRel) },
                        placeholder = {
                            Text(if (isDirType) S.placeholderNameDocs else S.placeholderNameReadme)
                        },
                        supportingText = {
                            Text(
                                if (isDirType) S.willCreateFolder(vm.currentPath.ifBlank { S.rootDir })
                                else S.willCreateFile(vm.currentPath.ifBlank { S.rootDir }),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val base = vm.currentPath
                        val full = if (base.isBlank()) newName.trim() else "$base/${newName.trim()}"
                        if (isDirType) vm.createDirectory(full) else vm.createFile(full)
                        createDialog = false
                    },
                    enabled = newName.isNotBlank()
                ) { Text(S.create) }
            },
            dismissButton = { TextButton(onClick = { createDialog = false }) { Text(S.cancel) } }
        )
    }

    // 长按菜单：编辑 / 删除
    menuTarget?.let { target ->
        DropdownMenu(expanded = true, onDismissRequest = { menuTarget = null }) {
            DropdownMenuItem(
                text = { Text(S.openInEditor) },
                onClick = {
                    menuTarget = null
                    nav.navigate(Routes.editor(vm.repoId, target.path))
                }
            )
            DropdownMenuItem(
                text = { Text(S.delete, color = MaterialTheme.colorScheme.error) },
                onClick = { menuTarget = null; deleteTarget = target }
            )
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(if (target.isDir) S.deleteFolder else S.deleteFile, style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    if (target.isDir) S.deleteFolderConfirm(target.path)
                    else S.deleteFileConfirm(target.path)
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteFile(target.path); deleteTarget = null }) {
                    Text(S.delete, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(S.cancel) } }
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
            tint = if (node.isDir) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            node.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        node.status?.let { StatusLetterBadge(it) }
    }
}
