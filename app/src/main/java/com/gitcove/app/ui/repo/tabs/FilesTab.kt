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
import com.gitcove.app.ui.components.EmptyView
import com.gitcove.app.ui.components.StatusLetterBadge
import com.gitcove.app.ui.nav.Routes
import com.gitcove.app.ui.repo.RepoViewModel
import com.gitcove.app.ui.theme.MonoFont

/**
 * 文件 Tab（文档 5.3）：面包屑导航 + 文件名/内容搜索 + 新建文件（功能 58/60/61/63）
 */
@Composable
fun FilesTab(vm: RepoViewModel, nav: androidx.navigation.NavHostController) {
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
                "根目录",
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
                Icon(Icons.Filled.Search, contentDescription = "搜索", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { createDialog = true }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "新建文件", modifier = Modifier.size(18.dp))
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
                    placeholder = { Text(if (contentMode) "搜索文件内容…" else "搜索文件名…") },
                    singleLine = true,
                    trailingIcon = {
                        if (keyword.isNotBlank()) {
                            IconButton(onClick = { keyword = ""; vm.navigateTo(vm.currentPath) }) {
                                Icon(Icons.Filled.Close, contentDescription = "清除", modifier = Modifier.size(16.dp))
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
                        label = { Text("文件名") }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = contentMode,
                        onClick = {
                            contentMode = true
                            if (keyword.isNotBlank()) vm.searchContent(keyword)
                        },
                        label = { Text("内容") }
                    )
                }
            }
        }

        val results = vm.searchResults
        when {
            results != null -> {
                if (results.isEmpty()) {
                    EmptyView("没有匹配结果")
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(results, key = { it.path + ":" + it.line }) { hit ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.openRecent(hit.path)
                                        nav.navigate(Routes.diff(vm.repoId, hit.path, cached = false))
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
                    EmptyView("此目录为空")
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(nodes, key = { it.path }) { node ->
                            FileRow(
                                node = node,
                                onClick = {
                                    if (node.isDir) vm.navigateTo(node.path)
                                    else {
                                        vm.openRecent(node.path)
                                        nav.navigate(Routes.diff(vm.repoId, node.path, cached = false))
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
        AlertDialog(
            onDismissRequest = { createDialog = false },
            title = { Text("新建文件", style = MaterialTheme.typography.titleMedium) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("路径（相对当前目录）") },
                    placeholder = { Text("README.md 或 src/main.py") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val base = vm.currentPath
                        val full = if (base.isBlank()) newName.trim() else "$base/${newName.trim()}"
                        vm.createFile(full)
                        createDialog = false
                    },
                    enabled = newName.isNotBlank()
                ) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { createDialog = false }) { Text("取消") } }
        )
    }

    // 长按菜单：编辑 / 删除
    menuTarget?.let { target ->
        DropdownMenu(expanded = true, onDismissRequest = { menuTarget = null }) {
            DropdownMenuItem(
                text = { Text("用编辑器打开") },
                onClick = {
                    menuTarget = null
                    nav.navigate(Routes.editor(vm.repoId, target.path))
                }
            )
            DropdownMenuItem(
                text = { Text("删除文件", color = MaterialTheme.colorScheme.error) },
                onClick = { menuTarget = null; deleteTarget = target }
            )
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除文件", style = MaterialTheme.typography.titleMedium) },
            text = { Text("确定删除 ${target.path} 吗？删除后可在改动页还原或提交。") },
            confirmButton = {
                TextButton(onClick = { vm.deleteFile(target.path); deleteTarget = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
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
